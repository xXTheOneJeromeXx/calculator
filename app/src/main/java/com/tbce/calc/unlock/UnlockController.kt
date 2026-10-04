package com.tbce.calc.unlock

import com.tbce.calc.Config

/**
 * The hidden arm / enter / submit gesture on the calculator.
 *
 * Every key still goes to the calculator as normal; this class only watches. Callers must
 * clear the calculator whenever a method here returns true or [Armed]/[Submit].
 */
class UnlockController(
    private val windowMs: Long = Config.ARM_WINDOW_MS,
    maxDigits: Int = Config.MAX_CODE_DIGITS,
) {
    enum class Key { DIGIT, BACKSPACE, CLEAR, OTHER }

    sealed interface LongPress
    data object OpenSetup : LongPress
    data object Armed : LongPress
    /** The caller owns [code] and must zero it after use. */
    class Submit(val code: ByteArray) : LongPress

    var armed = false
        private set
    private var armedAt = 0L
    private val buffer = ByteArray(maxDigits)
    private var length = 0

    /** '=' was held for the full hold time. */
    fun onLongEquals(now: Long, vaultExists: Boolean): LongPress {
        if (armed && expired(now)) disarm()
        if (armed) {
            val code = buffer.copyOf(length)
            disarm()
            return Submit(code)
        }
        if (!vaultExists) return OpenSetup
        armed = true
        armedAt = now
        return Armed
    }

    /** Any other key press. Returns true if the window had expired and the display must be cleared. */
    fun onKey(key: Key, digit: Int = 0, now: Long): Boolean {
        if (tick(now)) return true
        if (!armed) return false
        when (key) {
            Key.DIGIT -> if (length < buffer.size) buffer[length++] = ('0'.code + digit).toByte()
            Key.BACKSPACE -> if (length > 0) buffer[--length] = 0
            Key.CLEAR -> wipeBuffer()
            Key.OTHER -> disarm()
        }
        return false
    }

    /** Returns true if the armed window just expired (clear the display). */
    fun tick(now: Long): Boolean {
        if (armed && expired(now)) {
            disarm()
            return true
        }
        return false
    }

    /** App backgrounded or screen off. Returns true if it was armed (clear the display). */
    fun onBackground(): Boolean {
        val was = armed
        disarm()
        return was
    }

    private fun expired(now: Long) = now - armedAt >= windowMs

    private fun disarm() {
        armed = false
        wipeBuffer()
    }

    private fun wipeBuffer() {
        buffer.fill(0)
        length = 0
    }

    /** For tests: the buffer must be all zeros whenever the controller is not armed. */
    internal fun bufferIsZero() = buffer.all { it.toInt() == 0 }
}
