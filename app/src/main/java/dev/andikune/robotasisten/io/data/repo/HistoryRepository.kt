package dev.andikune.robotasisten.io.data.repo

import dev.andikune.robotasisten.io.data.db.HistoryDao
import dev.andikune.robotasisten.io.data.db.HistoryEntity
import dev.andikune.robotasisten.io.data.db.StatsDao
import dev.andikune.robotasisten.io.data.db.StatsEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository for storing solved questions history and tracking telemetry/stats.
 */
class HistoryRepository(
    private val historyDao: HistoryDao,
    private val statsDao: StatsDao
) {
    val allHistory: Flow<List<HistoryEntity>> = historyDao.getAllHistory()
    val statsFlow: Flow<StatsEntity?> = statsDao.getStats()

    suspend fun getHistoryList(): List<HistoryEntity> = historyDao.getHistoryList()

    suspend fun recordAnswer(
        question: String,
        answer: String,
        mode: String,
        questionType: String,
        isCacheHit: Boolean = false
    ) {
        historyDao.insertHistory(
            HistoryEntity(
                question = question,
                answer = answer,
                mode = mode,
                questionType = questionType,
                timestamp = System.currentTimeMillis()
            )
        )

        val current = statsDao.getStatsDirect() ?: StatsEntity()
        val updated = current.copy(
            totalQuestions = current.totalQuestions + 1,
            libraryCount = if (mode.contains("LIBRARY", true)) current.libraryCount + 1 else current.libraryCount,
            offlineCount = if (mode.contains("OFFLINE", true)) current.offlineCount + 1 else current.offlineCount,
            cloudCount = if (mode.contains("CLOUD", true) || mode.contains("OPEN_KEY", true)) current.cloudCount + 1 else current.cloudCount,
            cacheHits = if (isCacheHit) current.cacheHits + 1 else current.cacheHits,
            dailyQuotaUsed = if (mode.contains("CLOUD", true)) current.dailyQuotaUsed + 1 else current.dailyQuotaUsed,
            lastUpdated = System.currentTimeMillis()
        )
        statsDao.insertOrUpdate(updated)
    }

    suspend fun deleteById(id: Long) = historyDao.deleteById(id)

    suspend fun clearHistory() = historyDao.clearHistory()
}
