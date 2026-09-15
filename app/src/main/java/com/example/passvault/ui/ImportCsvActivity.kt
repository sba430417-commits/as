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
import com.example.passvault.util.encryptedForStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/** استيراد CSV من Chrome مع تصنيف البريد والجوال تلقائيًا. */
class ImportCsvActivity : AppCompatActivity() {
    private lateinit var binding: ActivityImportCsvBinding
    private val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(::importFile) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImportCsvBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.btnPickFile.setOnClickListener { filePicker.launch("text/*") }
    }

    private fun importFile(uri: Uri) {
        lifecycleScope.launch {
            binding.progress.visibility = android.view.View.VISIBLE
            val accounts = withContext(Dispatchers.IO) { parseCsv(uri) }
            if (accounts.isEmpty()) {
                Toast.makeText(this@ImportCsvActivity, "ما لقينا بيانات صالحة في الملف", Toast.LENGTH_LONG).show()
            } else {
                AppDatabase.getInstance(this@ImportCsvActivity).accountDao()
                    .insertAll(accounts.map { it.encryptedForStorage() })
                Toast.makeText(this@ImportCsvActivity, "تم استيراد ${accounts.size} حساب بنجاح", Toast.LENGTH_LONG).show()
                finish()
            }
            binding.progress.visibility = android.view.View.GONE
        }
    }

    private fun parseCsv(uri: Uri): List<Account> {
        val result = mutableListOf<Account>()
        val inputStream = contentResolver.openInputStream(uri) ?: return result
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            val header = reader.readLine() ?: return result
            val columns = splitCsvLine(header).map { it.trim().lowercase() }
            val nameIdx = columns.indexOf("name")
            val urlIdx = columns.indexOf("url")
            val userIdx = columns.indexOf("username")
            val passIdx = columns.indexOf("password")

            reader.forEachLine { line ->
                val cols = splitCsvLine(line)
                val rawName = nameIdx.takeIf { it >= 0 }?.let { cols.getOrNull(it) }.clean()
                val url = urlIdx.takeIf { it >= 0 }?.let { cols.getOrNull(it) }.clean()
                val rawUsername = userIdx.takeIf { it >= 0 }?.let { cols.getOrNull(it) }.clean()
                val password = passIdx.takeIf { it >= 0 }?.let { cols.getOrNull(it) }.clean()
                val siteName = rawName ?: url ?: return@forEachLine

                // Chrome يضع البريد أحيانًا في username؛ ننقله للبريد تلقائيًا.
                val candidates = listOf(rawUsername, rawName, url).filterNotNull()
                val email = candidates.firstOrNull { EMAIL_REGEX.matches(it) }
                val phoneRaw = candidates.firstOrNull { isPhone(it) }
                val phone = phoneRaw?.let(::normalizePhone)
                val username = rawUsername?.takeUnless { it == email || isPhone(it) }

                result.add(Account(
                    siteName = siteName,
                    displayName = null,
                    username = username,
                    encryptedPassword = password,
                    email = email,
                    phone = phone,
                    source = "imported_csv",
                    category = intent.getStringExtra("category") ?: "عام"
                ))
            }
        }
        return result
    }

    private fun String?.clean(): String? = this?.trim()?.trim('"')?.takeUnless { it.isBlank() }

    private fun isPhone(value: String): Boolean {
        val digits = value.filter { it.isDigit() }
        return digits.length >= 7 && value.count { it == '@' } == 0 &&
            value.any { it.isDigit() } && value.all { it.isDigit() || it in "+()- ." }
    }

    /** الأرقام المحلية تتحول إلى +966، والأرقام الدولية الموجودة تبقى كما هي. */
    private fun normalizePhone(value: String): String {
        val compact = value.filter { it.isDigit() || it == '+' }
        return when {
            compact.startsWith("00") -> "+" + compact.drop(2)
            compact.startsWith("+") -> compact
            compact.startsWith("0") -> "+966" + compact.drop(1)
            else -> "+966$compact"
        }
    }

    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var insideQuotes = false
        for (c in line) {
            when {
                c == '"' -> insideQuotes = !insideQuotes
                c == ',' && !insideQuotes -> { result.add(current.toString()); current.clear() }
                else -> current.append(c)
            }
        }
        result.add(current.toString())
        return result
    }

    companion object { private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") }
}
