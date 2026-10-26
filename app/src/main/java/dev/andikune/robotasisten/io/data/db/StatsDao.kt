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

    @Update
    suspend fun updateStats(stats: StatsEntity)
}
