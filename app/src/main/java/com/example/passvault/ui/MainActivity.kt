package com.example.passvault.ui

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.example.passvault.data.Account
import com.example.passvault.data.AppDatabase
import com.example.passvault.databinding.ActivityMainBinding
import com.example.passvault.util.AuthSession
import com.example.passvault.util.CategoryManager
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
    private var allAccounts: List<Account> = emptyList()
    private var currentCategory = "الكل"
    private val exportPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(::exportVault) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AuthSession.isUnlocked) { startActivity(Intent(this, AuthActivity::class.java)); finish(); return }
        binding = ActivityMainBinding.inflate(layoutInflater); setContentView(binding.root)
        startService(Intent().setComponent(ComponentName(this, com.example.passvault.util.TaskRemovalService::class.java)))
        adapter = AccountAdapter(
            onClick = { account -> startActivity(Intent(this, AccountDetailActivity::class.java).putExtra("account_id", account.id)) },
            onLongClick = { account -> selectionMode = true; selectedIds.add(account.id); updateSelectionUi(); adapter.refreshSelection(account.id) },
            isSelectionMode = { selectionMode },
            isSelected = { selectedIds.contains(it) },
            onSelectionChanged = { account ->
                if (!selectionMode) selectionMode = true
                if (!selectedIds.add(account.id)) selectedIds.remove(account.id)
                if (selectedIds.isEmpty()) selectionMode = false
                updateSelectionUi(); adapter.refreshSelection(account.id)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this); binding.recyclerView.adapter = adapter
        AppDatabase.getInstance(this).accountDao().getAll().observe(this) { list ->
            allAccounts = list; selectedIds.retainAll(list.map { it.id }.toSet()); renderCategory(); updateSelectionUi()
            binding.emptyState.visibility = if (filteredAccounts().isEmpty()) View.VISIBLE else View.GONE
        }
        binding.fabAdd.setOnClickListener { openAddAccount() }
        binding.btnImportChrome.setOnClickListener { openImport() }
        binding.btnExportVault.setOnClickListener { exportPicker.launch("your-account-backup.json") }
        binding.btnSelectAll.setOnClickListener {
            val ids = filteredAccounts().map { it.id }
            if (ids.all { selectedIds.contains(it) }) selectedIds.removeAll(ids.toSet()) else selectedIds.addAll(ids)
            if (selectedIds.isEmpty()) selectionMode = false
            updateSelectionUi(); adapter.submitList(filteredAccounts())
        }
        binding.btnMoveSelected.setOnClickListener { showMoveDialog() }
        binding.btnDeleteSelected.setOnClickListener { confirmDeleteSelected() }
        binding.btnAddCategory.setOnClickListener { showAddCategoryDialog() }
    }

    private fun filteredAccounts() = if (currentCategory == "الكل") allAccounts else allAccounts.filter { it.category == currentCategory }
    private fun renderCategory() { adapter.submitList(filteredAccounts()); renderCategoryButtons() }

    private fun renderCategoryButtons() {
        binding.categoryBar.removeAllViews()
        CategoryManager.get(this).forEach { category ->
            val button = MaterialButton(this).apply {
                text = category; isAllCaps = false; minHeight = 42; setOnClickListener { currentCategory = category; selectedIds.clear(); selectionMode = false; renderCategory(); updateSelectionUi() }
                if (category == currentCategory) setTextColor(getColor(com.example.passvault.R.color.primary))
            }
            binding.categoryBar.addView(button, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 48))
        }
    }

    private fun showAddCategoryDialog() {
        val input = EditText(this).apply { hint = "اسم التصنيف" }
        AlertDialog.Builder(this).setTitle("إنشاء تصنيف").setView(input)
            .setNegativeButton("إلغاء", null).setPositiveButton("إنشاء") { _, _ ->
                if (CategoryManager.add(this, input.text.toString())) { currentCategory = input.text.toString().trim(); renderCategory(); updateSelectionUi() }
                else Toast.makeText(this, "اكتب اسمًا جديدًا للتصنيف", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun openAddAccount() { startActivity(Intent(this, AddAccountActivity::class.java).putExtra("category", currentCategory.takeUnless { it == "الكل" } ?: "عام")) }
    private fun openImport() { startActivity(Intent(this, ImportCsvActivity::class.java).putExtra("category", currentCategory.takeUnless { it == "الكل" } ?: "عام")) }

    private fun updateSelectionUi() {
        binding.selectionHeader.visibility = if (selectionMode) View.VISIBLE else View.GONE
        binding.normalHeader.visibility = if (selectionMode) View.GONE else View.VISIBLE
        binding.tvSelectedCount.text = "تم تحديد ${selectedIds.size}"
        binding.btnSelectAll.text = if (filteredAccounts().isNotEmpty() && filteredAccounts().all { selectedIds.contains(it.id) }) "إلغاء الكل" else "تحديد الكل"
    }

    private fun showMoveDialog() {
        val categories = CategoryManager.get(this).filter { it != "الكل" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("نقل إلى تصنيف").setItems(categories) { _, which ->
            val target = categories[which]; val ids = selectedIds.toList()
            lifecycleScope.launch { AppDatabase.getInstance(this@MainActivity).accountDao().moveToCategory(ids, target); selectedIds.clear(); selectionMode = false; currentCategory = target; updateSelectionUi(); Toast.makeText(this@MainActivity, "تم نقل ${ids.size} حساب", Toast.LENGTH_SHORT).show() }
        }.setNegativeButton("إلغاء", null).show()
    }

    private fun confirmDeleteSelected() {
        if (selectedIds.isEmpty()) return
        AlertDialog.Builder(this).setTitle("حذف الحسابات").setMessage("هل تريد حذف ${selectedIds.size} حساب؟ لا يمكن التراجع عن هذا الإجراء.")
            .setNegativeButton("إلغاء", null).setPositiveButton("حذف") { _, _ -> val ids = selectedIds.toList(); lifecycleScope.launch { AppDatabase.getInstance(this@MainActivity).accountDao().deleteByIds(ids); selectedIds.clear(); selectionMode = false; updateSelectionUi(); Toast.makeText(this@MainActivity, "تم حذف ${ids.size} حساب", Toast.LENGTH_SHORT).show() } }.show()
    }

    override fun onUserLeaveHint() { super.onUserLeaveHint(); AuthSession.markBackgrounded() }
    override fun onResume() { super.onResume(); if (::binding.isInitialized && !AuthSession.isUnlocked && !redirectingToAuth) { redirectingToAuth = true; startActivity(Intent(this, AuthActivity::class.java)) } }

    private fun exportVault(uri: Uri) { lifecycleScope.launch { val json = withContext(Dispatchers.IO) { JSONArray().apply { allAccounts.forEach { a -> put(JSONObject().apply { put("siteName", a.siteName); put("displayName", a.displayName ?: ""); put("username", a.username ?: ""); put("password", a.encryptedPassword ?: ""); put("email", a.email ?: ""); put("phone", a.phone ?: ""); put("category", a.category) }) } }.toString(2) }; withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }; Toast.makeText(this@MainActivity, "تم تصدير الحسابات، احفظ الملف في مكان آمن", Toast.LENGTH_LONG).show() } }
}
