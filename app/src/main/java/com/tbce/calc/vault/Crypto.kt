package com.tbce.calc.vault

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object Crypto {
    private val random = SecureRandom()
    const val NONCE = 12
    private const val TAG_BITS = 128

    fun randomBytes(n: Int) = ByteArray(n).also { random.nextBytes(it) }

    /** Argon2id. The caller zeros the returned key. */
    fun argon2id(secret: ByteArray, salt: ByteArray, memoryKb: Int, iterations: Int, parallelism: Int): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withMemoryAsKB(memoryKb)
            .withIterations(iterations)
            .withParallelism(parallelism)
            .build()
        val out = ByteArray(32)
        Argon2BytesGenerator().apply { init(params) }.generateBytes(secret, out)
        return out
    }

    /** AES-256-GCM with a fresh random nonce. Output: nonce || ciphertext+tag. */
    fun seal(key: ByteArray, plain: ByteArray, aad: ByteArray? = null): ByteArray {
        val nonce = randomBytes(NONCE)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        aad?.let { c.updateAAD(it) }
        return nonce + c.doFinal(plain)
    }

    /** Throws [javax.crypto.AEADBadTagException] if the key is wrong or the data was altered. */
    fun open(key: ByteArray, sealed: ByteArray, aad: ByteArray? = null): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, sealed, 0, NONCE))
        aad?.let { c.updateAAD(it) }
        return c.doFinal(sealed, NONCE, sealed.size - NONCE)
    }

    fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val m = Mac.getInstance("HmacSHA256")
        m.init(SecretKeySpec(key, "HmacSHA256"))
        return m.doFinal(data)
    }
}
