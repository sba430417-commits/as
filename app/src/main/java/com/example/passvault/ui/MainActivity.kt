package com.example.passvault.ui

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.MotionEvent
import android.os.Handler
import android.os.Looper
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
    private var categorySelectionMode = false
    private val selectedCategories = linkedSetOf<String>()
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
        binding.btnSelectAllCategories.setOnClickListener {
            val removable = CategoryManager.get(this).filter { it != "الكل" && it != "الرئيسي" }
            if (selectedCategories.size == removable.size) selectedCategories.clear() else selectedCategories.addAll(removable)
            updateCategorySelectionUi(); renderCategoryButtons()
        }
        binding.btnDeleteCategories.setOnClickListener { confirmDeleteCategories() }
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
        currentCategory = null; selectionMode = false; selectedIds.clear(); categorySelectionMode = false; selectedCategories.clear()
        binding.categoryHomeHeader.visibility = View.VISIBLE; binding.categorySelectionHeader.visibility = View.GONE; binding.categoryList.visibility = View.VISIBLE; binding.normalHeader.visibility = View.GONE; binding.selectionHeader.visibility = View.GONE; binding.recyclerView.visibility = View.GONE; binding.emptyState.visibility = View.GONE; binding.fabAdd.visibility = View.GONE
        renderCategoryButtons()
    }

    private fun renderCategoryButtons() {
        binding.categoryList.removeAllViews()
        CategoryManager.get(this).filter { it != "الكل" }.forEach { category ->
            val count = if (category == "الرئيسي") allAccounts.count(::isMainCategory) else allAccounts.count { it.category == category }
            val button = MaterialButton(this).apply {
                text = "$category\n$count حساب"; isAllCaps = false; gravity = android.view.Gravity.CENTER; minHeight = 112
                setOnClickListener { if (categorySelectionMode) toggleCategorySelection(category) else openCategory(category) }
                if (category != "الرئيسي") {
                    val handler = Handler(Looper.getMainLooper()); var triggered = false
                    setOnTouchListener { _, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> { triggered = false; handler.postDelayed({ triggered = true; categorySelectionMode = true; toggleCategorySelection(category) }, 1000); false }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { handler.removeCallbacksAndMessages(null); if (triggered) true else false }
                            else -> false
                        }
                    }
                }
            }
            if (selectedCategories.contains(category)) button.setStrokeColorResource(R.color.primary)
            binding.categoryList.addView(button, android.widget.GridLayout.LayoutParams().apply { width = 0; height = 112; columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f); setMargins(6, 6, 6, 6) })
        }
        updateCategorySelectionUi()
    }

    private fun toggleCategorySelection(category: String) { if (category == "الرئيسي") return; if (!selectedCategories.add(category)) selectedCategories.remove(category); categorySelectionMode = selectedCategories.isNotEmpty(); updateCategorySelectionUi(); renderCategoryButtons() }
    private fun updateCategorySelectionUi() { binding.categorySelectionHeader.visibility = if (categorySelectionMode) View.VISIBLE else View.GONE; binding.tvSelectedCategories.text = "تم تحديد ${selectedCategories.size}"; binding.btnSelectAllCategories.text = if (selectedCategories.isNotEmpty()) "إلغاء الكل" else "تحديد الكل" }
    private fun confirmDeleteCategories() {
        if (selectedCategories.isEmpty()) return
        AlertDialog.Builder(this).setTitle("حذف الأقسام").setMessage("سيتم نقل الحسابات إلى القسم الرئيسي. هل تريد حذف ${selectedCategories.size} قسم؟").setNegativeButton("إلغاء", null).setPositiveButton("حذف") { _, _ ->
            val deleted = selectedCategories.toList(); lifecycleScope.launch { AppDatabase.getInstance(this@MainActivity).accountDao().moveToCategory(allAccounts.filter { it.category in deleted }.map { it.id }, "الرئيسي"); deleted.forEach { CategoryManager.remove(this@MainActivity, it) }; selectedCategories.clear(); categorySelectionMode = false; showCategoryHome(); Toast.makeText(this@MainActivity, "تم حذف الأقسام ونقل حساباتها للرئيسي", Toast.LENGTH_SHORT).show() }
        }.show()
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
