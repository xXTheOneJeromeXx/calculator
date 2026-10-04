package com.tbce.calc

import com.tbce.calc.unlock.UnlockController
import com.tbce.calc.unlock.UnlockController.Key
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every transition in the brief's section 4 table. */
class UnlockControllerTest {
    private val u = UnlockController(windowMs = 15_000, maxDigits = 16)

    private fun type(code: String, now: Long = 0) = code.forEach { u.onKey(Key.DIGIT, it - '0', now) }
    private fun submitted(r: UnlockController.LongPress) = String((r as UnlockController.Submit).code.map { it.toInt().toChar() }.toCharArray())

    @Test fun noVaultOpensSetup() {
        assertEquals(UnlockController.OpenSetup, u.onLongEquals(0, vaultExists = false))
        assertFalse(u.armed)
    }

    @Test fun armTypeSubmit() {
        assertEquals(UnlockController.Armed, u.onLongEquals(0, true))
        type("12345678", 1_000)
        assertEquals("12345678", submitted(u.onLongEquals(2_000, true)))
        assertFalse(u.armed)
        assertTrue(u.bufferIsZero())
    }

    @Test fun backspaceAndClearEditTheBuffer() {
        u.onLongEquals(0, true)
        type("1239")
        u.onKey(Key.BACKSPACE, now = 10)
        type("4")
        assertEquals("1234", submitted(u.onLongEquals(20, true)))
        u.onLongEquals(30, true)
        type("999")
        u.onKey(Key.CLEAR, now = 40)
        assertTrue(u.armed)
        type("55")
        assertEquals("55", submitted(u.onLongEquals(50, true)))
    }

    @Test fun otherKeyDisarmsSilently() {
        u.onLongEquals(0, true)
        type("123")
        u.onKey(Key.OTHER, now = 10)
        assertFalse(u.armed)
        assertTrue(u.bufferIsZero())
        // The next hold arms again rather than submitting.
        assertEquals(UnlockController.Armed, u.onLongEquals(20, true))
    }

    @Test fun timeoutDisarmsAndClears() {
        u.onLongEquals(0, true)
        type("123")
        assertFalse(u.tick(14_999))
        assertTrue(u.tick(15_000))
        assertFalse(u.armed)
        assertTrue(u.bufferIsZero())
    }

    @Test fun keyAfterTimeoutReportsClear() {
        u.onLongEquals(0, true)
        assertTrue(u.onKey(Key.DIGIT, 1, 20_000))
        assertFalse(u.armed)
    }

    @Test fun holdAfterTimeoutArmsAfresh() {
        u.onLongEquals(0, true)
        type("123")
        assertEquals(UnlockController.Armed, u.onLongEquals(16_000, true))
        type("9")
        assertEquals("9", submitted(u.onLongEquals(17_000, true)))
    }

    @Test fun backgroundDisarms() {
        assertFalse(u.onBackground())
        u.onLongEquals(0, true)
        type("123")
        assertTrue(u.onBackground())
        assertFalse(u.armed)
        assertTrue(u.bufferIsZero())
    }

    @Test fun digitsWhileNotArmedAreIgnored() {
        type("123")
        u.onLongEquals(0, true)
        assertArrayEquals(ByteArray(0), (u.onLongEquals(1, true) as UnlockController.Submit).code)
    }

    @Test fun bufferIsCapped() {
        u.onLongEquals(0, true)
        type("12345678901234567890")
        assertEquals("1234567890123456", submitted(u.onLongEquals(1, true)))
    }
}
