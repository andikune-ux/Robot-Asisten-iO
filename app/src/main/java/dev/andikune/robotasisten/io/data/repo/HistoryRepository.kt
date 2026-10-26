package dev.andikune.robotasisten.io.data.repo

import androidx.room.withTransaction
import dev.andikune.robotasisten.io.data.db.AppDatabase
import dev.andikune.robotasisten.io.data.db.HistoryDao
import dev.andikune.robotasisten.io.data.db.HistoryEntity
import dev.andikune.robotasisten.io.data.db.StatsDao
import dev.andikune.robotasisten.io.data.db.StatsEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository for storing solved questions history and tracking telemetry/stats.
 */
class HistoryRepository(
    private val database: AppDatabase,
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
        val timestamp = System.currentTimeMillis()
        database.withTransaction {
            historyDao.insertHistory(
                HistoryEntity(
                    question = question,
                    answer = answer,
                    mode = mode,
                    questionType = questionType,
                    timestamp = timestamp
                )
            )
            statsDao.recordUsage(mode, isCacheHit, timestamp)
        }
    }

    suspend fun deleteById(id: Long) = historyDao.deleteById(id)

    suspend fun clearHistory() = historyDao.clearHistory()
}
