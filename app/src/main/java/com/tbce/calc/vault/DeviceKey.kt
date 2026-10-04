package com.tbce.calc.vault

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** KEK_device: a key that never leaves the phone's secure hardware. */
interface DeviceKey {
    fun create()
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(sealed: ByteArray): ByteArray
    fun destroy()
}

/** Android Keystore AES-256-GCM key, StrongBox-backed when the phone has it, otherwise TEE. */
class KeystoreDeviceKey(private val context: Context) : DeviceKey {
    private val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    override fun create() {
        destroy()
        val strongBox = Build.VERSION.SDK_INT >= 28 &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        try {
            generate(strongBox)
        } catch (e: Exception) {
            if (strongBox && Build.VERSION.SDK_INT >= 28 && e is StrongBoxUnavailableException) generate(false)
            else throw e
        }
    }

    private fun generate(strongBox: Boolean) {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .apply { if (strongBox && Build.VERSION.SDK_INT >= 28) setIsStrongBoxBacked(true) }
            .build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(spec) }.generateKey()
    }

    private fun key() = store.getKey(ALIAS, null) as SecretKey

    override fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        return c.iv + c.doFinal(plain)
    }

    override fun decrypt(sealed: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, Crypto.NONCE))
        return c.doFinal(sealed, Crypto.NONCE, sealed.size - Crypto.NONCE)
    }

    override fun destroy() {
        if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS)
    }

    private companion object {
        const val ALIAS = "c0"
    }
}
