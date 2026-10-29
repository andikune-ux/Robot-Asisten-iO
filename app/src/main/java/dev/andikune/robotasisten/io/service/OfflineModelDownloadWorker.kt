package dev.andikune.robotasisten.io.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dev.andikune.robotasisten.io.R
import dev.andikune.robotasisten.io.di.AppModule
import dev.andikune.robotasisten.io.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Persists model downloads beyond the Compose screen and survives app UI destruction. */
class OfflineModelDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val modelId = inputData.getString(KEY_MODEL_ID) ?: return Result.failure()
        val downloader = AppModule.provideModelDownloader(applicationContext)
        val model = downloader.availableModels.firstOrNull { it.id == modelId } ?: return Result.failure()
        createChannel()
        promoteToForeground(createForegroundInfo(model.name, 0, "Memulai unduhan..."))

        val monitorJob = CoroutineScope(Dispatchers.Default).launch {
            downloader.downloadProgressInfo.collect { map ->
                val info = map[model.id]
                if (info != null) {
                    val statusText = if (info.isVerifying) {
                        "Memverifikasi integritas hash SHA-256 (100%)..."
                    } else {
                        "${info.percent}% · ${info.speedText}"
                    }
                    runCatching {
                        promoteToForeground(createForegroundInfo(model.name, info.percent, statusText))
                    }
                }
            }
        }

        var succeeded = false
        try {
            downloader.startDownload(model) { result -> succeeded = result }
        } finally {
            monitorJob.cancel()
        }

        if (succeeded) AppModule.provideSettingsRepository(applicationContext).setSelectedOfflineModel(model.id)
        return if (succeeded) Result.success() else retryOrFailure()
    }

    private fun retryOrFailure(): Result =
        if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()

    private suspend fun promoteToForeground(info: ForegroundInfo) {
        setForegroundAsync(info).get()
    }

    private fun createForegroundInfo(modelName: String, progress: Int, detailText: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_robot)
            .setContentTitle("Unduhan Model AI: $modelName")
            .setContentText(detailText.ifBlank { "Mengunduh model offline..." })
            .setProgress(100, progress.coerceIn(0, 100), progress == 0 && detailText.isBlank())
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
