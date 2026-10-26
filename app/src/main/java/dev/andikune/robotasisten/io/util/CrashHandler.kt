package dev.andikune.robotasisten.io.util

import android.content.Context

/**
 * Global uncaught exception handler to prevent hard crashes and record diagnostics.
 */
class CrashHandler private constructor(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        LogHelper.e("CrashHandler", "Uncaught exception on thread ${thread.name}", throwable)
        defaultHandler?.uncaughtException(thread, throwable)
    }

    companion object {
        fun init(context: Context) {
            val handler = CrashHandler(context.applicationContext)
            Thread.setDefaultUncaughtExceptionHandler(handler)
        }
    }
}
