package dev.andikune.robotasisten.io.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for caching answered questions to save AI quota and accelerate response time.
 */
@Entity(tableName = "cache")
data class CacheEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val queryHash: String,
    val queryNormalized: String,
    val answer: String,
    val mode: String,
    val timestamp: Long = System.currentTimeMillis(),
    val hitCount: Int = 1
)
