package dev.andikune.robotasisten.io.util

/**
 * Strips sensitive data, personal info, or exam headers before sending text to AI.
 */
object SanitizerHelper {
    private val EMAIL_REGEX = "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}".toRegex()
    private val PHONE_REGEX = "\\+?[0-9]{10,13}".toRegex()

    fun sanitizeQuery(text: String): String {
        return text.trim()
            .replace(EMAIL_REGEX, "[EMAIL_REDACTED]")
            .replace(PHONE_REGEX, "[PHONE_REDACTED]")
            .replace(Regex("\\s+"), " ")
    }

    fun containsExamKeyword(text: String): Boolean {
        val lower = text.lowercase()
        return Constants.EXAM_KEYWORDS.any { lower.contains(it) }
    }
}
