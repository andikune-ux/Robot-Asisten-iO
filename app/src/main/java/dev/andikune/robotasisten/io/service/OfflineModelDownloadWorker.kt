package dev.andikune.robotasisten.io.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dev.andikune.robotasisten.io.R
import dev.andikune.robotasisten.io.data.ai.ModelDownloader
import dev.andikune.robotasisten.io.di.AppModule
import dev.andikune.robotasisten.io.util.Constants

/** Persists model downloads beyond the Compose screen and survives app UI destruction. */
class OfflineModelDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val modelId = inputData.getString(KEY_MODEL_ID) ?: return Result.failure()
        val downloader = ModelDownloader(applicationContext)
        val model = downloader.availableModels.firstOrNull { it.id == modelId } ?: return Result.failure()
        createChannel()
        promoteToForeground(createForegroundInfo(model.name, 0))
        var succeeded = false
        downloader.startDownload(model) { result -> succeeded = result }
        if (succeeded) AppModule.provideSettingsRepository(applicationContext).setSelectedOfflineModel(model.id)
        return if (succeeded) Result.success() else retryOrFailure()
    }

    private fun retryOrFailure(): Result =
        if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()

    private suspend fun promoteToForeground(info: ForegroundInfo) {
        setForegroundAsync(info).get()
    }

    private fun createForegroundInfo(modelName: String, progress: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_robot)
            .setContentTitle(Constants.APP_NAME)
            .setContentText("Mengunduh model offline: $modelName")
            .setProgress(100, progress.coerceIn(0, 100), progress == 0)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Unduhan model AI offline", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        const val KEY_MODEL_ID = "offline_model_id"
        const val UNIQUE_WORK_PREFIX = "offline_model_download_"
        private const val CHANNEL_ID = "offline_model_downloads"
        private const val NOTIFICATION_ID = 2407
        private const val MAX_RETRIES = 3
    }
}
