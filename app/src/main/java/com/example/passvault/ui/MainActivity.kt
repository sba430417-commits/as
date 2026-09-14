package com.example.passvault.ui

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.passvault.data.Account
import com.example.passvault.data.AppDatabase
import com.example.passvault.databinding.ActivityMainBinding
import com.example.passvault.util.AuthSession
import com.example.passvault.util.encryptedForStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AccountAdapter
    private var redirectingToAuth = false
    private var selectionMode = false
    private val selectedIds = linkedSetOf<Long>()

    private val exportPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(::exportVault) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AuthSession.isUnlocked) { startActivity(Intent(this, AuthActivity::class.java)); finish(); return }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        startService(Intent().setComponent(ComponentName(this, com.example.passvault.util.TaskRemovalService::class.java)))

        adapter = AccountAdapter(
            onClick = { account -> startActivity(Intent(this, AccountDetailActivity::class.java).putExtra("account_id", account.id)) },
            onLongClick = { account ->
                if (!selectionMode) selectionMode = true
                selectedIds.add(account.id)
                updateSelectionUi()
                adapter.notifyDataSetChanged()
            },
            isSelectionMode = { selectionMode },
            isSelected = { selectedIds.contains(it) },
            onSelectionChanged = { account ->
                if (!selectionMode) selectionMode = true
                if (!selectedIds.add(account.id)) selectedIds.remove(account.id)
                if (selectedIds.isEmpty()) selectionMode = false
                updateSelectionUi()
                adapter.notifyDataSetChanged()
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        AppDatabase.getInstance(this).accountDao().getAll().observe(this) { list ->
            adapter.submitList(list)
            selectedIds.retainAll(list.map { it.id }.toSet())
            binding.emptyState.visibility = if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            updateSelectionUi()
        }
        binding.fabAdd.setOnClickListener { startActivity(Intent(this, AddAccountActivity::class.java)) }
        binding.btnImportChrome.setOnClickListener { startActivity(Intent(this, ImportCsvActivity::class.java)) }
        binding.btnExportVault.setOnClickListener { exportPicker.launch("your-account-backup.json") }
        binding.btnSelectAll.setOnClickListener {
            val all = adapter.currentList.map { it.id }
            if (selectedIds.size == all.size) selectedIds.clear() else selectedIds.addAll(all)
            updateSelectionUi(); adapter.notifyDataSetChanged()
        }
        binding.btnDeleteSelected.setOnClickListener { confirmDeleteSelected() }
    }

    private fun updateSelectionUi() {
        binding.selectionHeader.visibility = if (selectionMode) android.view.View.VISIBLE else android.view.View.GONE
        binding.normalHeader.visibility = if (selectionMode) android.view.View.GONE else android.view.View.VISIBLE
        binding.tvSelectedCount.text = "تم تحديد ${selectedIds.size}"
        binding.btnSelectAll.text = if (selectedIds.size == adapter.currentList.size && adapter.currentList.isNotEmpty()) "إلغاء الكل" else "تحديد الكل"
    }

    private fun confirmDeleteSelected() {
        if (selectedIds.isEmpty()) return
        AlertDialog.Builder(this).setTitle("حذف الحسابات")
            .setMessage("هل تريد حذف ${selectedIds.size} حساب؟ لا يمكن التراجع عن هذا الإجراء.")
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("حذف") { _, _ ->
                val ids = selectedIds.toList()
                lifecycleScope.launch {
                    AppDatabase.getInstance(this@MainActivity).accountDao().deleteByIds(ids)
                    selectedIds.clear(); selectionMode = false; updateSelectionUi()
                    Toast.makeText(this@MainActivity, "تم حذف ${ids.size} حساب", Toast.LENGTH_SHORT).show()
                }
            }.show()
    }

    override fun onUserLeaveHint() { super.onUserLeaveHint(); AuthSession.markBackgrounded() }
    override fun onResume() { super.onResume(); if (::binding.isInitialized && !AuthSession.isUnlocked && !redirectingToAuth) { redirectingToAuth = true; startActivity(Intent(this, AuthActivity::class.java)) } }

    private fun exportVault(uri: Uri) {
        lifecycleScope.launch {
            val json = withContext(Dispatchers.IO) {
                JSONArray().apply { AppDatabase.getInstance(this@MainActivity).accountDao().getAllNow().forEach { a -> put(JSONObject().apply { put("siteName", a.siteName); put("displayName", a.displayName ?: ""); put("username", a.username ?: ""); put("password", a.encryptedPassword ?: ""); put("email", a.email ?: ""); put("phone", a.phone ?: ""); put("category", a.category) }) } }.toString(2)
            }
            withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }
            Toast.makeText(this@MainActivity, "تم تصدير الحسابات، احفظ الملف في مكان آمن", Toast.LENGTH_LONG).show()
        }
    }
}
