package dev.andikune.robotasisten.io.logic

import dev.andikune.robotasisten.io.data.db.AiQueueDao
import dev.andikune.robotasisten.io.data.db.AiQueueEntity

/** Handles offline or transient failures by queuing tasks for background execution. */
class AiCallQueue(private val aiQueueDao: AiQueueDao) {
    suspend fun enqueueFailedCall(prompt: String, provider: String, model: String) {
        aiQueueDao.enqueue(AiQueueEntity(prompt = prompt, provider = provider, model = model, status = "PENDING"))
    }

    suspend fun getPendingTasks(): List<AiQueueEntity> = aiQueueDao.getPendingTasks()

    /** A task remains pending unless its worker explicitly confirms a successful call. */
    suspend fun markCompleted(task: AiQueueEntity, succeeded: Boolean = false) {
        aiQueueDao.updateTask(task.copy(status = if (succeeded) "COMPLETED" else "PENDING"))
    }
}
