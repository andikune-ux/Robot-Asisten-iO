package dev.andikune.robotasisten.io.util

import android.util.Log

/**
 * Standardized logging utility with in-memory log buffer for the Debug Screen.
 */
object LogHelper {
    private const val TAG_PREFIX = "RAiO_"
    private val memoryLogs = mutableListOf<String>()
    private const val MAX_LOGS = 200

    @Synchronized
    fun d(tag: String, message: String) {
        val entry = "[DEBUG] [$tag] $message"
        Log.d("$TAG_PREFIX$tag", message)
        appendLog(entry)
    }

    @Synchronized
    fun i(tag: String, message: String) {
        val entry = "[INFO] [$tag] $message"
        Log.i("$TAG_PREFIX$tag", message)
        appendLog(entry)
    }

    @Synchronized
    fun w(tag: String, message: String) {
        val entry = "[WARN] [$tag] $message"
        Log.w("$TAG_PREFIX$tag", message)
        appendLog(entry)
    }

    @Synchronized
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val entry = "[ERROR] [$tag] $message: ${throwable?.message}"
        Log.e("$TAG_PREFIX$tag", message, throwable)
        appendLog(entry)
    }

    private fun appendLog(entry: String) {
        if (memoryLogs.size >= MAX_LOGS) {
            memoryLogs.removeAt(0)
        }
        memoryLogs.add("${System.currentTimeMillis()} $entry")
    }

    @Synchronized
    fun getLogs(): List<String> = memoryLogs.toList()

    @Synchronized
    fun clearLogs() {
        memoryLogs.clear()
    }
}
