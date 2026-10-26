package dev.andikune.robotasisten.io.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.andikune.robotasisten.io.data.db.AppDatabase
import dev.andikune.robotasisten.io.di.AppModule
import dev.andikune.robotasisten.io.util.LogHelper

/**
 * Background worker retrying deferred AI calls when network connectivity is restored.
 */
class AiRetryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val queue = db.aiQueueDao().getPendingTasks()
            val aiRepo = AppModule.provideAiRepository(applicationContext)

            for (task in queue) {
                val res = aiRepo.queryCloud(task.prompt)
                if (res != null) {
                    db.aiQueueDao().updateTask(task.copy(status = "COMPLETED"))
                    LogHelper.i("AiRetryWorker", "Successfully processed queued task ${task.id}")
                }
            }
            Result.success()
        } catch (e: Exception) {
            LogHelper.e("AiRetryWorker", "Retry queue execution failed", e)
            Result.retry()
        }
    }
}
