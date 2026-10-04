package com.tbce.calc

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tbce.calc.vault.KeyVault
import com.tbce.calc.vault.VaultSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** FRONT is the visible, locked front screen of whichever face is active (dictionary, or the play edition's own). */
enum class Screen { FRONT, SETUP, INSIDE }

/** [ui] is the wording from the encrypted pack, used by the setup and code screens. */
class AppController(private val vault: KeyVault, private val scope: CoroutineScope, val ui: Map<String, String>) {
    /** Bumped on every lock, so a slow unlock that finishes after a lock is thrown away. */
    private var generation = 0

    var screen by mutableStateOf(Screen.FRONT)
        private set
    var session: VaultSession? = null
        private set

    /** Bumped when the vault opens, so the vault screen reloads its contents. */
    var vaultOpens by mutableIntStateOf(0)
        private set

    /** Which disguise the launcher shows and which front screen appears when locked. */
    var disguise by mutableStateOf(Disguise.DICTIONARY)
        private set

    fun initDisguise(d: Disguise) { disguise = d }

    /** Switches the launcher icon and name, and the front screen, to [d]. */
    fun setDisguise(context: Context, d: Disguise) {
        Disguises.set(context, d)
        disguise = d
    }

    /**
     * The hidden arm/submit gesture of a disguise (the Dictionary's Search button): the first hold
     * arms silently, the second submits the digits typed in between. [digits] is what the face
     * shows at the time of the hold.
     */
    var armed by mutableStateOf(false)
        private set
    private var faceTimer: Job? = null

    fun faceTrigger(digits: ByteArray) {
        if (!vault.exists()) { screen = Screen.SETUP; return }
        if (!armed) {
            armed = true
            faceTimer?.cancel()
            faceTimer = scope.launch { delay(Config.ARM_WINDOW_MS); armed = false }
        } else {
            armed = false
            faceTimer?.cancel()
            val code = digits.copyOf()
            val gen = generation
            scope.launch {
                val s = withContext(Dispatchers.Default) { try { vault.unlock(code) } finally { code.fill(0) } }
                if (s == null) return@launch
                if (gen != generation) { s.wipe(); return@launch }
                open(s)
            }
        }
    }

    fun hasVault() = vault.exists()

    fun startSetup() { screen = Screen.SETUP }

    /**
     * The undisguised face's plain code entry: no hidden gesture, just the code and Open.
     * [onWrong] runs on the main thread if the code is wrong. The caller zeros [code] afterwards;
     * this works on its own copy.
     */
    fun tryCode(code: ByteArray, onWrong: () -> Unit) {
        if (!vault.exists()) { screen = Screen.SETUP; return }
        val copy = code.copyOf()
        val gen = generation
        scope.launch {
            val s = withContext(Dispatchers.Default) { try { vault.unlock(copy) } finally { copy.fill(0) } }
            if (s == null) { if (gen == generation) onWrong(); return@launch }
            if (gen != generation) { s.wipe(); return@launch }
            open(s)
        }
    }

    fun faceDisarm() {
        armed = false
        faceTimer?.cancel()
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
        screen = Screen.FRONT
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
        screen = Screen.FRONT
    }

    fun eraseEverything() {
        lock()
        vault.erase()
    }

    fun onBackground() {
        pickerOpen = false
        faceDisarm()
        lock()
    }
}
