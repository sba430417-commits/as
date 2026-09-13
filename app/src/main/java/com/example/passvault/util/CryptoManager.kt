package com.example.passvault.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** تشفير الحقول الحساسة باستخدام مفتاح لا يغادر Android Keystore. */
object CryptoManager {
    private const val KEY_ALIAS = "passvault_master_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFIX = "enc1:"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return generator.generateKey()
    }

    fun isEncrypted(value: String?): Boolean = value?.startsWith(PREFIX) == true

    fun encrypt(value: String): String {
        if (value.isEmpty() || isEncrypted(value)) return value
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val body = Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        return "$PREFIX$iv:$body"
    }

    fun decrypt(value: String?): String {
        if (value.isNullOrEmpty() || !isEncrypted(value)) return value.orEmpty()
        return try {
            val parts = value.removePrefix(PREFIX).split(":", limit = 2)
            if (parts.size != 2) return ""
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128,
                Base64.decode(parts[0], Base64.NO_WRAP)))
            val plain = String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
            // يدعم ترحيل كلمة المرور التي كانت مخزنة بالصيغة القديمة IV:CIPHERTEXT.
            if (plain.count { it == ':' } == 1 && !plain.startsWith(PREFIX)) decryptLegacy(plain) else plain
        } catch (_: Exception) { "" }
    }

    private fun decryptLegacy(value: String): String = try {
        val parts = value.split(":", limit = 2)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128,
            Base64.decode(parts[0], Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    } catch (_: Exception) { value }
}
