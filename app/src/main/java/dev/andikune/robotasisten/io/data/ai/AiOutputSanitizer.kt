package dev.andikune.robotasisten.io.data.ai

/** Removes leaked model protocol markers while preserving ordinary prose and fenced code. */
object AiOutputSanitizer {
    private val protocolTokens = Regex("<\\|[^|\\r\\n]{1,80}\\|>")
    private val closedReasoningBlock = Regex("<think>.*?</think>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val assistantPrefix = Regex("(?im)^\\s*assistant\\s*[:：]\\s*")

    fun clean(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw
            .replace(closedReasoningBlock, "")
            .replace(protocolTokens, "")
            .replace(Regex("(?i)</?s>"), "")
            .replace(assistantPrefix, "")
            .replace(Regex("[ \\t]+\\n"), "\n")
            .replace("\r\n", "\n")
            .trim()
        return cleaned.takeIf { it.isNotBlank() }
    }
}
