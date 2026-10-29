package dev.andikune.robotasisten.io.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.andikune.robotasisten.io.data.ai.RelxChatMessage
import dev.andikune.robotasisten.io.data.ai.RelxChatStore
import dev.andikune.robotasisten.io.di.AppModule
import dev.andikune.robotasisten.io.util.LogHelper

/** Completes one persisted chat request even when the chat screen is no longer visible. */
class RelxChatReplyWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val store = RelxChatStore(applicationContext)
        val request = store.pendingRequest() ?: return Result.success()

        try {
            val repository = AppModule.provideAiRepository(applicationContext)
            val reply = try {
                repository.chatReply(
                    mode = request.mode,
                    history = request.history,
                    userMessage = request.userMessage,
                    persona = request.persona,
                    memory = request.memory,
                    instructions = request.instructions,
                    reasoningDepth = request.reasoningDepth
                )
            } catch (t: Throwable) {
                LogHelper.e("RelxChatReplyWorker", "Error generating AI response", t)
                "Gagal menghasilkan respon: ${t.message ?: "Terjadi kesalahan internal"}. Silakan periksa kembali konfigurasi mode aktif."
            }

            val answer = reply?.takeIf { it.isNotBlank() }
                ?: "RELX AI belum menerima jawaban. Periksa model offline atau koneksi, API key, provider, dan model aktif; lalu coba balas ulang."

            store.saveMessages(
                request.conversationId,
                request.pendingMessages + RelxChatMessage(
                    role = RelxChatStore.ROLE_ASSISTANT,
                    content = answer
                )
            )
        } catch (t: Throwable) {
            LogHelper.e("RelxChatReplyWorker", "Fatal error in RelxChatReplyWorker", t)
            runCatching {
                store.saveMessages(
                    request.conversationId,
                    request.pendingMessages + RelxChatMessage(
                        role = RelxChatStore.ROLE_ASSISTANT,
                        content = "Terjadi gangguan saat memproses balasan: ${t.message ?: "Kesalahan sistem"}"
                    )
                )
            }
        } finally {
            store.clearPendingRequest()
        }

        return Result.success()
    }
}
