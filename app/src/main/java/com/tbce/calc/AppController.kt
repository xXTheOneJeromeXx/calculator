package com.tbce.calc

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tbce.calc.calc.Calculator
import com.tbce.calc.unlock.UnlockController
import com.tbce.calc.vault.KeyVault
import com.tbce.calc.vault.VaultSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { CALC, SETUP, INSIDE }

/** Calculator keys, as the UI sends them. */
sealed interface CalcKey {
    data class Digit(val d: Int) : CalcKey
    data object Dot : CalcKey
    data class Operator(val c: Char) : CalcKey
    data class PostOp(val c: Char) : CalcKey
    data object Negate : CalcKey
    data object Back : CalcKey
    data object Clear : CalcKey
    data object Equals : CalcKey
    data object LParen : CalcKey
    data object RParen : CalcKey
    data class Func(val name: String) : CalcKey
    data class Const(val name: String) : CalcKey
    data object Angle : CalcKey
}

/** [ui] is the wording from the encrypted pack, used by the setup and code screens. */
class AppController(private val vault: KeyVault, private val scope: CoroutineScope, val ui: Map<String, String>) {
    private val calc = Calculator()
    private val unlock = UnlockController()
    private var timeout: Job? = null

    /** Bumped on every lock, so a slow unlock that finishes after a lock is thrown away. */
    private var generation = 0

    var screen by mutableStateOf(Screen.CALC)
        private set
    var session: VaultSession? = null
        private set

    var display by mutableStateOf("0")
        private set
    var preview by mutableStateOf("")
        private set
    var previous by mutableStateOf("")
        private set
    var degrees by mutableStateOf(true)
        private set
    /** Bumped when the vault opens, so the vault screen reloads its contents. */
    var vaultOpens by mutableIntStateOf(0)
        private set

    private fun now() = SystemClock.elapsedRealtime()

    private fun refresh() {
        display = calc.display
        preview = calc.preview
        previous = calc.previous
        degrees = calc.degrees
    }

    fun onKey(key: CalcKey) {
        val kind = when (key) {
            is CalcKey.Digit -> UnlockController.Key.DIGIT
            CalcKey.Back -> UnlockController.Key.BACKSPACE
            CalcKey.Clear -> UnlockController.Key.CLEAR
            else -> UnlockController.Key.OTHER
        }
        if (unlock.onKey(kind, (key as? CalcKey.Digit)?.d ?: 0, now())) calc.clear()
        when (key) {
            is CalcKey.Digit -> calc.digit(key.d)
            CalcKey.Dot -> calc.decimal()
            is CalcKey.Operator -> calc.operator(key.c)
            is CalcKey.PostOp -> calc.postfix(key.c)
            CalcKey.Negate -> calc.negate()
            CalcKey.Back -> calc.backspace()
            CalcKey.Clear -> calc.clear()
            CalcKey.Equals -> calc.equals()
            CalcKey.LParen -> calc.openParen()
            CalcKey.RParen -> calc.closeParen()
            is CalcKey.Func -> calc.function(key.name)
            is CalcKey.Const -> calc.constant(key.name)
            CalcKey.Angle -> calc.toggleAngle()
        }
        if (!unlock.armed) timeout?.cancel()
        refresh()
    }

    fun onEqualsHeld() {
        when (val r = unlock.onLongEquals(now(), vault.exists())) {
            UnlockController.OpenSetup -> screen = Screen.SETUP
            UnlockController.Armed -> {
                calc.clear()
                timeout?.cancel()
                timeout = scope.launch {
                    delay(Config.ARM_WINDOW_MS)
                    if (unlock.tick(now())) { calc.clear(); refresh() }
                }
            }
            is UnlockController.Submit -> {
                calc.clear()
                timeout?.cancel()
                val gen = generation
                scope.launch {
                    val s = withContext(Dispatchers.Default) {
                        try { vault.unlock(r.code) } finally { r.code.fill(0) }
                    }
                    if (s == null) return@launch
                    if (gen != generation) { s.wipe(); return@launch }
                    open(s)
                }
            }
        }
        refresh()
    }

    /** Setup finished: the new vault is open. The caller zeros [code]. */
    suspend fun finishSetup(code: ByteArray): Boolean {
        val gen = generation
        val s = withContext(Dispatchers.Default) { vault.create(code) }
        if (gen != generation) { s.wipe(); return false }
        open(s)
        return true
    }

    fun cancelSetup() {
        screen = Screen.CALC
    }

    private fun open(s: VaultSession) {
        session = s
        vaultOpens++
        screen = Screen.INSIDE
    }

    /**
     * True while the system file picker is open for an export or import. Leaving the app for the
     * picker then does not lock straight away (see MainActivity), only after a grace period.
     */
    var pickerOpen = false

    fun wipeEnabled() = vault.wipeEnabled()

    fun setWipeEnabled(enabled: Boolean) = vault.setWipeEnabled(enabled)

    /** Re-wraps the vault key under [code]. The caller zeros [code]. */
    suspend fun changeCode(code: ByteArray) {
        val s = session ?: return
        withContext(Dispatchers.Default) { vault.changeCode(s, code) }
    }

    /** Before-lock hook so the vault screen can save what is on it. */
    var beforeLock: ((VaultSession) -> Unit)? = null

    /** Panic button, corner double-tap, back, idle timeout, background or screen off. */
    fun lock() {
        generation++
        session?.let { s ->
            try { beforeLock?.invoke(s) } catch (_: Exception) {}
            s.wipe()
        }
        session = null
        screen = Screen.CALC
    }

    fun eraseEverything() {
        lock()
        vault.erase()
        calc.clear()
        refresh()
    }

    fun onBackground() {
        pickerOpen = false
        if (unlock.onBackground()) calc.clear()
        timeout?.cancel()
        lock()
        refresh()
    }
}
