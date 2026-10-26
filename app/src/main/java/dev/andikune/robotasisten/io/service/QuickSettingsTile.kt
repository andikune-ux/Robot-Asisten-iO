package dev.andikune.robotasisten.io.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import dev.andikune.robotasisten.io.util.PermissionHelper

/**
 * Quick Settings Tile to toggle R A . i O Floating Robot from the notification shade.
 */
@RequiresApi(Build.VERSION_CODES.N)
class QuickSettingsTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val isRunning = OverlayService.isRunning.value
        if (!isRunning) {
            if (PermissionHelper.hasOverlayPermission(this)) {
                val intent = Intent(this, OverlayService::class.java)
                startForegroundService(intent)
            } else {
                PermissionHelper.openOverlaySettings(this)
            }
        } else {
            val intent = Intent(this, OverlayService::class.java)
            stopService(intent)
        }
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isRunning = OverlayService.isRunning.value
        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (isRunning) "R A . i O (Aktif)" else "R A . i O"
        tile.updateTile()
    }
}
