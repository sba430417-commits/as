package com.example.passvault.util

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** يميّز إزالة التطبيق من شاشة التطبيقات الأخيرة عن مجرد وضعه بالخلفية. */
class TaskRemovalService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_NOT_STICKY

    override fun onTaskRemoved(rootIntent: Intent?) {
        AuthSession.markTaskRemoved()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }
}
