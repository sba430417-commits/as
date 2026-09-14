package com.example.passvault.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.passvault.data.Account
import com.example.passvault.data.AppDatabase
import com.example.passvault.databinding.ActivityAddAccountBinding
import com.example.passvault.util.encryptedForStorage
import kotlinx.coroutines.launch

class AddAccountActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddAccountBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.btnSave.setOnClickListener { saveAccount() }
    }

    private fun String.orNullIfBlank(): String? = this.trim().ifBlank { null }

    private fun saveAccount() {
        val siteName = binding.etSiteName.text.toString().trim()
        if (siteName.isBlank()) {
            Toast.makeText(this, "اكتب اسم أو رابط الشركة / التطبيق", Toast.LENGTH_SHORT).show()
            return
        }

        val username = binding.etUsername.text.toString().orNullIfBlank()
        val passwordRaw = binding.etPassword.text.toString().orNullIfBlank()
        val email = binding.etEmail.text.toString().orNullIfBlank()
        val phone = binding.etPhone.text.toString().orNullIfBlank()

        if (passwordRaw == null) {
            Toast.makeText(this, "كلمة المرور مطلوبة", Toast.LENGTH_SHORT).show()
            return
        }
        if (email == null && phone == null) {
            Toast.makeText(this, "أدخل البريد الإلكتروني أو رقم الجوال على الأقل", Toast.LENGTH_SHORT).show()
            return
        }

        val account = Account(
            siteName = siteName,
            displayName = null,
            username = username,
            encryptedPassword = passwordRaw,
            email = email,
            phone = phone,
            source = "manual",
            category = "عام"
        )

        lifecycleScope.launch {
            AppDatabase.getInstance(this@AddAccountActivity).accountDao().insert(account.encryptedForStorage())
            Toast.makeText(this@AddAccountActivity, "تم إنشاء الخانة", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
