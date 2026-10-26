package dev.andikune.robotasisten.io.data.repo

import dev.andikune.robotasisten.io.data.db.RuleDao
import dev.andikune.robotasisten.io.data.db.RuleEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository handling rule-based matching data for Mode Library.
 */
class RuleRepository(private val ruleDao: RuleDao) {
    val allRules: Flow<List<RuleEntity>> = ruleDao.getAllRules()

    suspend fun getRulesList(): List<RuleEntity> = ruleDao.getRulesList()

    suspend fun getRuleCount(): Int = ruleDao.getRuleCount()

    suspend fun insertRule(rule: RuleEntity): Long = ruleDao.insertRule(rule)

    suspend fun insertAll(rules: List<RuleEntity>) = ruleDao.insertAll(rules)

    suspend fun updateRule(rule: RuleEntity) = ruleDao.updateRule(rule)

    suspend fun deleteRule(rule: RuleEntity) = ruleDao.deleteRule(rule)

    suspend fun deleteRuleById(id: Long) = ruleDao.deleteRuleById(id)

    suspend fun clearAll() = ruleDao.clearRules()
}
