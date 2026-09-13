package com.example.passvault.ui

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.passvault.data.Account
import com.example.passvault.data.AppDatabase
import com.example.passvault.databinding.ActivityImportCsvBinding
import com.example.passvault.util.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * كروم يسمح بتصدير كلمات المرور المحفوظة كملف CSV يدويًا من:
 * إعدادات كروم → كلمات المرور → (⋮) → تصدير كلمات المرور
 * هذا الملف يحتوي أعمدة: name, url, username, password
 * هذي الشاشة تقرأ هذا الملف وتضيف الحسابات تلقائيًا لقاعدة بياناتنا
 * المحلية المشفّرة. ما فيه أي وصول مباشر أو خفي لبيانات جوجل.
 */
class ImportCsvActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImportCsvBinding

    private val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { importFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImportCsvBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.btnPickFile.setOnClickListener {
            filePicker.launch("text/*")
        }
    }

    private fun importFile(uri: Uri) {
        lifecycleScope.launch {
            binding.progress.visibility = android.view.View.VISIBLE
            val accounts = withContext(Dispatchers.IO) { parseCsv(uri) }

            if (accounts.isEmpty()) {
                Toast.makeText(this@ImportCsvActivity, "ما لقينا بيانات صالحة في الملف", Toast.LENGTH_LONG).show()
            } else {
                AppDatabase.getInstance(this@ImportCsvActivity).accountDao().insertAll(accounts)
                Toast.makeText(
                    this@ImportCsvActivity,
                    "تم استيراد ${accounts.size} حساب بنجاح",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
            binding.progress.visibility = android.view.View.GONE
        }
    }

    /**
     * صيغة تصدير كروم القياسية: name,url,username,password
     * نتعامل بمرونة مع ترتيب الأعمدة عن طريق قراءة الهيدر أولاً.
     */
    private fun parseCsv(uri: Uri): List<Account> {
        val result = mutableListOf<Account>()
        val inputStream = contentResolver.openInputStream(uri) ?: return result
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            val header = reader.readLine() ?: return result
            val columns = header.split(",").map { it.trim().lowercase() }

            val nameIdx = columns.indexOf("name")
            val urlIdx = columns.indexOf("url")
            val userIdx = columns.indexOf("username")
            val passIdx = columns.indexOf("password")

            reader.forEachLine { line ->
                val cols = splitCsvLine(line)
                if (cols.isEmpty()) return@forEachLine

                val name = nameIdx.takeIf { it >= 0 }?.let { cols.getOrNull(it) }
                val url = urlIdx.takeIf { it >= 0 }?.let { cols.getOrNull(it) }
                val username = userIdx.takeIf { it >= 0 }?.let { cols.getOrNull(it) }
                val password = passIdx.takeIf { it >= 0 }?.let { cols.getOrNull(it) }

                val siteName = (name ?: url)?.trim().takeUnless { it.isNullOrBlank() } ?: return@forEachLine

                result.add(
                    Account(
                        siteName = siteName,
                        displayName = null,
                        username = username?.takeUnless { it.isBlank() },
                        encryptedPassword = password?.takeUnless { it.isBlank() }?.let { CryptoManager.encrypt(it) },
                        email = null,
                        phone = null,
                        source = "imported_csv"
                    )
                )
            }
        }
        return result
    }

    /** تقسيم بسيط لسطر CSV مع دعم القيم المحاطة بعلامات اقتباس */
    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var insideQuotes = false
        for (c in line) {
            when {
                c == '"' -> insideQuotes = !insideQuotes
                c == ',' && !insideQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
        }
        result.add(current.toString())
        return result
    }
}
