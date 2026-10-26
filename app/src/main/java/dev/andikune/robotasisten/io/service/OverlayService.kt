package dev.andikune.robotasisten.io.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder

class OverlayService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1001, buildNotification())
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val ch = "raio_overlay"
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(ch) == null) {
            nm.createNotificationChannel(
                NotificationChannel(ch, "Robot", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return Notification.Builder(this, ch)
            .setContentTitle("R A . i O")
            .setContentText("Robot aktif")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
    }
}
