package dev.andikune.robotasisten.io.data.ai

import dev.andikune.robotasisten.io.logic.QuestionDetector
import dev.andikune.robotasisten.io.logic.RuleEngine

/**
 * Mode 1: Local Rule-Based Engine. 100% offline, instantaneous response, zero token usage.
 */
class LibraryAi(private val ruleEngine: RuleEngine) {

    suspend fun answer(question: String): String? {
        val rule = ruleEngine.match(question)
        if (rule != null) {
            return rule.answer
        }
        val detected = QuestionDetector.detect(question)
        return AnswerTemplate.generateTemplate(question, detected.type)
    }
}
