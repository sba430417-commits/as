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
import com.example.passvault.R
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
    private var currentCategory: String? = null
    private val exportPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(::exportVault) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AuthSession.isUnlocked) { startActivity(Intent(this, AuthActivity::class.java)); finish(); return }
        binding = ActivityMainBinding.inflate(layoutInflater); setContentView(binding.root)
        startService(Intent().setComponent(ComponentName(this, com.example.passvault.util.TaskRemovalService::class.java)))
        adapter = AccountAdapter(
            onClick = { account -> startActivity(Intent(this, AccountDetailActivity::class.java).putExtra("account_id", account.id)) },
            onLongClick = { account -> selectionMode = true; selectedIds.add(account.id); updateSelectionUi(); adapter.refreshSelection(account.id) },
            isSelectionMode = { selectionMode }, isSelected = { selectedIds.contains(it) },
            onSelectionChanged = { account -> if (!selectionMode) selectionMode = true; if (!selectedIds.add(account.id)) selectedIds.remove(account.id); if (selectedIds.isEmpty()) selectionMode = false; updateSelectionUi(); adapter.refreshSelection(account.id) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this); binding.recyclerView.adapter = adapter
        AppDatabase.getInstance(this).accountDao().getAll().observe(this) { list -> allAccounts = list; selectedIds.retainAll(list.map { it.id }.toSet()); if (currentCategory == null) showCategoryHome() else renderAccounts(); updateSelectionUi() }
        binding.btnAddCategoryHome.setOnClickListener { showAddCategoryDialog() }
        binding.btnBackCategories.setOnClickListener { showCategoryHome() }
        binding.fabAdd.setOnClickListener { openAddAccount() }
        binding.btnImportChrome.setOnClickListener { openImport() }
        binding.btnExportVault.setOnClickListener { requestExportAuthentication() }
        binding.btnSelectAll.setOnClickListener { val ids = filteredAccounts().map { it.id }; if (ids.all { selectedIds.contains(it) }) selectedIds.removeAll(ids.toSet()) else selectedIds.addAll(ids); if (selectedIds.isEmpty()) selectionMode = false; updateSelectionUi(); adapter.submitList(filteredAccounts()) }
        binding.btnMoveSelected.setOnClickListener { showMoveDialog() }
        binding.btnDeleteSelected.setOnClickListener { confirmDeleteSelected() }
        showCategoryHome()
    }

    private fun isMainCategory(account: Account) = account.category == "الرئيسي" || account.category == "عام"
    private fun filteredAccounts() = if (currentCategory == "الرئيسي") allAccounts.filter(::isMainCategory) else allAccounts.filter { it.category == currentCategory }

    private fun showCategoryHome() {
        currentCategory = null; selectionMode = false; selectedIds.clear(); binding.categoryHomeHeader.visibility = View.VISIBLE; binding.categoryList.visibility = View.VISIBLE; binding.normalHeader.visibility = View.GONE; binding.selectionHeader.visibility = View.GONE; binding.recyclerView.visibility = View.GONE; binding.emptyState.visibility = View.GONE; binding.fabAdd.visibility = View.GONE
        binding.categoryList.removeAllViews()
        CategoryManager.get(this).filter { it != "الكل" }.forEach { category ->
            val count = if (category == "الرئيسي") allAccounts.count(::isMainCategory) else allAccounts.count { it.category == category }
            val button = MaterialButton(this).apply { text = "$category   ($count)"; isAllCaps = false; gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL; minHeight = 58; setPadding(20, 0, 20, 0); setOnClickListener { openCategory(category) } }
            binding.categoryList.addView(button, LinearLayout.LayoutParams(-1, 58).apply { bottomMargin = 10 })
        }
    }

    private fun openCategory(category: String) { currentCategory = category; binding.categoryHomeHeader.visibility = View.GONE; binding.categoryList.visibility = View.GONE; binding.normalHeader.visibility = View.VISIBLE; binding.tvCurrentCategory.text = category; binding.recyclerView.visibility = View.VISIBLE; binding.fabAdd.visibility = View.VISIBLE; renderAccounts() }
    private fun renderAccounts() { adapter.submitList(filteredAccounts()); binding.emptyState.visibility = if (filteredAccounts().isEmpty()) View.VISIBLE else View.GONE; updateSelectionUi() }
    private fun openAddAccount() { startActivity(Intent(this, AddAccountActivity::class.java).putExtra("category", currentCategory ?: "الرئيسي")) }
    private fun openImport() { startActivity(Intent(this, ImportCsvActivity::class.java).putExtra("category", currentCategory ?: "الرئيسي")) }

    private fun showAddCategoryDialog() { val input = EditText(this).apply { hint = "اسم القسم" }; AlertDialog.Builder(this).setTitle("إنشاء قسم").setView(input).setNegativeButton("إلغاء", null).setPositiveButton("إنشاء") { _, _ -> if (CategoryManager.add(this, input.text.toString())) showCategoryHome() else Toast.makeText(this, "اكتب اسمًا جديدًا للقسم", Toast.LENGTH_SHORT).show() }.show() }
    private fun updateSelectionUi() { binding.selectionHeader.visibility = if (selectionMode && currentCategory != null) View.VISIBLE else View.GONE; binding.tvSelectedCount.text = "تم تحديد ${selectedIds.size}"; binding.btnSelectAll.text = if (filteredAccounts().isNotEmpty() && filteredAccounts().all { selectedIds.contains(it.id) }) "إلغاء الكل" else "تحديد الكل" }

    private fun showMoveDialog() { val categories = CategoryManager.get(this).filter { it != "الكل" && it != currentCategory }.toTypedArray(); AlertDialog.Builder(this).setTitle("نقل إلى قسم").setItems(categories) { _, which -> val ids = selectedIds.toList(); lifecycleScope.launch { AppDatabase.getInstance(this@MainActivity).accountDao().moveToCategory(ids, categories[which]); selectedIds.clear(); selectionMode = false; renderAccounts(); Toast.makeText(this@MainActivity, "تم نقل ${ids.size} حساب", Toast.LENGTH_SHORT).show() } }.setNegativeButton("إلغاء", null).show() }
    private fun confirmDeleteSelected() { if (selectedIds.isEmpty()) return; AlertDialog.Builder(this).setTitle("حذف الحسابات").setMessage("هل تريد حذف ${selectedIds.size} حساب؟ لا يمكن التراجع عن هذا الإجراء.").setNegativeButton("إلغاء", null).setPositiveButton("حذف") { _, _ -> val ids = selectedIds.toList(); lifecycleScope.launch { AppDatabase.getInstance(this@MainActivity).accountDao().deleteByIds(ids); selectedIds.clear(); selectionMode = false; renderAccounts(); Toast.makeText(this@MainActivity, "تم حذف ${ids.size} حساب", Toast.LENGTH_SHORT).show() } }.show() }

    private fun requestExportAuthentication() { AlertDialog.Builder(this).setTitle("تأكيد التصدير").setMessage("اختر طريقة التحقق قبل إنشاء النسخة").setItems(arrayOf("البصمة", "رمز PIN للجوال")) { _, which -> if (which == 0) authenticateExportBiometric() else authenticateExportDevice() }.setNegativeButton("إلغاء", null).show() }
    private fun authenticateExportBiometric() { val prompt = androidx.biometric.BiometricPrompt(this, androidx.core.content.ContextCompat.getMainExecutor(this), object : androidx.biometric.BiometricPrompt.AuthenticationCallback() { override fun onAuthenticationSucceeded(r: androidx.biometric.BiometricPrompt.AuthenticationResult) { exportPicker.launch("your-account-backup.json") } }).also { it.authenticate(androidx.biometric.BiometricPrompt.PromptInfo.Builder().setTitle("تأكيد التصدير").setNegativeButtonText("إلغاء").build()) } }
    private fun authenticateExportDevice() { val prompt = androidx.biometric.BiometricPrompt(this, androidx.core.content.ContextCompat.getMainExecutor(this), object : androidx.biometric.BiometricPrompt.AuthenticationCallback() { override fun onAuthenticationSucceeded(r: androidx.biometric.BiometricPrompt.AuthenticationResult) { exportPicker.launch("your-account-backup.json") } }); prompt.authenticate(androidx.biometric.BiometricPrompt.PromptInfo.Builder().setTitle("تأكيد التصدير").setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL).build()) }

    override fun onUserLeaveHint() { super.onUserLeaveHint(); AuthSession.markBackgrounded() }
    override fun onResume() { super.onResume(); if (::binding.isInitialized && !AuthSession.isUnlocked && !redirectingToAuth) { redirectingToAuth = true; startActivity(Intent(this, AuthActivity::class.java)) } }
    private fun exportVault(uri: Uri) { lifecycleScope.launch { val json = withContext(Dispatchers.IO) { JSONArray().apply { allAccounts.forEach { a -> put(JSONObject().apply { put("siteName", a.siteName); put("displayName", a.displayName ?: ""); put("username", a.username ?: ""); put("password", a.encryptedPassword ?: ""); put("email", a.email ?: ""); put("phone", a.phone ?: ""); put("category", a.category) }) } }.toString(2) }; withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }; Toast.makeText(this@MainActivity, "تم تصدير الحسابات، احفظ الملف في مكان آمن", Toast.LENGTH_LONG).show() } }
}
