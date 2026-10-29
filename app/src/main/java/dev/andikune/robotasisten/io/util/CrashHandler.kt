package dev.andikune.robotasisten.io.util

import android.content.Context
import android.os.Build
import dev.andikune.robotasisten.io.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Persists the most recent fatal exception so the next launch can show and copy it. */
class CrashHandler private constructor(context: Context) : Thread.UncaughtExceptionHandler {
    private val appContext = context.applicationContext
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        runCatching { saveCrash(thread, throwable) }
        runCatching { defaultHandler?.uncaughtException(thread, throwable) }
    }

    private fun saveCrash(thread: Thread, throwable: Throwable) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())
        val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val report = buildString {
            appendLine("=== RELX.iO / RAILX AI — LAPORAN FORCE CLOSE ===")
            appendLine("Waktu: $stamp")
            appendLine("Versi: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Package: ${BuildConfig.APPLICATION_ID}")
            appendLine("Perangkat: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
            appendLine("Thread Penyebab: ${thread.name} (id=${thread.id})")
            appendLine("Penyebab Utama: ${throwable.javaClass.name}: ${throwable.message ?: "Tanpa pesan"}")
            appendLine()
            appendLine("=== STACK TRACE & LOKASI KODE ===")
            append(trace)
        }
        val target = crashFile(appContext)
        runCatching {
            FileOutputStream(target).use { fos ->
                fos.write(report.toByteArray(Charsets.UTF_8))
                fos.flush()
                fos.fd.sync()
            }
        }
    }

    companion object {
        private const val FILE_NAME = "last_crash_report.txt"

        @Synchronized
        fun init(context: Context) {
            val current = Thread.getDefaultUncaughtExceptionHandler()
            if (current !is CrashHandler) {
                Thread.setDefaultUncaughtExceptionHandler(CrashHandler(context))
            }
        }

        fun read(context: Context): String? = runCatching {
            crashFile(context.applicationContext).takeIf(File::exists)?.readText(Charsets.UTF_8)
        }.getOrNull()?.takeIf(String::isNotBlank)

        fun clear(context: Context) {
            runCatching { crashFile(context.applicationContext).delete() }
        }

        private fun crashFile(context: Context) = File(context.filesDir, FILE_NAME)
    }
}
