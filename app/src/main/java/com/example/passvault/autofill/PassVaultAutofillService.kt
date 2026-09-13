package com.example.passvault.autofill

import android.app.assist.AssistStructure
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillContext
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.example.passvault.R
import com.example.passvault.data.Account
import com.example.passvault.data.AppDatabase
import com.example.passvault.util.CryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * خدمة أندرويد الرسمية للحفظ والتعبئة التلقائية.
 * لا تعمل إلا بعد أن يفعّلها المستخدم من إعدادات Autofill في النظام.
 */
class PassVaultAutofillService : AutofillService() {

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: android.os.CancellationSignal,
        callback: FillCallback
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onSuccess(null)
            return
        }
        val fields = findFields(structure)
        if (fields.usernameId == null && fields.passwordId == null) {
            callback.onSuccess(null)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val accounts = AppDatabase.getInstance(applicationContext).accountDao().getAllNow()
            val response = FillResponse.Builder()
            accounts.take(20).forEach { account ->
                val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                    setTextViewText(android.R.id.text1, account.siteName)
                }
                val dataset = Dataset.Builder(presentation)
                fields.usernameId?.let { id ->
                    account.username?.let { dataset.setValue(id, AutofillValue.forText(it), presentation) }
                }
                fields.passwordId?.let { id ->
                    account.encryptedPassword?.let {
                        dataset.setValue(id, AutofillValue.forText(CryptoManager.decrypt(it)), presentation)
                    }
                }
                response.addDataset(dataset.build())
            }
            callback.onSuccess(response.build())
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onFailure("لا توجد بيانات للحفظ")
            return
        }
        val fields = findFields(structure)
        val username = fields.usernameValue
        val password = fields.passwordValue
        if (username.isNullOrBlank() && password.isNullOrBlank()) {
            callback.onFailure("لم يتم العثور على اسم مستخدم أو كلمة مرور")
            return
        }

        val siteName = structure.activityComponent?.packageName?.substringAfterLast('.') ?: "حساب محفوظ"
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getInstance(applicationContext).accountDao().insert(
                Account(
                    siteName = siteName,
                    displayName = null,
                    username = username,
                    encryptedPassword = password?.let { CryptoManager.encrypt(it) },
                    email = null,
                    phone = null,
                    source = "autofill",
                    category = "عام"
                )
            )
            callback.onSuccess()
        }
    }

    private data class Fields(
        var usernameId: AutofillId? = null,
        var passwordId: AutofillId? = null,
        var usernameValue: String? = null,
        var passwordValue: String? = null
    )

    private fun findFields(structure: AssistStructure): Fields {
        val fields = Fields()
        for (i in 0 until structure.windowNodeCount) {
            walk(structure.getWindowNodeAt(i).rootViewNode, fields)
        }
        return fields
    }

    private fun walk(node: AssistStructure.ViewNode, fields: Fields) {
        val hints = node.autofillHints?.map { it.lowercase() } ?: emptyList()
        val hintString = hints.joinToString(" ")
        val isPassword = hintString.contains("password") || node.inputType and 0x80 != 0
        val isUsername = hintString.contains("username") || hintString.contains("email")
        if (isPassword) {
            fields.passwordId = node.autofillId
            fields.passwordValue = node.autofillValue?.textValue?.toString()
        } else if (isUsername) {
            fields.usernameId = node.autofillId
            fields.usernameValue = node.autofillValue?.textValue?.toString()
        }
        for (i in 0 until node.childCount) walk(node.getChildAt(i), fields)
    }
}
