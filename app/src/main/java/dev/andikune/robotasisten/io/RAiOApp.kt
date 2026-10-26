package dev.andikune.robotasisten.io

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.andikune.robotasisten.io.service.CleanupWorker
import dev.andikune.robotasisten.io.util.CrashHandler
import dev.andikune.robotasisten.io.util.LogHelper
import java.util.concurrent.TimeUnit

/**
 * Application entry point for R A . i O.
 * Initializes global handlers, DI singletons, and background maintenance workers.
 */
class RAiOApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashHandler.init(this)
        LogHelper.i("RAiOApp", "R A . i O initialized successfully")

        // Schedule daily cache cleanup worker
        try {
            val cleanupRequest = PeriodicWorkRequestBuilder<CleanupWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "raio_cleanup_worker",
                ExistingPeriodicWorkPolicy.KEEP,
                cleanupRequest
            )
        } catch (e: Exception) {
            LogHelper.w("RAiOApp", "WorkManager cleanup scheduling error: ${e.message}")
        }
    }

    companion object {
        lateinit var instance: RAiOApp
            private set
    }
}
