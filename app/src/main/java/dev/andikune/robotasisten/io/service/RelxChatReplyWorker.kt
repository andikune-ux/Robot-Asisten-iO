package dev.andikune.robotasisten.io.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.andikune.robotasisten.io.data.ai.RelxChatMessage
import dev.andikune.robotasisten.io.data.ai.RelxChatStore
import dev.andikune.robotasisten.io.di.AppModule

/** Completes one persisted chat request even when the chat screen is no longer visible. */
class RelxChatReplyWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val store = RelxChatStore(applicationContext)
        val request = store.pendingRequest() ?: return Result.success()
        val repository = AppModule.provideAiRepository(applicationContext)
        val reply = runCatching {
            repository.chatReply(
                mode = request.mode,
                history = request.history,
                userMessage = request.userMessage,
                persona = request.persona,
                memory = request.memory,
                instructions = request.instructions,
                reasoningDepth = request.reasoningDepth
            )
        }.getOrNull()
        val answer = reply?.takeIf { it.isNotBlank() }
            ?: "RELX AI belum menerima jawaban. Periksa model offline atau koneksi, API key, provider, dan model aktif; lalu coba balas ulang."
        val current = store.currentConversation()
        if (current?.id == request.conversationId) {
            store.saveMessages(request.conversationId, request.pendingMessages + RelxChatMessage(
                role = RelxChatStore.ROLE_ASSISTANT,
                content = answer
            ))
        } else {
            val selected = store.conversations().firstOrNull { it.id == request.conversationId }
            if (selected != null) {
                store.saveMessages(request.conversationId, request.pendingMessages + RelxChatMessage(
                    role = RelxChatStore.ROLE_ASSISTANT,
                    content = answer
                ))
            }
        }
        store.clearPendingRequest()
        return Result.success()
    }
}
