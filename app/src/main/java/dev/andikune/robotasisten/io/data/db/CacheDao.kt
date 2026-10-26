package dev.andikune.robotasisten.io.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * Data Access Object for query response caching.
 */
@Dao
interface CacheDao {
    @Query("SELECT * FROM cache WHERE queryHash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): CacheEntity?

    @Query("SELECT * FROM cache ORDER BY hitCount DESC, timestamp DESC")
    suspend fun getAllCache(): List<CacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(cache: CacheEntity): Long

    @Update
    suspend fun updateCache(cache: CacheEntity)

    @Query("DELETE FROM cache WHERE timestamp < :expiryTime")
    suspend fun deleteExpired(expiryTime: Long): Int

    @Query("DELETE FROM cache")
    suspend fun clearCache()

    @Query("SELECT COUNT(*) FROM cache")
    suspend fun getCacheCount(): Int
}
