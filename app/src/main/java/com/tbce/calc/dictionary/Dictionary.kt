package com.tbce.calc.dictionary

/**
 * The Dictionary disguise's word data: WordNet 3.0, built by tools/build_dict.py into one file
 * per first letter (a..z, 0 for the rest) plus file 1 with irregular forms. Plain decoy content.
 * [read] returns a file's text, or null if it is missing. Only one letter is kept in memory.
 */
class Dictionary(private val read: (String) -> String?) {
    /** [related] are near words from WordNet's "similar to" and "see also" links (thesaurus only). */
    class Sense(
        val pos: Char,
        val definition: String,
        val examples: List<String>,
        val synonyms: List<String>,
        val antonyms: List<String>,
        val related: List<String>,
    )

    /** [key] is the lowercase headword, [display] its usual spelling, [from] the form searched for. */
    class Entry(val key: String, val display: String, val senses: List<Sense>, val from: String? = null)

    private class Bucket(val name: String, val keys: Array<String>, val lines: Array<String>)

    @Volatile private var bucket: Bucket? = null
    private val irregular: Map<String, List<Pair<Char, String>>> by lazy {
        val m = HashMap<String, MutableList<Pair<Char, String>>>()
        read("1")?.lineSequence()?.forEach { line ->
            val f = line.split('\t')
            if (f.size == 3 && f[1].isNotEmpty()) m.getOrPut(f[0]) { ArrayList() } += f[1][0] to f[2]
        }
        m
    }

    private fun bucketFor(key: String): Bucket? {
        val c = key.firstOrNull() ?: return null
        val name = if (c in 'a'..'z') "$c" else "0"
        bucket?.let { if (it.name == name) return it }
        val text = read(name) ?: return null
        val lines = text.split('\n').filter { it.isNotEmpty() }.toTypedArray()
        val keys = Array(lines.size) { lines[it].substringBefore('\t') }
        return Bucket(name, keys, lines).also { bucket = it }
    }

    /** Lowercases and tidies what was typed: trims, folds runs of spaces, dashes become spaces. */
    fun normalize(query: String): String = query.trim().lowercase().replace('_', ' ').replace(Regex("\\s+"), " ")

    /** Headwords starting with [prefix], in alphabetical order, at most [limit]. */
    fun suggest(prefix: String, limit: Int = 40): List<String> {
        val p = normalize(prefix)
        if (p.isEmpty()) return emptyList()
        val b = bucketFor(p) ?: return emptyList()
        var i = b.keys.binarySearch(p).let { if (it < 0) -it - 1 else it }
        val out = ArrayList<String>()
        while (i < b.keys.size && out.size < limit && b.keys[i].startsWith(p)) {
            out += b.lines[i].split('\t')[1]
            i++
        }
        return out
    }

    /** Exact headword only. */
    fun entry(key: String): Entry? {
        val k = normalize(key)
        val b = bucketFor(k) ?: return null
        val i = b.keys.binarySearch(k)
        if (i < 0) return null
        val f = b.lines[i].split('\t', limit = 3)
        if (f.size < 3) return null
        return Entry(f[0], f[1], f[2].split('\u001e').map(::sense))
    }

    /**
     * Looks up what was typed: the word itself, else its base form ("ran" -> run, "boxes" -> box),
     * using WordNet's irregular forms and its suffix rules.
     */
    fun lookup(query: String): Entry? {
        val q = normalize(query)
        if (q.isEmpty()) return null
        entry(q)?.let { return it }
        for (base in baseForms(q)) {
            entry(base)?.let { return Entry(it.key, it.display, it.senses, from = q) }
        }
        return null
    }

    private fun baseForms(q: String): List<String> {
        val out = LinkedHashSet<String>()
        irregular[q]?.forEach { out += it.second }
        for ((suffix, ending) in RULES) {
            if (q.length > suffix.length && q.endsWith(suffix)) out += q.dropLast(suffix.length) + ending
        }
        return out.toList()
    }

    private fun sense(raw: String): Sense {
        val f = raw.split('\u001f')
        val pos = f.getOrNull(0)?.firstOrNull() ?: 'n'
        val gloss = f.getOrNull(1).orEmpty()
        val syn = f.getOrNull(2).orEmpty().split(", ").filter { it.isNotEmpty() }
        val ant = f.getOrNull(3).orEmpty().split(", ").filter { it.isNotEmpty() }
        val rel = f.getOrNull(4).orEmpty().split(", ").filter { it.isNotEmpty() }
        // A gloss is the definition, then its quoted examples: definition; "example"; "example".
        val q = gloss.indexOf('"')
        val definition = (if (q < 0) gloss else gloss.substring(0, q)).trim().trimEnd(';').trim()
        val examples = if (q < 0) emptyList() else QUOTED.findAll(gloss.substring(q)).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()
        return Sense(pos, definition, examples, syn, ant, rel)
    }

    companion object {
        private val QUOTED = Regex("\"([^\"]*)\"")

        // WordNet's morphy detachment rules for nouns, verbs and adjectives, most specific first.
        private val RULES = listOf(
            "sses" to "ss", "ches" to "ch", "shes" to "sh", "xes" to "x", "zes" to "z", "ses" to "s",
            "ies" to "y", "men" to "man", "es" to "e", "es" to "", "s" to "",
            "ied" to "y", "ed" to "e", "ed" to "", "ing" to "e", "ing" to "",
            "est" to "e", "est" to "", "er" to "e", "er" to "",
        )

        fun posName(pos: Char) = when (pos) {
            'v' -> "verb"
            'a' -> "adjective"
            'r' -> "adverb"
            else -> "noun"
        }
    }
}
