package com.example.passvault.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * يحتفظ بحالة إعداد الحماية محليًا بشكل مشفّر.
 * لا يتم تخزين رمز قفل الجهاز أو أي PIN؛ التحقق يتم بواسطة نظام أندرويد.
 */
object PrefsManager {

    private const val PREFS_NAME = "passvault_secure_prefs"
    private const val KEY_SETUP_DONE = "setup_done"

    private fun prefs(context: Context) = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun isSetupDone(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SETUP_DONE, false)

    fun markSetupDone(context: Context) {
        prefs(context).edit().putBoolean(KEY_SETUP_DONE, true).apply()
    }
}
