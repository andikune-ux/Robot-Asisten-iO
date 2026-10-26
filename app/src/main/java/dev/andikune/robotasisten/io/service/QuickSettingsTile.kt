package dev.andikune.robotasisten.io.service

import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class QuickSettingsTile : TileService() {
    override fun onClick() {
        super.onClick()
        /* TODO: toggle overlay service */
    }
}
