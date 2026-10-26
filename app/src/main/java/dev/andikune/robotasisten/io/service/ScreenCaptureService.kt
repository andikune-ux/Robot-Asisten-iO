package dev.andikune.robotasisten.io.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dev.andikune.robotasisten.io.util.Constants
import dev.andikune.robotasisten.io.util.LogHelper

/**
 * Screen capture background service supporting visual frame capture and OCR analysis.
 */
class ScreenCaptureService : Service() {

    override fun onCreate() {
        super.onCreate()
        NotificationService.createNotificationChannel(this)
        val notification = NotificationService.buildForegroundNotification(this, "Visual capture service active")
        startForeground(Constants.NOTIFICATION_ID + 1, notification)
        LogHelper.i("ScreenCaptureService", "ScreenCaptureService started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        LogHelper.i("ScreenCaptureService", "ScreenCaptureService stopped")
    }
}
