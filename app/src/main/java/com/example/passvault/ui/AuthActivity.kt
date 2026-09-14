package com.example.passvault.ui

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.example.passvault.databinding.ActivityAuthBinding
import com.example.passvault.util.AuthSession
import com.example.passvault.util.UpdateManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** شاشة الدخول: بصمة أولاً، ثم رمز قفل الجهاز. */
class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private var deviceCredentialStarted = false
    private var updateDialog: AlertDialog? = null
    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id != UpdateManager.savedDownloadId(context)) return
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val uri = manager.getUriForDownloadedFile(id)
            if (uri == null) {
                Toast.makeText(context, "تعذر تنزيل التحديث", Toast.LENGTH_LONG).show()
                return
            }
            UpdateManager.clearSavedDownload(context)
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        AuthSession.isUnlocked = false
        binding.btnLogin.setOnClickListener { startAuthentication() }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), RECEIVER_NOT_EXPORTED)
        else registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        checkForRequiredUpdate()
    }

    private fun checkForRequiredUpdate() {
        binding.btnLogin.isEnabled = false
        lifecycleScope.launch {
            val release = UpdateManager.latestRelease()
            val latestCommit = UpdateManager.latestMainCommit()
            binding.btnLogin.isEnabled = true
            val commitIsNew = UpdateManager.isCommitOutdated(latestCommit)
            val releaseIsNew = release != null && UpdateManager.isNewer(release)
            val needsUpdate = releaseIsNew || commitIsNew
            if (needsUpdate) {
                if (release != null && (releaseIsNew || UpdateManager.releaseContainsCommit(release, latestCommit))) showRequiredUpdate(release)
                else showReleaseRequiredMessage()
            }
        }
    }

    private fun showReleaseRequiredMessage() {
        AlertDialog.Builder(this)
            .setTitle("تحديث مطلوب")
            .setMessage("تم العثور على نسخة أحدث من التطبيق، لكن لم يتم نشر ملف APK داخل GitHub Release بعد. يجب نشر ملف APK ثم تحديث التطبيق قبل المتابعة.")
            .setCancelable(false)
            .setPositiveButton("فتح GitHub") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sba430417-commits/as/releases")))
            }
            .show()
    }

    private fun showRequiredUpdate(release: UpdateManager.Release) {
        updateDialog = AlertDialog.Builder(this)
            .setTitle("تحديث مطلوب")
            .setMessage("يتوفر إصدار أحدث (${release.versionName}). يجب تحديث التطبيق قبل المتابعة.")
            .setCancelable(false)
            .setPositiveButton("تنزيل التحديث") { _, _ ->
                UpdateManager.startDownload(this, release)
                Toast.makeText(this, "بدأ تنزيل التحديث وسيظهر المثبت بعد اكتماله.", Toast.LENGTH_LONG).show()
                showRequiredUpdate(release)
            }.create()
        updateDialog?.show()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(downloadReceiver) }
        updateDialog?.dismiss()
        super.onDestroy()
    }

    private fun startAuthentication() {
        AlertDialog.Builder(this)
            .setTitle("طريقة تسجيل الدخول")
            .setItems(arrayOf("البصمة", "رمز PIN للجوال")) { _, which ->
                if (which == 0) authenticateWithBiometric() else authenticateWithDeviceCredential()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun authenticateWithBiometric() {
        val biometricAvailable = BiometricManager.from(this).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
        if (!biometricAvailable) {
            Toast.makeText(this, "البصمة غير مفعلة على هذا الجهاز", Toast.LENGTH_SHORT).show()
            return
        }
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                AuthSession.isUnlocked = true
                startActivity(Intent(this@AuthActivity, MainActivity::class.java))
                finish()
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
