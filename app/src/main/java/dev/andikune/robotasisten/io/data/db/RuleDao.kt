package dev.andikune.robotasisten.io.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for rules in Mode Library.
 */
@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY id ASC")
    fun getAllRules(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules")
    suspend fun getRulesList(): List<RuleEntity>

    @Query("SELECT COUNT(*) FROM rules")
    suspend fun getRuleCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: RuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<RuleEntity>)

    @Update
    suspend fun updateRule(rule: RuleEntity)

    @Delete
    suspend fun deleteRule(rule: RuleEntity)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun deleteRuleById(id: Long)

    @Query("DELETE FROM rules")
    suspend fun clearRules()
}
