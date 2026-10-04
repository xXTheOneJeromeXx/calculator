package com.tbce.calc.notes

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The decoy notepad's own notes. These are ordinary, non-secret notes you can keep so the
 * disguise looks lived-in; they are stored in plain preferences on purpose. The scripture reader
 * and its notes are separate and encrypted.
 */
class NotesStore(context: Context) {
    private val prefs = context.getSharedPreferences("n", Context.MODE_PRIVATE)

    class Note(val id: Long, var body: String, var updated: Long) {
        val title: String get() = body.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty().ifEmpty { "New note" }
        val preview: String get() = body.lineSequence().drop(1).firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    }

    fun load(): MutableList<Note> {
        val out = ArrayList<Note>()
        try {
            val a = JSONArray(prefs.getString("v", "[]"))
            for (i in 0 until a.length()) a.getJSONObject(i).let { out += Note(it.getLong("id"), it.getString("b"), it.getLong("u")) }
        } catch (_: Exception) {
        }
        return out.sortedByDescending { it.updated }.toMutableList()
    }

    fun save(notes: List<Note>) {
        val a = JSONArray()
        for (n in notes) a.put(JSONObject().put("id", n.id).put("b", n.body).put("u", n.updated))
        prefs.edit().putString("v", a.toString()).apply()
    }
}
