package dev.andikune.robotasisten.io.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.andikune.robotasisten.io.data.db.AppDatabase
import dev.andikune.robotasisten.io.util.LogHelper

/**
 * Background worker to purge cache older than 7 days.
 */
class CleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
            val deletedCount = db.cacheDao().deleteExpired(sevenDaysAgo)
            LogHelper.i("CleanupWorker", "Cleaned up $deletedCount expired cache records")
            Result.success()
        } catch (e: Exception) {
            LogHelper.e("CleanupWorker", "Cleanup failed", e)
            Result.retry()
        }
    }
}
