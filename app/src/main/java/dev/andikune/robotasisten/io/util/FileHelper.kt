package dev.andikune.robotasisten.io.util

import android.content.Context
import java.io.File

/**
 * File I/O utility for local rules, offline AI models storage, and exports.
 */
object FileHelper {
    fun getModelsDir(context: Context): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getExportFile(context: Context, filename: String): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, filename)
    }

    fun writeString(file: File, content: String) {
        file.writeText(content, Charsets.UTF_8)
    }

    fun readString(file: File): String {
        return file.readText(Charsets.UTF_8)
    }
}
