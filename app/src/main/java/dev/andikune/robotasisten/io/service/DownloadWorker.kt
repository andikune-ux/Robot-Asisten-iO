package dev.andikune.robotasisten.io.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.andikune.robotasisten.io.di.AppModule
import dev.andikune.robotasisten.io.util.LogHelper

/**
 * WorkManager worker executing offline model downloads with constraint handling.
 */
class DownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val modelId = inputData.getString("model_id") ?: return Result.failure()
        return try {
            val downloader = AppModule.provideModelDownloader(applicationContext)
            val model = downloader.availableModels.find { it.id == modelId } ?: return Result.failure()
            LogHelper.i("DownloadWorker", "Starting background model download: ${model.name}")
            var success = false
            downloader.startDownload(model) { isSuccess ->
                success = isSuccess
            }
            if (success) Result.success() else Result.retry()
        } catch (e: Exception) {
            LogHelper.e("DownloadWorker", "Failed background download for $modelId", e)
            Result.retry()
        }
    }
}
