package dev.andikune.robotasisten.io.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for recording solved study questions, chosen answers, and resolution method.
 */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val question: String,
    val answer: String,
    val mode: String,
    val questionType: String = "MULTIPLE_CHOICE",
    val timestamp: Long = System.currentTimeMillis(),
    val wasConfirmed: Boolean = true
)
