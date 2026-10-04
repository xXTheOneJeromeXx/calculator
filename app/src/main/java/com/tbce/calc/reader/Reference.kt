package com.tbce.calc.reader

/** A place in the text: book code, chapter, and optionally a verse range. */
data class Ref(val book: String, val chapter: Int, val verse: Int = 0, val verseEnd: Int = 0)

/**
 * Parses typed references such as "John 3:16", "jn 3 16", "1 cor 13:4-7", "Ps 23" or "Jude 5".
 * [aliases] maps book codes to lower-case names and abbreviations; [chapters] gives each book's
 * chapter count so out-of-range chapters are rejected.
 */
class ReferenceParser(aliases: Map<String, List<String>>, private val chapters: Map<String, Int>) {
    private val names: List<Pair<String, String>> =
        aliases.flatMap { (code, list) -> (list + code.lowercase()).map { norm(it) to code } }
            .distinct()
            .sortedByDescending { it.first.length }

    fun parse(input: String): Ref? {
        val q = norm(input)
        if (q.isEmpty()) return null
        for ((name, code) in names) {
            if (!q.startsWith(name)) continue
            val rest = q.substring(name.length)
            if (rest.isNotEmpty() && rest[0].isLetter()) continue
            val nums = Regex("\\d+").findAll(rest).map { it.value.toInt() }.toList()
            if (rest.isNotBlank() && !rest.matches(Regex("[\\s\\d:.,\\-–]*"))) continue
            val max = chapters[code] ?: return null
            return when {
                nums.isEmpty() -> Ref(code, 1)
                // One-chapter books: "Jude 5" means verse 5.
                max == 1 && nums.size == 1 && nums[0] > 1 -> Ref(code, 1, nums[0])
                nums[0] !in 1..max -> null
                nums.size == 1 -> Ref(code, nums[0])
                nums.size == 2 -> Ref(code, nums[0], nums[1])
                else -> Ref(code, nums[0], nums[1], nums[2])
            }
        }
        return null
    }

    private fun norm(s: String) = s.lowercase().replace('.', ' ').replace(Regex("\\s+"), " ").trim()
}
