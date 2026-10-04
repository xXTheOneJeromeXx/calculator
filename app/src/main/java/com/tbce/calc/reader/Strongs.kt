package com.tbce.calc.reader

import org.json.JSONArray
import org.json.JSONObject

/**
 * Strong's numbers for the reader, from the packs built by tools/build_packs.py:
 * s/<book> (the KJV's words and their numbers, per verse), lex (Strong's Hebrew and Greek
 * dictionaries) and conc (every verse that uses each number, and how the KJV renders it).
 * Numbers look like "H430" or "G26". The lexicon and concordance load on first use.
 */
class Strongs(private val packs: Packs, private val books: List<String>) {
    /** One KJV word or phrase and the number it translates. */
    class Word(val text: String, val number: String)

    /**
     * A dictionary entry. [derivation], [definition] and [usage] are Strong's own wording;
     * [usage] lists the KJV's renderings. Numbers inside them ("from H7218") can be linked.
     * The sources don't always split derivation from definition where Strong did, so show
     * [meaning] (both, as printed) rather than either part alone.
     */
    class Entry(
        val number: String,
        val lemma: String,
        val transliteration: String,
        val pronunciation: String,
        val derivation: String,
        val definition: String,
        val usage: String,
    ) {
        val hebrew: Boolean get() = number.startsWith("H")
        val meaning: String get() = listOf(derivation, definition).filter { it.isNotBlank() }.joinToString(" ")
    }

    /** Where a number is used: verses in canonical order, and KJV renderings by count. */
    class Usage(val verses: List<Ref>, val renderings: List<Pair<String, Int>>)

    private val tags = LinkedHashMap<String, JSONArray>(4, 0.75f, true)
    private val lex: JSONObject by lazy { JSONObject(packs.text("lex")) }
    private val conc: JSONObject by lazy { JSONObject(packs.text("conc")) }

    /** The KJV's tagged words in [book] [chapter]:[verse], in order; empty if there are none. */
    fun words(book: String, chapter: Int, verse: Int): List<Word> {
        val chapters = synchronized(tags) {
            tags.getOrPut(book) { JSONArray(packs.text("s/$book")) }.also {
                while (tags.size > 3) tags.remove(tags.keys.first())
            }
        }
        if (chapter < 1 || chapter > chapters.length()) return emptyList()
        val v = chapters.getJSONObject(chapter - 1).optJSONArray("$verse") ?: return emptyList()
        return (0 until v.length()).map { v.getJSONArray(it).let { w -> Word(w.getString(0), w.getString(1)) } }
    }

    fun entry(number: String): Entry? {
        val n = normalize(number) ?: return null
        val a = lex.optJSONArray(n) ?: return null
        return Entry(n, a.getString(0), a.getString(1), a.getString(2), a.getString(3), a.getString(4), a.getString(5))
    }

    fun usage(number: String): Usage {
        val n = normalize(number) ?: return Usage(emptyList(), emptyList())
        val o = conc.optJSONObject(n) ?: return Usage(emptyList(), emptyList())
        val d = o.getJSONArray("v")
        val refs = ArrayList<Ref>(d.length())
        var id = 0
        for (i in 0 until d.length()) {
            id += d.getInt(i)
            val b = id ushr 16
            if (b < books.size) refs += Ref(books[b], (id ushr 8) and 0xFF, id and 0xFF)
        }
        val r = o.getJSONArray("r")
        val renderings = (0 until r.length()).map { r.getJSONArray(it).let { x -> x.getString(0) to x.getInt(1) } }
        return Usage(refs, renderings)
    }

    /** The KJV's most frequent renderings of [number], for a one-line summary. */
    fun renderings(number: String, limit: Int = 3): List<String> {
        val r = normalize(number)?.let { conc.optJSONObject(it)?.optJSONArray("r") } ?: return emptyList()
        return (0 until minOf(limit, r.length())).map { r.getJSONArray(it).getString(0) }
    }

    companion object {
        private val NUMBER = Regex("^([HhGg])\\s*0*(\\d{1,4})$")

        /** "h0430", "H 430" -> "H430"; null if [text] is not a Strong's number. */
        fun normalize(text: String): String? =
            NUMBER.matchEntire(text.trim())?.let { it.groupValues[1].uppercase() + it.groupValues[2] }

        /** Strong's numbers mentioned in a piece of entry text, for linking. */
        val MENTION = Regex("\\b[HG]\\d{1,4}\\b")
    }
}
