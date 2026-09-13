package com.example.passvault.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.passvault.util.PrefsManager

/**
 * شاشة توافق قديمة؛ تدفق الحماية الجديد يبدأ من AuthActivity ويستخدم
 * بصمة المستخدم ثم رمز قفل الجهاز مباشرة.
 */
class SetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PrefsManager.markSetupDone(this)
        startActivity(Intent(this, AuthActivity::class.java))
        finish()
    }
}
