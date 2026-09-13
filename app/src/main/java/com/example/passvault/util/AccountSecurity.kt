package com.example.passvault.util

import com.example.passvault.data.Account

fun Account.encryptedForStorage(): Account = if (CryptoManager.isEncrypted(siteName)) this else copy(
    siteName = CryptoManager.encrypt(siteName),
    displayName = displayName?.let(CryptoManager::encrypt),
    username = username?.let(CryptoManager::encrypt),
    encryptedPassword = encryptedPassword?.let(CryptoManager::encrypt),
    email = email?.let(CryptoManager::encrypt),
    phone = phone?.let(CryptoManager::encrypt),
    category = CryptoManager.encrypt(category)
)

fun Account.decryptedForUi(): Account = copy(
    siteName = CryptoManager.decrypt(siteName),
    displayName = displayName?.let(CryptoManager::decrypt),
    username = username?.let(CryptoManager::decrypt),
    encryptedPassword = encryptedPassword?.let(CryptoManager::decrypt),
    email = email?.let(CryptoManager::decrypt),
    phone = phone?.let(CryptoManager::decrypt),
    category = CryptoManager.decrypt(category).ifBlank { "عام" }
)

fun String?.plainValue(): String? = this?.let(CryptoManager::decrypt)
fun String?.plainValueOrDash(): String = plainValue().orEmpty().ifBlank { "—" }
fun String?.encryptedValue(): String? = this?.trim()?.ifBlank { null }?.let(CryptoManager::encrypt)
fun Account.plainPassword(): String? = encryptedPassword?.let(CryptoManager::decrypt)
fun Account.plainSiteName(): String = CryptoManager.decrypt(siteName)
fun Account.plainUsername(): String? = username.plainValue()
fun Account.plainEmail(): String? = email.plainValue()
fun Account.plainDisplayName(): String? = displayName.plainValue()
fun Account.plainPhone(): String? = phone.plainValue()
fun Account.plainCategory(): String = CryptoManager.decrypt(category).ifBlank { "عام" }
