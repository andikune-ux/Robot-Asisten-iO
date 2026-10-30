package dev.andikune.robotasisten.io.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.andikune.robotasisten.io.data.ai.RelxPendingChatRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object RelxChatReplyHelper {
    private const val UNIQUE_WORK = "relx_ai_chat_reply"

    fun enqueue(context: Context, request: RelxPendingChatRequest) {
        val constraints = Constraints.Builder().apply {
            if (request.mode.trim().equals("AI_OPEN_KEY", ignoreCase = true)) {
                setRequiredNetworkType(NetworkType.CONNECTED)
            }
        }.build()
        val work = OneTimeWorkRequestBuilder<RelxChatReplyWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                15_000L,
                java.util.concurrent.TimeUnit.MILLISECONDS
            )
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_WORK,
            ExistingWorkPolicy.REPLACE,
            work
        )
    }

    fun observe(context: Context): Flow<WorkInfo?> =
        WorkManager.getInstance(context.applicationContext)
            .getWorkInfosForUniqueWorkFlow(UNIQUE_WORK)
            .map { it.firstOrNull() }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK)
    }
}
