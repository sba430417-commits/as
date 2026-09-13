package com.example.passvault.util

/** جلسة مؤقتة: تبقى مفتوحة 30 دقيقة في الخلفية ثم تطلب التحقق من جديد. */
object AuthSession {
    private const val SESSION_TIMEOUT_MS = 30 * 60 * 1000L
    private var unlockedAt: Long = 0L
    private var backgroundAt: Long? = null

    var isUnlocked: Boolean
        get() = unlockedAt > 0L &&
            (backgroundAt == null || System.currentTimeMillis() - backgroundAt!! < SESSION_TIMEOUT_MS)
        set(value) {
            if (value) {
                unlockedAt = System.currentTimeMillis()
                backgroundAt = null
            } else {
                unlockedAt = 0L
                backgroundAt = null
            }
        }

    fun markBackgrounded() {
        if (unlockedAt > 0L) backgroundAt = System.currentTimeMillis()
    }

    fun markTaskRemoved() {
        isUnlocked = false
    }
}
