package dev.andikune.robotasisten.io.util

import android.content.Context
import android.os.Build
import android.util.Log
import dev.andikune.robotasisten.io.BuildConfig
import kotlinx.coroutines.CoroutineExceptionHandler
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Global and Coroutine Exception Handler for RAILX AI.
 * Persists fatal crashes and logs immediately to Logcat with tag RAILX_CRASH_DEBUG.
 */
class CrashHandler private constructor(context: Context) : Thread.UncaughtExceptionHandler {
    private val appContext = context.applicationContext
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // 1. Explicitly print to Logcat with required tag
        Log.e(TAG_DEBUG, "FATAL CRASH di thread ${thread.name} (id=${thread.id}): ${throwable.message}", throwable)
        
        // 2. Persist synchronously to disk
        runCatching { recordCrashInternal(appContext, "UncaughtException: ${thread.name}", throwable) }
        
        // 3. Delegate to default Android handler
        runCatching { defaultHandler?.uncaughtException(thread, throwable) }
    }

    companion object {
        const val TAG_DEBUG = "RAILX_CRASH_DEBUG"
        private const val FILE_NAME = "last_crash_report.txt"

        @Volatile
        private var instance: CrashHandler? = null

        @Synchronized
        fun init(context: Context) {
            val appCtx = context.applicationContext
            if (instance == null) {
                val handler = CrashHandler(appCtx)
                instance = handler
                Thread.setDefaultUncaughtExceptionHandler(handler)
                Log.i(TAG_DEBUG, "Global UncaughtExceptionHandler terpasang untuk RAILX AI")
            }
        }

        fun createCoroutineExceptionHandler(processName: String, context: Context? = null): CoroutineExceptionHandler {
            return CoroutineExceptionHandler { _, throwable ->
                Log.e(TAG_DEBUG, "Error di [$processName]: ${throwable.message}", throwable)
                if (context != null) {
                    recordCrash(context, processName, throwable)
                }
            }
        }

        fun recordCrash(context: Context, processName: String, throwable: Throwable) {
            Log.e(TAG_DEBUG, "Error di [$processName]: ${throwable.message}", throwable)
            recordCrashInternal(context.applicationContext, processName, throwable)
        }

        private fun recordCrashInternal(context: Context, processName: String, throwable: Throwable) {
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())
            val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
            val report = buildString {
                appendLine("=== RELX.iO / RAILX AI — LAPORAN FORCE CLOSE ===")
                appendLine("Waktu: $stamp")
                appendLine("Proses: $processName")
                appendLine("Versi: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("Package: ${BuildConfig.APPLICATION_ID}")
                appendLine("Perangkat: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
                appendLine("Penyebab Utama: ${throwable.javaClass.name}: ${throwable.message ?: "Tanpa pesan"}")
                appendLine()
                appendLine("=== STACK TRACE & LOKASI KODE ===")
                append(trace)
            }
            val target = crashFile(context)
            runCatching {
                FileOutputStream(target).use { fos ->
                    fos.write(report.toByteArray(Charsets.UTF_8))
                    fos.flush()
                    fos.fd.sync()
                }
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
