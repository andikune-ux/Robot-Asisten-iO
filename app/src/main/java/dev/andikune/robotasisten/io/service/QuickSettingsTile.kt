package dev.andikune.robotasisten.io.service

import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

/** No overlay toggle is wired to this tile yet. */
@RequiresApi(Build.VERSION_CODES.N)
class QuickSettingsTile : TileService() {
    override fun onClick() {
        super.onClick()
        qsTile?.apply {
            label = "R A . i O (belum tersedia)"
            updateTile()
        }
    }
}
