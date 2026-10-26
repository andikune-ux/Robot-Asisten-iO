package dev.andikune.robotasisten.io.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for assistant telemetry and statistics.
 */
@Dao
interface StatsDao {
    @Query("SELECT * FROM stats WHERE id = 1 LIMIT 1")
    fun getStats(): Flow<StatsEntity?>

    @Query("SELECT * FROM stats WHERE id = 1 LIMIT 1")
    suspend fun getStatsDirect(): StatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(stats: StatsEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun ensureStatsRow(stats: StatsEntity)

    @Query("""
        UPDATE stats SET
            totalQuestions = totalQuestions + 1,
            libraryCount = libraryCount + CASE WHEN UPPER(:mode) = 'LIBRARY' THEN 1 ELSE 0 END,
            offlineCount = offlineCount + CASE WHEN UPPER(:mode) IN ('OFFLINE', 'AI_OFFLINE') THEN 1 ELSE 0 END,
            cloudCount = cloudCount + CASE WHEN UPPER(:mode) IN ('CLOUD', 'OPEN_KEY', 'AI_OPEN_KEY') THEN 1 ELSE 0 END,
            cacheHits = cacheHits + CASE WHEN :isCacheHit THEN 1 ELSE 0 END,
            dailyQuotaUsed = dailyQuotaUsed + CASE WHEN UPPER(:mode) IN ('CLOUD', 'OPEN_KEY', 'AI_OPEN_KEY') THEN 1 ELSE 0 END,
            lastUpdated = :timestamp
        WHERE id = 1
    """)
    suspend fun incrementUsage(mode: String, isCacheHit: Boolean, timestamp: Long)

    @androidx.room.Transaction
    suspend fun recordUsage(mode: String, isCacheHit: Boolean, timestamp: Long) {
        ensureStatsRow(StatsEntity())
        incrementUsage(mode, isCacheHit, timestamp)
    }

    @Update
    suspend fun updateStats(stats: StatsEntity)
}
