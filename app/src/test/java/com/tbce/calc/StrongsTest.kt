package com.tbce.calc

import com.tbce.calc.reader.Packs
import com.tbce.calc.reader.Ref
import com.tbce.calc.reader.Strongs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Reads the real packs in src/main/assets, the same way the app does. */
class StrongsTest {
    private val packs = Packs { name -> File("src/main/assets/r/$name").readBytes() }
    private val books = packs.meta().translations.first { it.id == "kjv" }.books.map { it.code }
    private val strongs = Strongs(packs, books)

    @Test fun kjvWordsCarryTheirNumbers() {
        val w = strongs.words("JHN", 3, 16)
        assertEquals("loved", w.first { it.number == "G25" }.text)
        assertTrue(w.any { it.text == "only begotten" && it.number == "G3439" })
        assertEquals("H7225", strongs.words("GEN", 1, 1).first { it.text == "beginning" }.number)
        assertTrue(strongs.words("GEN", 1, 99).isEmpty())
        assertTrue(strongs.words("GEN", 99, 1).isEmpty())
    }

    @Test fun lexiconHasHebrewAndGreek() {
        val h = strongs.entry("H430")!!
        assertEquals("אֱלֹהִים", h.lemma)
        assertTrue(h.hebrew)
        assertTrue(h.derivation.contains("H433"))
        val g = strongs.entry("g0026")!!
        assertEquals("G26", g.number)
        assertEquals("ἀγάπη", g.lemma)
        assertTrue(g.definition.startsWith("love"))
        assertTrue(g.usage.contains("charity"))
        assertNull(strongs.entry("G99999"))
        assertNull(strongs.entry("love"))
    }

    @Test fun concordanceListsVersesInOrderAndRenderings() {
        val u = strongs.usage("H7225")
        assertEquals(Ref("GEN", 1, 1), u.verses.first())
        assertEquals(u.verses, u.verses.sortedWith(compareBy({ books.indexOf(it.book) }, { it.chapter }, { it.verse })))
        assertEquals("beginning", u.renderings.first().first)
        assertTrue(Ref("JHN", 3, 16) in strongs.usage("G25").verses)
    }

    @Test fun everyTaggedWordHasAnEntry() {
        for (b in listOf("GEN", "PSA", "MAT", "REV")) {
            for (w in strongs.words(b, 1, 1)) assertNotNull("${w.number} in $b", strongs.entry(w.number))
        }
    }

    @Test fun normalizesTypedNumbers() {
        assertEquals("H430", Strongs.normalize(" h0430 "))
        assertEquals("G26", Strongs.normalize("G 26"))
        assertNull(Strongs.normalize("John 3:16"))
        assertNull(Strongs.normalize("H"))
    }

    @Test fun findsByNumberEnglishAndTransliteration() {
        assertEquals(listOf("G26"), strongs.find("g0026").map { it.number })
        // English: what the KJV translates as "love", most used first.
        val love = strongs.find("love").map { it.number }
        assertTrue(love.take(3).containsAll(listOf("G25", "G26")) || love.indexOf("G26") < 5)
        assertTrue("H157" in love.take(5))
        // Transliteration, with or without accents.
        assertEquals("G26", strongs.find("agape").first().number)
        assertEquals("H430", strongs.find("elohiym").first().number)
        assertTrue(strongs.find("a").isEmpty())
        assertTrue(strongs.find("zzzqqq").isEmpty())
        assertTrue(strongs.find("love", limit = 5).size == 5)
    }

    @Test fun plainFoldsAccents() {
        assertEquals("agape", Strongs.plain("agápē"))
        assertEquals("elohiym", Strongs.plain("ʼĕlôhîym"))
    }
}
