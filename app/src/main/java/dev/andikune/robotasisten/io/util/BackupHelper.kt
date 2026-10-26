package dev.andikune.robotasisten.io.util

import android.content.Context
import com.squareup.moshi.Types
import dev.andikune.robotasisten.io.data.db.HistoryEntity
import dev.andikune.robotasisten.io.data.db.RuleEntity
import dev.andikune.robotasisten.io.data.repo.HistoryRepository
import dev.andikune.robotasisten.io.data.repo.RuleRepository
import java.io.File

/**
 * Handles JSON backup export and import for rules and history.
 */
class BackupHelper(
    private val ruleRepository: RuleRepository,
    private val historyRepository: HistoryRepository
) {

    suspend fun exportRulesToJson(context: Context): File {
        val rules = ruleRepository.getRulesList()
        val type = Types.newParameterizedType(List::class.java, RuleEntity::class.java)
        val adapter = JsonHelper.moshi.adapter<List<RuleEntity>>(type)
        val json = adapter.toJson(rules)

        val file = FileHelper.getExportFile(context, "raio_rules_backup_${System.currentTimeMillis()}.json")
        FileHelper.writeString(file, json)
        return file
    }

    suspend fun importRulesFromJson(json: String): Int {
        val type = Types.newParameterizedType(List::class.java, RuleEntity::class.java)
        val adapter = JsonHelper.moshi.adapter<List<RuleEntity>>(type)
        val list = adapter.fromJson(json) ?: return 0
        ruleRepository.insertAll(list)
        return list.size
    }

    suspend fun exportHistoryToJson(context: Context): File {
        val history = historyRepository.getHistoryList()
        val type = Types.newParameterizedType(List::class.java, HistoryEntity::class.java)
        val adapter = JsonHelper.moshi.adapter<List<HistoryEntity>>(type)
        val json = adapter.toJson(history)

        val file = FileHelper.getExportFile(context, "raio_history_backup_${System.currentTimeMillis()}.json")
        FileHelper.writeString(file, json)
        return file
    }
}
