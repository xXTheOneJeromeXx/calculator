package com.tbce.calc.dictionary

import android.content.Context

/**
 * The decoy dictionary's recent lookups, so the disguise looks used. Plain preferences on purpose.
 * Only headwords that were actually opened are kept, never raw typed text.
 */
class DictionaryStore(context: Context) {
    private val prefs = context.getSharedPreferences("w", Context.MODE_PRIVATE)

    fun recent(): List<String> = prefs.getString("r", "").orEmpty().split('\n').filter { it.isNotEmpty() }

    fun add(word: String) {
        val list = listOf(word) + recent().filter { it != word }
        prefs.edit().putString("r", list.take(MAX).joinToString("\n")).apply()
    }

    fun clear() = prefs.edit().remove("r").apply()

    private companion object {
        const val MAX = 20
    }
}
