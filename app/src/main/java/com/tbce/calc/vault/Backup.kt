package com.tbce.calc.vault

import com.tbce.calc.Config
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Backup files (brief section 5): passphrase + Argon2id + AES-256-GCM. The file is
 * salt(16) || nonce(12) || ciphertext, with no magic bytes or version marker, so it looks
 * like opaque data. Import simply tries to decrypt.
 */
object Backup {
    fun seal(passphrase: CharArray, plain: ByteArray): ByteArray {
        val salt = Crypto.randomBytes(16)
        val key = derive(passphrase, salt)
        return try { salt + Crypto.seal(key, deflate(plain)) } finally { key.fill(0) }
    }

    /** Returns the contents, or null if the passphrase is wrong or the file is not a backup. */
    fun open(passphrase: CharArray, file: ByteArray): ByteArray? {
        if (file.size < 16 + Crypto.NONCE + 16) return null
        val key = derive(passphrase, file.copyOfRange(0, 16))
        return try {
            inflate(Crypto.open(key, file.copyOfRange(16, file.size)))
        } catch (e: Exception) {
            null
        } finally {
            key.fill(0)
        }
    }

    private fun derive(passphrase: CharArray, salt: ByteArray): ByteArray {
        val bytes = Charsets.UTF_8.encode(java.nio.CharBuffer.wrap(passphrase)).let { b -> ByteArray(b.remaining()).also { b.get(it) } }
        return try {
            Crypto.argon2id(bytes, salt, Config.KDF_MEMORY_KB, Config.KDF_ITERATIONS, Config.KDF_PARALLELISM)
        } finally {
            bytes.fill(0)
        }
    }

    private fun deflate(b: ByteArray): ByteArray {
        val d = Deflater(9)
        d.setInput(b); d.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.toByteArray()
    }

    private fun inflate(b: ByteArray): ByteArray {
        val i = Inflater()
        i.setInput(b)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (!i.finished()) {
            val n = i.inflate(buf)
            if (n == 0 && (i.needsInput() || i.needsDictionary())) break
            out.write(buf, 0, n)
        }
        i.end()
        return out.toByteArray()
    }
}
