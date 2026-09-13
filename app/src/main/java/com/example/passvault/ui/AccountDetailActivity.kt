package com.example.passvault.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.passvault.data.Account
import com.example.passvault.data.AppDatabase
import com.example.passvault.databinding.ActivityAccountDetailBinding
import com.example.passvault.util.CryptoManager
import kotlinx.coroutines.launch

class AccountDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountDetailBinding
    private var account: Account? = null
    private var passwordVisible = false
    private var plainPassword: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val id = intent.getLongExtra("account_id", -1)
        if (id == -1L) { finish(); return }

        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@AccountDetailActivity).accountDao()
            val acc = dao.getById(id)
            if (acc == null) { finish(); return@launch }
            account = acc
            bind(acc)
        }

        binding.btnTogglePassword.setOnClickListener { togglePassword() }
        binding.btnCopyPassword.setOnClickListener { copyPassword() }
        binding.btnDelete.setOnClickListener { deleteAccount() }
    }

    private fun bind(acc: Account) {
        binding.tvSiteName.text = acc.siteName
        binding.tvDisplayName.text = acc.displayName ?: "—"
        binding.tvUsername.text = acc.username ?: "—"
        binding.tvEmail.text = acc.email ?: "—"
        binding.tvPhone.text = acc.phone ?: "—"

        if (acc.encryptedPassword != null) {
            plainPassword = CryptoManager.decrypt(acc.encryptedPassword)
            binding.tvPassword.text = "••••••••"
            binding.btnTogglePassword.isEnabled = true
            binding.btnCopyPassword.isEnabled = true
        } else {
            binding.tvPassword.text = "—"
            binding.btnTogglePassword.isEnabled = false
            binding.btnCopyPassword.isEnabled = false
        }
    }

    private fun togglePassword() {
        passwordVisible = !passwordVisible
        binding.tvPassword.text = if (passwordVisible) plainPassword else "••••••••"
    }

    private fun copyPassword() {
        val pw = plainPassword ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("password", pw))
        Toast.makeText(this, "تم نسخ كلمة المرور", Toast.LENGTH_SHORT).show()
    }

    private fun deleteAccount() {
        val acc = account ?: return
        lifecycleScope.launch {
            AppDatabase.getInstance(this@AccountDetailActivity).accountDao().delete(acc)
            finish()
        }
    }
}
