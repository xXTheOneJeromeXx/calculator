package com.tbce.calc.vault

import java.io.File

/**
 * An unlocked vault. Holds the DEK in memory until [wipe], and stores named items as
 * AES-256-GCM blobs under opaque file names.
 */
class VaultSession internal constructor(private val dek: ByteArray, private val dir: File) {
    private val encKey = Crypto.hmac(dek, "enc".toByteArray())
    private val nameKey = Crypto.hmac(dek, "name".toByteArray())

    var wiped = false
        private set

    fun put(name: String, data: ByteArray) {
        check(!wiped)
        val f = fileFor(name)
        writeAtomic(f, Crypto.seal(encKey, data, f.name.toByteArray()))
    }

    fun get(name: String): ByteArray? {
        check(!wiped)
        val f = fileFor(name)
        if (!f.exists()) return null
        return Crypto.open(encKey, f.readBytes(), f.name.toByteArray())
    }

    /** The DEK, for re-wrapping under a new code. */
    internal fun key(): ByteArray = dek

    /** Zeros every key this session holds. */
    fun wipe() {
        dek.fill(0)
        encKey.fill(0)
        nameKey.fill(0)
        wiped = true
    }

    internal fun keysAreZero() = dek.all { it.toInt() == 0 } && encKey.all { it.toInt() == 0 } && nameKey.all { it.toInt() == 0 }

    private fun fileFor(name: String): File {
        val h = Crypto.hmac(nameKey, name.toByteArray())
        return File(dir, h.take(8).joinToString("") { "%02x".format(it) })
    }
}
