package com.tbce.calc.reader

import org.json.JSONArray
import org.json.JSONObject
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class BookMeta(val code: String, val name: String, val chapters: Int)

class Translation(val id: String, val abbr: String, val name: String, val books: List<BookMeta>) {
    val totalChapters = books.sumOf { it.chapters }
    private val starts = books.runningFold(0) { acc, b -> acc + b.chapters }

    fun bookIndex(code: String) = books.indexOfFirst { it.code == code }

    /** Position across the whole text, used by the swipe pager. */
    fun globalIndex(book: Int, chapter: Int) = starts[book] + chapter - 1

    /** (book index, chapter number) for a global index. */
    fun locate(global: Int): Pair<Int, Int> {
        val b = books.indices.last { starts[it] <= global }
        return b to (global - starts[b] + 1)
    }
}

class Meta(val translations: List<Translation>, val aliases: Map<String, List<String>>, val ui: Map<String, String>) {
    fun ui(key: String) = ui[key] ?: key
}

sealed interface Block
class Heading(val level: Int, val text: String) : Block
class Title(val text: String) : Block
/** A run of text; [verse] is the verse it belongs to, [start] is true where that verse begins. */
class Seg(val verse: Int, val start: Boolean, val text: String)
class Para(val style: String, val segs: List<Seg>) : Block
data object Break : Block

class Book(val name: String, val chapters: List<List<Block>>)

/**
 * Reads the encrypted packs built by tools/build_packs.py. [load] returns a pack's raw bytes by
 * file name (from the APK's assets in the app, from the source tree in tests).
 */
class Packs(private val load: (String) -> ByteArray) {
    private val key = K.get()

    private fun name(logical: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(logical.toByteArray()).joinToString("") { "%02x".format(it) }.substring(0, 16)
    }

    fun bytes(logical: String): ByteArray {
        val n = name(logical)
        val blob = load(n)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, blob, 0, 12))
        c.updateAAD(n.toByteArray())
        return inflate(c.doFinal(blob, 12, blob.size - 12))
    }

    fun text(logical: String) = String(bytes(logical), Charsets.UTF_8)

    fun meta(): Meta {
        val o = JSONObject(text("meta"))
        val ts = o.getJSONArray("translations").objects().map { t ->
            val books = t.getJSONArray("books").arrays().map { BookMeta(it.getString(0), it.getString(1), it.getInt(2)) }
            Translation(t.getString("id"), t.getString("abbr"), t.getString("name"), books)
        }
        val al = o.getJSONObject("aliases").let { a -> a.keys().asSequence().associateWith { k -> a.getJSONArray(k).strings() } }
        val ui = o.getJSONObject("ui").let { u -> u.keys().asSequence().associateWith { u.getString(it) } }
        return Meta(ts, al, ui)
    }

    fun book(translation: String, code: String): Book {
        val o = JSONObject(text("$translation/$code"))
        val chapters = o.getJSONArray("c").arrays().map { ch ->
            ch.arrays().map { b ->
                when (b.getString(0)) {
                    "h" -> Heading(b.getInt(1), b.getString(2))
                    "d" -> Title(b.getString(1))
                    "b" -> Break
                    else -> Para(b.getString(1), b.getJSONArray(2).arrays().map { s ->
                        val v = s.getInt(0)
                        Seg(kotlin.math.abs(v), v > 0, s.getString(1))
                    })
                }
            }
        }
        return Book(o.getString("n"), chapters)
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inf = Inflater()
        inf.setInput(data)
        val out = java.io.ByteArrayOutputStream(data.size * 4)
        val buf = ByteArray(64 * 1024)
        while (!inf.finished()) {
            val n = inf.inflate(buf)
            if (n == 0 && inf.needsInput()) break
            out.write(buf, 0, n)
        }
        inf.end()
        return out.toByteArray()
    }
}

private fun JSONArray.arrays() = (0 until length()).map { getJSONArray(it) }
private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
private fun JSONArray.strings() = (0 until length()).map { getString(it) }
