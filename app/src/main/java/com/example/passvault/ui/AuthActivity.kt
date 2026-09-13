package com.example.passvault.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.example.passvault.databinding.ActivityAuthBinding
import com.example.passvault.util.AuthSession

/** شاشة الدخول: بصمة أولاً، ثم رمز قفل الجهاز. */
class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private var deviceCredentialStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        AuthSession.isUnlocked = false
        binding.btnLogin.setOnClickListener { startAuthentication() }
    }

    private fun startAuthentication() {
        val biometricAvailable = BiometricManager.from(this).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS

        if (biometricAvailable) authenticateWithBiometric() else authenticateWithDeviceCredential()
    }

    private fun authenticateWithBiometric() {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                // انتظر دورة واجهة قصيرة حتى تُغلق نافذة البصمة قبل فتح نافذة رمز الجهاز.
                binding.root.postDelayed({ authenticateWithDeviceCredential() }, 300)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                Toast.makeText(this@AuthActivity, "لم تكتمل البصمة", Toast.LENGTH_SHORT).show()
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("تأكيد البصمة")
            .setSubtitle("استخدم بصمتك للمتابعة")
            .setNegativeButtonText("إلغاء")
            .build()
        prompt.authenticate(promptInfo)
    }

    private fun authenticateWithDeviceCredential() {
        if (deviceCredentialStarted) return
        deviceCredentialStarted = true

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                AuthSession.isUnlocked = true
                startActivity(Intent(this@AuthActivity, MainActivity::class.java))
                finish()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                deviceCredentialStarted = false
                Toast.makeText(this@AuthActivity, "لم يتم التحقق من رمز الجهاز", Toast.LENGTH_SHORT).show()
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("أدخل رمز الجهاز")
            .setSubtitle("استخدم رمز قفل هاتفك للمتابعة")
            .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(promptInfo)
    }
}
