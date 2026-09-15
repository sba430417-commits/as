package com.example.passvault.ui

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
    private var downloadStarted = false
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
            openInstaller(uri)
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

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) openCompletedDownloadIfReady()
    }

    private fun openCompletedDownloadIfReady() {
        val id = UpdateManager.savedDownloadId(this)
        if (id == -1L) return
        val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = manager.query(DownloadManager.Query().setFilterById(id))
        query.use { cursor ->
            if (cursor.moveToFirst() && cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL) {
                manager.getUriForDownloadedFile(id)?.let {
                    UpdateManager.clearSavedDownload(this)
                    openInstaller(it)
                }
            }
        }
    }

    private fun openInstaller(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            Toast.makeText(this, "فعّل السماح بالتثبيت، وسيظهر مثبت التحديث تلقائيًا بعد الرجوع", Toast.LENGTH_LONG).show()
            return
        }
        startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
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
            if (needsUpdate && release != null && (releaseIsNew || UpdateManager.releaseContainsCommit(release, latestCommit)) && !downloadStarted) {
                downloadStarted = true
                binding.btnLogin.isEnabled = false
                UpdateManager.startDownload(this@AuthActivity, release)
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
                updateDialog?.dismiss()
                Toast.makeText(this, "سيظهر مثبت Android تلقائيًا عند اكتمال التنزيل.", Toast.LENGTH_LONG).show()
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
