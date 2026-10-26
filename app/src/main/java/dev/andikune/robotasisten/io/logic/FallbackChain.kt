package dev.andikune.robotasisten.io.logic

import dev.andikune.robotasisten.io.data.ai.AiRepository
import dev.andikune.robotasisten.io.data.ai.AnswerTemplate
import dev.andikune.robotasisten.io.util.LogHelper

/**
 * Result returned by the resolution pipeline.
 */
data class ResolutionResult(
    val answer: String,
    val source: String, // 📚 Library, 🧠 Offline, ☁️ Cloud, 💾 Cache, 📝 Template
    val explanation: String = "",
    val isCacheHit: Boolean = false
)

/**
 * Executes tiered fallback strategy across Cache -> Mode Engine -> Fallback Engine -> Template.
 */
class FallbackChain(
    private val cacheMatcher: CacheMatcher,
    private val ruleEngine: RuleEngine,
    private val aiRepository: AiRepository
) {

    suspend fun resolve(
        question: String,
        activeMode: String,
        questionType: QuestionType
    ): ResolutionResult {
        // Cloud provider/model/key are not part of the current cache key, so do not
        // reuse a cloud answer after the user's cloud configuration changes.
        val cached = if (activeMode == "AI_OPEN_KEY") null else cacheMatcher.findInCache(question, activeMode)
        if (!cached.isNullOrBlank()) {
            LogHelper.i("FallbackChain", "Resolved via Cache")
            return ResolutionResult(answer = cached, source = "💾 Cache", isCacheHit = true)
        }

        // Step 2: Try primary requested mode
        when (activeMode) {
            "LIBRARY" -> {
                val rule = ruleEngine.match(question)
                if (rule != null) {
                    cacheMatcher.saveToCache(question, rule.answer, "LIBRARY")
                    return ResolutionResult(answer = rule.answer, source = "📚 Library", explanation = rule.explanation)
                }
            }
            "AI_OFFLINE" -> {
                val offlineAns = aiRepository.queryOffline(question)
                if (!offlineAns.isNullOrBlank()) {
                    cacheMatcher.saveToCache(question, offlineAns, "AI_OFFLINE")
                    return ResolutionResult(answer = offlineAns, source = "🧠 Offline")
                }
            }
            "AI_OPEN_KEY" -> {
                val cloudAns = aiRepository.queryCloud(question)
                if (!cloudAns.isNullOrBlank()) {
                    return ResolutionResult(answer = cloudAns, source = "☁️ Cloud")
                }
            }
        }

        // Step 3: Fallback to Library Rule Engine
        val fallbackRule = ruleEngine.match(question)
        if (fallbackRule != null) {
            return ResolutionResult(answer = fallbackRule.answer, source = "📚 Library Fallback", explanation = fallbackRule.explanation)
        }

        // Step 4: Fallback to Template Engine
        val templateAnswer = AnswerTemplate.generateTemplate(question, questionType)
        return ResolutionResult(answer = templateAnswer, source = "📝 Template")
    }
}
