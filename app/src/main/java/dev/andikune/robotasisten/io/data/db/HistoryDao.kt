package dev.andikune.robotasisten.io.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for query history.
 */
@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    suspend fun getHistoryList(): List<HistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: HistoryEntity): Long

    @Delete
    suspend fun deleteHistory(history: HistoryEntity)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    @Query("SELECT COUNT(*) FROM history")
    suspend fun getCount(): Int
}
