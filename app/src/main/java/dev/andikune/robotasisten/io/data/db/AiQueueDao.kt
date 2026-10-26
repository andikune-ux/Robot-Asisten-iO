package dev.andikune.robotasisten.io.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * Data Access Object for queued background AI tasks.
 */
@Dao
interface AiQueueDao {
    @Query("SELECT * FROM ai_queue WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingTasks(): List<AiQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(task: AiQueueEntity): Long

    @Update
    suspend fun updateTask(task: AiQueueEntity)

    @Delete
    suspend fun deleteTask(task: AiQueueEntity)

    @Query("DELETE FROM ai_queue WHERE status = 'COMPLETED'")
    suspend fun clearCompleted()
}
