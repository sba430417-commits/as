package com.example.passvault.ui

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.provider.Settings
import android.content.ComponentName
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.passvault.data.Account
import com.example.passvault.data.AppDatabase
import com.example.passvault.databinding.ActivityMainBinding
import com.example.passvault.util.AuthSession
import com.example.passvault.util.encryptedForStorage
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AccountAdapter
    private var redirectingToAuth = false

    private val exportPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { exportVault(it) }
    }

    private val importPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importVault(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!AuthSession.isUnlocked) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        startService(Intent().setComponent(ComponentName(this, com.example.passvault.util.TaskRemovalService::class.java)))

        adapter = AccountAdapter { account ->
            val intent = Intent(this, AccountDetailActivity::class.java)
            intent.putExtra("account_id", account.id)
            startActivity(intent)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        val dao = AppDatabase.getInstance(this).accountDao()
        dao.getAll().observe(this) { list ->
            adapter.submitList(list)
            binding.emptyState.visibility = if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddAccountActivity::class.java))
        }

        binding.btnImportChrome.setOnClickListener {
            startActivity(Intent(this, ImportCsvActivity::class.java))
        }

        binding.btnEnableAutofill.setOnClickListener {
            startActivity(Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                data = Uri.parse("package:$packageName")
            })
        }

        binding.btnExportVault.setOnClickListener {
            exportPicker.launch("your-account-backup.json")
        }
        binding.btnImportVault.setOnClickListener {
            importPicker.launch(arrayOf("application/json", "text/*"))
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // يبقى الدخول صالحًا 30 دقيقة عند الرجوع من الخلفية.
        AuthSession.markBackgrounded()
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized && !AuthSession.isUnlocked && !redirectingToAuth) {
            redirectingToAuth = true
            startActivity(Intent(this, AuthActivity::class.java))
        }
    }

    private fun exportVault(uri: Uri) {
        lifecycleScope.launch {
            val json = withContext(Dispatchers.IO) {
                val array = JSONArray()
                AppDatabase.getInstance(this@MainActivity).accountDao().getAllNow().forEach { account ->
                    array.put(JSONObject().apply {
                        put("siteName", account.siteName)
                        put("displayName", account.displayName ?: "")
                        put("username", account.username ?: "")
                        put("password", account.encryptedPassword ?: "")
                        put("email", account.email ?: "")
                        put("phone", account.phone ?: "")
                        put("category", account.category)
                    })
                }
                array.toString(2)
            }
            withContext(Dispatchers.IO) {
                contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }
            Toast.makeText(this@MainActivity, "تم تصدير الحسابات، احفظ الملف في مكان آمن", Toast.LENGTH_LONG).show()
        }
    }

    private fun importVault(uri: Uri) {
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) {
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return@withContext 0
                val array = JSONArray(text)
                val accounts = (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    val site = item.optString("siteName").trim()
                    if (site.isBlank()) return@mapNotNull null
                    Account(
                        siteName = site,
                        displayName = item.optString("displayName").ifBlank { null },
                        username = item.optString("username").ifBlank { null },
                        encryptedPassword = item.optString("password").ifBlank { null },
                        email = item.optString("email").ifBlank { null },
                        phone = item.optString("phone").ifBlank { null },
                        source = "vault_import",
                        category = item.optString("category").ifBlank { "عام" }
                    )
                }
                AppDatabase.getInstance(this@MainActivity).accountDao().insertAll(accounts.map { it.encryptedForStorage() })
                accounts.size
            }
            Toast.makeText(this@MainActivity, "تمت استعادة $count حساب", Toast.LENGTH_LONG).show()
        }
    }
}
