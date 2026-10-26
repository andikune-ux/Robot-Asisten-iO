package dev.andikune.robotasisten.io.logic

import dev.andikune.robotasisten.io.data.ai.AiRepository
import dev.andikune.robotasisten.io.data.ai.AnswerTemplate
import dev.andikune.robotasisten.io.util.LogHelper
import java.util.Locale

data class ResolutionResult(
    val answer: String,
    val source: String,
    val explanation: String = "",
    val isCacheHit: Boolean = false
)

/** Resolves known local rules or the selected AI mode without inventing an answer. */
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
        val mode = activeMode.uppercase(Locale.ROOT)
        // Cloud responses depend on provider/model/key and must never be reused across configuration changes.
        val cached = if (mode == MODE_AI_OPEN_KEY) null else cacheMatcher.findInCache(question, mode)
        if (!cached.isNullOrBlank()) {
            LogHelper.i(TAG, "Resolved via cache")
            return ResolutionResult(answer = cached, source = "Cache", isCacheHit = true)
        }

        when (mode) {
            MODE_LIBRARY -> {
                ruleEngine.match(question)?.let { rule ->
                    cacheMatcher.saveToCache(question, rule.answer, MODE_LIBRARY)
                    return ResolutionResult(answer = rule.answer, source = "Library", explanation = rule.explanation)
                }
            }
            MODE_AI_OFFLINE -> {
                aiRepository.queryOffline(question)?.takeIf(String::isNotBlank)?.let { answer ->
                    cacheMatcher.saveToCache(question, answer, MODE_AI_OFFLINE)
                    return ResolutionResult(answer = answer, source = "AI Offline")
                }
            }
            MODE_AI_OPEN_KEY -> {
                aiRepository.queryCloud(question)?.takeIf(String::isNotBlank)?.let { answer ->
                    return ResolutionResult(answer = answer, source = "AI Cloud")
                }
            }
            else -> return unavailable("Mode \"$activeMode\" tidak dikenali. Pilih ulang mode di aplikasi.")
        }

        // A known library rule is a safe fallback for an AI mode; never invent other facts.
        ruleEngine.match(question)?.let { rule ->
            return ResolutionResult(answer = rule.answer, source = "Library fallback", explanation = rule.explanation)
        }

        return when (mode) {
            MODE_AI_OFFLINE -> unavailable(
                "Model AI Offline belum siap. Unduh model Qwen pada halaman Mode AI Offline, lalu coba lagi."
            )
            MODE_AI_OPEN_KEY -> unavailable(
                "AI Cloud tidak mengembalikan jawaban. Periksa koneksi, provider, API key, kuota, dan konfigurasi model."
            )
            else -> {
                val template = AnswerTemplate.generateTemplate(question, questionType)
                if (template.startsWith(UNANSWERED_PREFIX, ignoreCase = true)) {
                    unavailable("Tidak ada aturan Library yang cocok. Tambahkan materi/aturan di Library atau pilih mode AI.")
                } else {
                    ResolutionResult(answer = template, source = "Kalkulator lokal")
                }
            }
        }
    }

    private fun unavailable(message: String) = ResolutionResult(
        answer = message,
        source = SOURCE_UNAVAILABLE,
        explanation = "Tidak ada jawaban faktual yang dibuat; tindakan otomatis dinonaktifkan."
    )

    companion object {
        private const val TAG = "FallbackChain"
        private const val MODE_LIBRARY = "LIBRARY"
        private const val MODE_AI_OFFLINE = "AI_OFFLINE"
        private const val MODE_AI_OPEN_KEY = "AI_OPEN_KEY"
        private const val UNANSWERED_PREFIX = "Jawaban belum dapat ditentukan"
        const val SOURCE_UNAVAILABLE = "Tidak tersedia"
    }
}
