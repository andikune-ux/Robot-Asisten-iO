package dev.andikune.robotasisten.io.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import dev.andikune.robotasisten.io.MainActivity
import dev.andikune.robotasisten.io.navigation.Routes
import dev.andikune.robotasisten.io.util.Constants
import dev.andikune.robotasisten.io.util.PermissionHelper

@RequiresApi(Build.VERSION_CODES.N)
class QuickSettingsTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        refreshState()
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return
        if (OverlayService.isRunning.value) {
            stopService(Intent(this, OverlayService::class.java).setAction(Constants.ACTION_STOP_OVERLAY))
            tile.state = Tile.STATE_INACTIVE
        } else {
            val preferences = getSharedPreferences("raio_settings_prefs", MODE_PRIVATE)
            val mode = preferences.getString("active_mode", "").orEmpty()
            val permissionsReady = PermissionHelper.hasOverlayPermission(this) &&
                PermissionHelper.isAccessibilityServiceEnabled(this, MyAccessibilityService::class.java)
            if (!permissionsReady || mode !in setOf("LIBRARY", "AI_OFFLINE", "AI_OPEN_KEY")) {
                openApp(Routes.PERMISSIONS)
            } else {
                val intent = Intent(this, OverlayService::class.java).putExtra("mode", mode)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            }
        }
        refreshState()
    }

    private fun refreshState() {
        qsTile?.apply {
            label = Constants.APP_NAME
            state = if (OverlayService.isRunning.value) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    private fun openApp(route: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(Constants.EXTRA_NAVIGATION_ROUTE, route)
        }
        if (Build.VERSION.SDK_INT >= 34) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                56,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        fun requestRefresh(context: android.content.Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runCatching {
                    TileService.requestListeningState(context, ComponentName(context, QuickSettingsTile::class.java))
                }
            }
        }
    }
}
