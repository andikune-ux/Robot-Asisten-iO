package dev.andikune.robotasisten.io.util

import android.content.Context
import com.google.gson.reflect.TypeToken
import dev.andikune.robotasisten.io.data.db.HistoryEntity
import dev.andikune.robotasisten.io.data.db.RuleEntity
import dev.andikune.robotasisten.io.data.repo.HistoryRepository
import dev.andikune.robotasisten.io.data.repo.RuleRepository
import java.io.File

/**
 * Handles JSON backup export and import for rules and history using Gson.
 */
class BackupHelper(
    private val ruleRepository: RuleRepository,
    private val historyRepository: HistoryRepository
) {

    suspend fun exportRulesToJson(context: Context): File {
        val rules = ruleRepository.getRulesList()
        val json = JsonHelper.gson.toJson(rules)

        val file = FileHelper.getExportFile(context, "raio_rules_backup_${System.currentTimeMillis()}.json")
        FileHelper.writeString(file, json)
        return file
    }

    suspend fun importRulesFromJson(json: String): Int {
        val type = object : TypeToken<List<RuleEntity>>() {}.type
        val list: List<RuleEntity>? = JsonHelper.gson.fromJson(json, type)
        if (list != null) {
            ruleRepository.insertAll(list)
            return list.size
        }
        return 0
    }

    suspend fun exportHistoryToJson(context: Context): File {
        val history = historyRepository.getHistoryList()
        val json = JsonHelper.gson.toJson(history)

        val file = FileHelper.getExportFile(context, "raio_history_backup_${System.currentTimeMillis()}.json")
        FileHelper.writeString(file, json)
        return file
    }
}
