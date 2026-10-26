package dev.andikune.robotasisten.io.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for usage statistics (total answered, per-mode counts, cache hits).
 */
@Entity(tableName = "stats")
data class StatsEntity(
    @PrimaryKey
    val id: Int = 1,
    val totalQuestions: Int = 0,
    val libraryCount: Int = 0,
    val offlineCount: Int = 0,
    val cloudCount: Int = 0,
    val cacheHits: Int = 0,
    val dailyQuotaUsed: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)
