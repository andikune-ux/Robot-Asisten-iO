package dev.andikune.robotasisten.io.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility for formatting timestamps and measuring intervals.
 */
object TimeHelper {
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun formatDateTime(timestamp: Long): String {
        return dateFormat.format(Date(timestamp))
    }

    fun formatTime(timestamp: Long): String {
        return timeFormat.format(Date(timestamp))
    }
}
