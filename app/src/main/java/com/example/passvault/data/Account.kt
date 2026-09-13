package com.example.passvault.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * حساب واحد محفوظ (موقع / تطبيق / شركة).
 * كل الحقول اختيارية إلا اسم الموقع، وأي حقل يترك فاضي ما يتخزن (null).
 * كلمة المرور تتخزن مشفّرة (عبر CryptoManager) وليس كنص صريح.
 */
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siteName: String,           // اسم الموقع/التطبيق/الشركة
    val displayName: String?,       // الاسم
    val username: String?,          // اليوزر نيم
    val encryptedPassword: String?, // الباسورد (مشفّر)
    val email: String?,             // الإيميل
    val phone: String?,             // رقم الجوال
    val source: String = "manual",  // manual أو imported_csv
    val category: String = "عام"    // شخصي، عمل، مالي، أو تصنيف مخصص
)
