package dev.andikune.robotasisten.io.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder

class ScreenCaptureService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1002, buildNotification())
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val ch = "raio_capture"
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(ch) == null) {
            nm.createNotificationChannel(
                NotificationChannel(ch, "Screen Capture", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return Notification.Builder(this, ch)
            .setContentTitle("R A . i O")
            .setContentText("Screen capture aktif")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }
}
