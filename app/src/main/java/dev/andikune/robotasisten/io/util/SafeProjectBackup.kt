package dev.andikune.robotasisten.io.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import dev.andikune.robotasisten.io.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SafeProjectBackup {
    /** Android 10+: create the app-owned text document at the requested shared-storage path. */
    fun export(context: Context): Uri {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Android versi ini memerlukan pemilihan folder"
        }
        val resolver = context.contentResolver
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val displayName = "RELX-iO_BackupAman_$timestamp.txt"
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$BACKUP_PATH/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val output = resolver.insert(collection, values)
            ?: error("Android tidak dapat membuat berkas di Penyimpanan internal/$BACKUP_PATH")
        try {
            resolver.openOutputStream(output, "w")?.use { stream ->
                stream.write(buildReport(context).toByteArray(Charsets.UTF_8))
                stream.flush()
            } ?: error("Tidak dapat membuka berkas Backup Aman untuk ditulis")
            val publishValues = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            check(resolver.update(output, publishValues, null, null) == 1) {
                "Berkas backup dibuat tetapi gagal dipublikasikan"
            }
            return output
        } catch (error: Exception) {
            runCatching { resolver.delete(output, null, null) }
            throw error
        }
    }

    /** SAF fallback for devices that restrict direct MediaStore folders, including Android 10+. */
    fun export(context: Context, selectedTree: Uri): Uri {
        val root = DocumentFile.fromTreeUri(context, selectedTree)
            ?: error("Folder yang dipilih tidak dapat dibuka")
        check(root.canWrite()) { "Folder yang dipilih tidak mengizinkan penulisan" }

        val backupFolder = when {
            root.name.equals("Backup aman", ignoreCase = true) -> root
            root.name.equals("Relxio", ignoreCase = true) -> getOrCreateDirectory(root, "Backup aman")
            else -> getOrCreateDirectory(getOrCreateDirectory(root, "Relxio"), "Backup aman")
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val file = backupFolder.createFile("text/plain", "RELX-iO_BackupAman_$timestamp.txt")
            ?: error("Tidak dapat membuat berkas backup")
        context.contentResolver.openOutputStream(file.uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use {
            it.write(buildReport(context))
        } ?: error("Tidak dapat membuka output stream untuk berkas backup")
        return file.uri
    }

    private fun getOrCreateDirectory(parent: DocumentFile, name: String): DocumentFile =
        parent.findFile(name)?.let {
            if (it.isDirectory) it else error("$name sudah ada tetapi bukan folder")
        } ?: parent.createDirectory(name) ?: error("Tidak dapat membuat folder $name")

    private fun buildReport(context: Context): String {
        val handoff = context.assets.open("relx_project_handoff.txt")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val runtimeInfo = buildString {
            appendLine("\n\nINFORMASI RUNTIME SAAT BACKUP")
            appendLine("==============================")
            appendLine("Waktu ekspor: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())}")
            appendLine("Versi aplikasi terpasang: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Application ID: ${BuildConfig.APPLICATION_ID}")
            appendLine("Perangkat: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Lokasi tujuan: Penyimpanan internal/$BACKUP_PATH")
            appendLine("\nBerkas ini adalah dokumentasi proyek. Tidak ada database, konfigurasi rahasia, API key, keystore, atau data pribadi aplikasi yang diekspor.")
        }
        return handoff + runtimeInfo
    }

    private const val BACKUP_PATH = "Relxio/Backup aman"
}
