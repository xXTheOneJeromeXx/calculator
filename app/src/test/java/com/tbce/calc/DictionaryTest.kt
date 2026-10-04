package com.tbce.calc

import com.tbce.calc.dictionary.Dictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Runs against the real word data the app ships (app/src/main/assets/w). */
class DictionaryTest {
    private val dir = listOf(File("src/main/assets/w"), File("app/src/main/assets/w")).first { it.isDirectory }
    private val dict = Dictionary { name -> File(dir, name).takeIf { it.exists() }?.readText() }

    @Test fun findsAWordWithDefinitionsExamplesAndSynonyms() {
        val e = dict.lookup("happy")
        assertNotNull(e)
        e!!
        assertEquals("happy", e.key)
        assertNull(e.from)
        val s = e.senses.first()
        assertEquals('a', s.pos)
        assertTrue(s.definition.isNotEmpty() && !s.definition.contains('"'))
        assertTrue(e.senses.any { it.examples.isNotEmpty() })
        assertTrue(e.senses.any { "unhappy" in it.antonyms })
    }

    @Test fun keepsUsualCapitalisationAndMultiWordEntries() {
        assertEquals("Paris", dict.lookup("paris")!!.display)
        assertEquals("tree", dict.lookup("tree")!!.display)
        assertNotNull(dict.lookup("  Ice   Cream "))
    }

    @Test fun findsBaseFormsOfInflectedWords() {
        assertEquals("run", dict.lookup("ran")!!.key)
        assertEquals("ran", dict.lookup("ran")!!.from)
        assertEquals("box", dict.lookup("boxes")!!.key)
        assertEquals("city", dict.lookup("cities")!!.key)
        assertEquals("walk", dict.lookup("walked")!!.key)
    }

    @Test fun suggestsByPrefixInOrder() {
        val s = dict.suggest("thesau")
        assertTrue(s.isNotEmpty())
        assertTrue(s.all { it.lowercase().startsWith("thesau") })
        assertEquals(s.map { it.lowercase() }.sorted(), s.map { it.lowercase() })
        assertTrue(dict.suggest("zzzzqx").isEmpty())
        assertTrue(dict.suggest("").isEmpty())
    }

    @Test fun digitsAreJustAnOrdinarySearchWithNoEntry() {
        // A code typed in the search box looks like any failed lookup.
        assertNull(dict.lookup("482913"))
    }
}
