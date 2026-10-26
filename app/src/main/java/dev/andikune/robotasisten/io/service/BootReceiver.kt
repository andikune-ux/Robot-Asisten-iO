package dev.andikune.robotasisten.io.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.andikune.robotasisten.io.util.LogHelper

/**
 * Receiver to restore background monitors on device reboot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            LogHelper.i("BootReceiver", "Device boot completed, R A . i O ready")
        }
    }
}
