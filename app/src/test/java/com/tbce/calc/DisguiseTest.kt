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
    }

    @Test fun onlyTheReaderAndDictionaryRemain() {
        // Calculator, Notes, Clock and Sudoku were removed in 1.5: each extra face was one more
        // thing a searched phone could give away.
        assertEquals(listOf(Disguise.READER, Disguise.DICTIONARY), Disguise.entries.toList())
    }

    @Test fun defaultsComeFirst() {
        // Disguises.current falls back to the first face an edition has: READER on play, and
        // DICTIONARY on direct (which has no READER alias).
        assertEquals(Disguise.READER, Disguise.entries[0])
        assertEquals(Disguise.DICTIONARY, Disguise.entries[1])
    }
}
