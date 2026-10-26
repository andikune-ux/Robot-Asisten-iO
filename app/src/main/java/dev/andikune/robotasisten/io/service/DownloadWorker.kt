package dev.andikune.robotasisten.io.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DownloadWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = Result.failure()
}
