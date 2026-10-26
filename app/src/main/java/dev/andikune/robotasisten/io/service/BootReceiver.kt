package dev.andikune.robotasisten.io.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Boot restoration is intentionally not implemented. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) = Unit
}
