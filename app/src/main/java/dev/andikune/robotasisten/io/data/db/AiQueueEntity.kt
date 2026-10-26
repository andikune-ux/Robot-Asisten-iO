package dev.andikune.robotasisten.io.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for retrying failed AI queries in the background.
 */
@Entity(tableName = "ai_queue")
data class AiQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val prompt: String,
    val provider: String,
    val model: String,
    val status: String = "PENDING", // PENDING, RETRYING, COMPLETED, FAILED
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0
)
