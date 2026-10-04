package com.tbce.calc.vault

import com.tbce.calc.Config
import java.io.File
import java.nio.ByteBuffer

/**
 * Key hierarchy (brief section 5):
 *   KEK_code   = Argon2id(code, salt)
 *   KEK_device = Android Keystore key
 *   stored     = GCM_KEK_device( GCM_KEK_code( DEK ) )
 *
 * The code is never stored. A right code is recognised only because the inner GCM tag verifies,
 * and right and wrong codes run the same derivation.
 */
class KeyVault(
    private val dir: File,
    private val device: DeviceKey,
    private val memoryKb: Int = Config.KDF_MEMORY_KB,
    private val iterations: Int = Config.KDF_ITERATIONS,
    private val parallelism: Int = Config.KDF_PARALLELISM,
) {
    private val keyFile = File(dir, "c0")
    /** Wipe setting and failed-attempt count, sealed with the device key: [enabled byte][count int]. */
    private val guardFile = File(dir, "c1")
    private val dataDir = File(dir, "d")

    fun exists(): Boolean = keyFile.exists()

    /** First-run setup. The caller zeros [code]. */
    fun create(code: ByteArray): VaultSession {
        erase()
        val dek = Crypto.randomBytes(32)
        val salt = Crypto.randomBytes(16)
        val kek = Crypto.argon2id(code, salt, memoryKb, iterations, parallelism)
        val inner = try { Crypto.seal(kek, dek) } finally { kek.fill(0) }
        device.create()
        writeKey(salt, device.encrypt(inner))
        writeGuard(false, 0)
        return VaultSession(dek, dataDir)
    }

    /** Re-wraps the open vault's key under [newCode]; nothing else is re-encrypted. The caller zeros [newCode]. */
    fun changeCode(session: VaultSession, newCode: ByteArray) {
        check(!session.wiped)
        val salt = Crypto.randomBytes(16)
        val kek = Crypto.argon2id(newCode, salt, memoryKb, iterations, parallelism)
        val inner = try { Crypto.seal(kek, session.key()) } finally { kek.fill(0) }
        writeKey(salt, device.encrypt(inner))
    }

    private fun writeKey(salt: ByteArray, outer: ByteArray) {
        val header = ByteBuffer.allocate(HEADER).put(1).put(salt)
            .putInt(memoryKb).putInt(iterations).putInt(parallelism).array()
        writeAtomic(keyFile, header + outer)
    }

    /** Whether the vault erases itself after [Config.WIPE_AFTER] wrong codes in a row. */
    fun wipeEnabled(): Boolean = readGuard().first

    fun setWipeEnabled(enabled: Boolean) {
        writeGuard(enabled, readGuard().second)
    }

    private fun readGuard(): Pair<Boolean, Int> = try {
        val b = ByteBuffer.wrap(device.decrypt(guardFile.readBytes()))
        (b.get().toInt() == 1) to b.int
    } catch (e: Exception) {
        false to 0
    }

    private fun writeGuard(enabled: Boolean, count: Int) {
        try {
            writeAtomic(guardFile, device.encrypt(ByteBuffer.allocate(5).put(if (enabled) 1 else 0).putInt(count).array()))
        } catch (_: Exception) {
        }
    }

    /** Returns a session if [code] is right, null otherwise. The caller zeros [code]. */
    fun unlock(code: ByteArray): VaultSession? {
        val blob = try { keyFile.readBytes() } catch (e: Exception) { return null }
        if (blob.size <= HEADER) return null
        val b = ByteBuffer.wrap(blob)
        b.get()
        val salt = ByteArray(16).also { b.get(it) }
        val m = b.int
        val t = b.int
        val p = b.int
        val kek = Crypto.argon2id(code, salt, m, t, p)
        val session = try {
            val inner = device.decrypt(blob.copyOfRange(HEADER, blob.size))
            VaultSession(Crypto.open(kek, inner), dataDir)
        } catch (e: Exception) {
            null
        } finally {
            kek.fill(0)
        }
        // The guard file is rewritten on both paths, so a wrong code makes no extra disk activity.
        val (enabled, count) = readGuard()
        val failures = if (session != null) 0 else count + 1
        if (session == null && enabled && failures >= Config.WIPE_AFTER) {
            erase()
            return null
        }
        writeGuard(enabled, failures)
        return session
    }

    /** Wrong codes since the last right one (for tests). */
    internal fun failures(): Int = readGuard().second

    /** Crypto-erase: the wrapped key and the hardware key go first, then the data, best effort. */
    fun erase() {
        keyFile.delete()
        guardFile.delete()
        try { device.destroy() } catch (_: Exception) {}
        dataDir.deleteRecursively()
    }

    private companion object {
        const val HEADER = 1 + 16 + 4 + 4 + 4
    }
}

internal fun writeAtomic(target: File, bytes: ByteArray) {
    target.parentFile?.mkdirs()
    val tmp = File(target.parentFile, target.name + ".t")
    tmp.outputStream().use { it.write(bytes); it.fd.sync() }
    if (!tmp.renameTo(target)) {
        target.delete()
        tmp.renameTo(target)
    }
}
