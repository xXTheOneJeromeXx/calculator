package com.tbce.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DisguiseTest {
    @Test fun eachFaceHasAUniqueAliasAndLabel() {
        val aliases = Disguise.entries.map { it.alias }
        val labels = Disguise.entries.map { it.label }
        assertEquals("aliases must be unique", aliases.size, aliases.toSet().size)
        assertEquals("labels must be unique", labels.size, labels.toSet().size)
        assertTrue(aliases.all { it.startsWith("Face") && it.isNotBlank() })
        assertTrue(
            listOf(Disguise.CALCULATOR, Disguise.NOTES, Disguise.CLOCK, Disguise.SUDOKU)
                .all { it in Disguise.entries },
        )
    }
}
