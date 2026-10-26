package dev.andikune.robotasisten.io.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.andikune.robotasisten.io.MainActivity
import dev.andikune.robotasisten.io.R
import dev.andikune.robotasisten.io.util.Constants

/**
 * Handles notification channels and persistent foreground notification for the overlay assistant.
 */
object NotificationService {

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                Constants.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows status of R A . i O Floating Robot Assistant"
                setShowBadge(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun buildForegroundNotification(context: Context, statusText: String): Notification {
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            appIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(context, OverlayService::class.java).apply {
            action = Constants.ACTION_STOP_OVERLAY
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val analyzeIntent = Intent(context, OverlayService::class.java).apply {
            action = Constants.ACTION_TRIGGER_ANALYZE
        }
        val analyzePendingIntent = PendingIntent.getService(
            context,
            2,
            analyzeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(Constants.APP_NAME)
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_robot)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_robot, "🔍 Analisa", analyzePendingIntent)
            .addAction(R.drawable.ic_bubble, "❌ Matikan", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
