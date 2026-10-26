package dev.andikune.robotasisten.io.logic

import dev.andikune.robotasisten.io.data.db.RuleEntity
import dev.andikune.robotasisten.io.data.repo.RuleRepository

/**
 * Evaluates questions against local rules without requiring an internet connection or AI quota.
 */
class RuleEngine(private val ruleRepository: RuleRepository) {

    suspend fun match(question: String): RuleEntity? {
        val rules = ruleRepository.getRulesList()
        val normalizedQuery = normalize(question)

        // 1. Direct contains or regex match
        for (rule in rules) {
            if (rule.isRegex) {
                try {
                    val regex = rule.questionPattern.toRegex(RegexOption.IGNORE_CASE)
                    if (regex.containsMatchIn(question)) {
                        return rule
                    }
                } catch (_: Exception) {}
            } else {
                val normalizedPattern = normalize(rule.questionPattern)
                if (normalizedQuery.contains(normalizedPattern) || normalizedPattern.contains(normalizedQuery)) {
                    return rule
                }
            }
        }

        // 2. Keyword overlap score
        var bestRule: RuleEntity? = null
        var highestScore = 0
        val queryWords = normalizedQuery.split(" ").filter { it.length > 2 }

        for (rule in rules) {
            val patternWords = normalize(rule.questionPattern).split(" ").filter { it.length > 2 }
            val commonCount = queryWords.intersect(patternWords.toSet()).size
            if (commonCount > highestScore && commonCount >= 2) {
                highestScore = commonCount
                bestRule = rule
            }
        }

        return bestRule
    }

    private fun normalize(text: String): String {
        return text.lowercase().replace(Regex("[^a-z0-9 ]"), " ").trim()
    }
}
