package dev.andikune.robotasisten.io.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.andikune.robotasisten.io.data.ai.OfflineModelSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Public scheduling facade for downloads; callers do not bind the job to a screen coroutine. */
object OfflineModelDownloadHelper {
    fun enqueue(context: Context, model: OfflineModelSpec) {
        val request = OneTimeWorkRequestBuilder<OfflineModelDownloadWorker>()
            .setInputData(workDataOf(OfflineModelDownloadWorker.KEY_MODEL_ID to model.id))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                30_000L,
                java.util.concurrent.TimeUnit.MILLISECONDS
            )
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            OfflineModelDownloadWorker.UNIQUE_WORK_PREFIX + model.id,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun observe(context: Context, model: OfflineModelSpec): Flow<WorkInfo?> =
        WorkManager.getInstance(context.applicationContext)
            .getWorkInfosForUniqueWorkFlow(OfflineModelDownloadWorker.UNIQUE_WORK_PREFIX + model.id)
            .map { it.firstOrNull() }

    fun cancel(context: Context, model: OfflineModelSpec) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(OfflineModelDownloadWorker.UNIQUE_WORK_PREFIX + model.id)
    }
}
