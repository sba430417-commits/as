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
            val error = "أدخل البريد الإلكتروني أو رقم الجوال على الأقل"
            binding.emailLayout.error = error
            binding.phoneLayout.error = error
            return
        }

        var invalidContact = false
        if (email != null && !EMAIL_REGEX.matches(email)) {
            binding.emailLayout.error = "اكتب بريدًا صحيحًا مثل name@gmail.com"
            invalidContact = true
        } else {
            binding.emailLayout.error = null
        }
        if (phone != null && !PHONE_REGEX.matches(phone)) {
            binding.phoneLayout.error = "اكتب رقم جوال صحيحًا من 7 إلى 15 رقمًا فقط"
            invalidContact = true
        } else {
            binding.phoneLayout.error = null
        }
        if (invalidContact) return

        binding.emailLayout.error = null
        binding.phoneLayout.error = null

        val account = Account(
            siteName = siteName,
            displayName = null,
            username = username,
            encryptedPassword = passwordRaw,
            email = email,
            phone = phone,
            source = "manual",
            category = intent.getStringExtra("category") ?: "الرئيسي"
        )

        lifecycleScope.launch {
            AppDatabase.getInstance(this@AddAccountActivity).accountDao().insert(account.encryptedForStorage())
            Toast.makeText(this@AddAccountActivity, "تم إنشاء الخانة", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    companion object {
        private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")
        private val PHONE_REGEX = Regex("^\\d{7,15}$")
    }
}
