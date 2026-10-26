package dev.andikune.robotasisten.io.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dev.andikune.robotasisten.io.util.LogHelper
import dev.andikune.robotasisten.io.util.ToastHelper

/** Capture is not implemented; this service must not imply that screen capture is active. */
class ScreenCaptureService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogHelper.e("ScreenCaptureService", "Screen capture requested but is not implemented")
        ToastHelper.showShort(this, "Screen capture belum tersedia; tidak ada layar yang direkam")
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
