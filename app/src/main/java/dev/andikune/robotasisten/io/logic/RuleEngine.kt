package dev.andikune.robotasisten.io.logic

import dev.andikune.robotasisten.io.data.db.RuleEntity
import dev.andikune.robotasisten.io.data.repo.RuleRepository

/** Evaluates questions against local rules without requiring internet or AI quota. */
class RuleEngine(private val ruleRepository: RuleRepository) {
    suspend fun match(question: String): RuleEntity? {
        val rules = ruleRepository.getRulesList()
        val normalizedQuery = normalize(question)
        if (normalizedQuery.isBlank()) return null

        for (rule in rules) {
            if (rule.isRegex) {
                try {
                    if (rule.questionPattern.isNotBlank() && rule.questionPattern.toRegex(RegexOption.IGNORE_CASE).containsMatchIn(question)) return rule
                } catch (_: Exception) { }
            } else {
                val pattern = normalize(rule.questionPattern)
                if (pattern.isNotBlank() && containsPhrase(normalizedQuery, pattern)) return rule
            }
        }

        var bestRule: RuleEntity? = null
        var highestScore = 0
        val queryWords = normalizedQuery.split(' ').filter { it.length > 2 }.toSet()
        for (rule in rules) {
            val patternWords = normalize(rule.questionPattern).split(' ').filter { it.length > 2 }.toSet()
            val commonCount = queryWords.intersect(patternWords).size
            if (commonCount > highestScore && commonCount >= 2) {
                highestScore = commonCount
                bestRule = rule
            }
        }
        return bestRule
    }

    private fun containsPhrase(text: String, phrase: String): Boolean =
        Regex("(^| )${Regex.escape(phrase)}( |$)").containsMatchIn(text)

    private fun normalize(text: String): String = text.lowercase()
        .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
        .replace(Regex("\\s+"), " ").trim()
}
