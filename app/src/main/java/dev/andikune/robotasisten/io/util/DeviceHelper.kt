package dev.andikune.robotasisten.io.util

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs

/**
 * Device diagnostic helper (RAM, free storage, Android version, manufacturer).
 */
object DeviceHelper {
    fun getDeviceInfo(context: Context): Map<String, String> {
        val stat = StatFs(Environment.getDataDirectory().path)
        val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
        val freeMb = freeBytes / (1024 * 1024)

        return mapOf(
            "Device" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "Android Version" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "Available Storage" to "$freeMb MB",
            "Arch" to Build.SUPPORTED_ABIS.joinToString(", ")
        )
    }

    fun hasSufficientStorageForOfflineModel(requiredMb: Long = 500): Boolean {
        val stat = StatFs(Environment.getDataDirectory().path)
        val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
        val freeMb = freeBytes / (1024 * 1024)
        return freeMb >= requiredMb
    }
}
