package dev.andikune.robotasisten.io.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * Utility for copying text to and reading from system clipboard.
 */
object ClipboardHelper {
    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

    fun getClipboardText(context: Context): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val item = clipboard.primaryClip?.getItemAt(0)
        return item?.text?.toString()
    }
}
