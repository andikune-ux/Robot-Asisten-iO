package dev.andikune.robotasisten.io.logic

enum class QuestionType { MULTIPLE_CHOICE, FILL_IN_THE_BLANK, CALCULATION, ESSAY }

data class DetectedQuestion(
    val rawText: String,
    val type: QuestionType,
    val questionText: String,
    val options: List<String> = emptyList(),
    val hasInputField: Boolean = false,
    val confidence: Float = 0.9f
)

object QuestionDetector {
    private val CALC_REGEX = "(\\d+\\s*[+\\-*/×÷]\\s*\\d+|berapa hasil dari|hitunglah|berapakah nilai|rumus|luas|keliling)".toRegex(RegexOption.IGNORE_CASE)
    private val BLANK_REGEX = "(__{2,}|\\.{3,}|\\[\\s*\\]|titik-titik|isian di bawah)".toRegex(RegexOption.IGNORE_CASE)
    private val ESSAY_REGEX = "(jelaskan|uraikan|mengapa|bagaimana|apa alasan|analisislah|sebutkan dan jelaskan)".toRegex(RegexOption.IGNORE_CASE)
    private val QUESTION_MARKER_REGEX = "(\\?|\\b(apa|apakah|siapa|kapan|dimana|di mana|mengapa|bagaimana|berapa|manakah|pilih|tentukan|hitung|jelaskan|uraikan|sebutkan)\\b)".toRegex(RegexOption.IGNORE_CASE)
    private val optionRegex = "^[A-Ea-e][.)]\\s*(.+)$".toRegex()

    fun isLikelyQuestion(text: String): Boolean {
        val clean = text.trim()
        if (clean.isEmpty()) return false
        return extractedOptionsCount(clean) >= 2 ||
            clean.contains(CALC_REGEX) ||
            clean.contains(BLANK_REGEX) ||
            clean.contains(ESSAY_REGEX) ||
            clean.contains(QUESTION_MARKER_REGEX)
    }

    fun detect(text: String, nodeOptions: List<String> = emptyList(), hasInput: Boolean = false): DetectedQuestion {
        val clean = text.trim()
        val extractedOptions = if (nodeOptions.isNotEmpty()) nodeOptions.map(String::trim).filter(String::isNotEmpty) else extractTextOptions(clean)
        val type = when {
            extractedOptions.size >= 2 -> QuestionType.MULTIPLE_CHOICE
            clean.contains(BLANK_REGEX) -> QuestionType.FILL_IN_THE_BLANK
            clean.contains(CALC_REGEX) -> QuestionType.CALCULATION
            clean.contains(ESSAY_REGEX) || clean.length > 120 -> QuestionType.ESSAY
            hasInput -> QuestionType.FILL_IN_THE_BLANK
            else -> QuestionType.ESSAY
        }
        return DetectedQuestion(clean, type, clean, extractedOptions, hasInput)
    }

    private fun extractTextOptions(text: String): List<String> {
        val options = text.lineSequence().mapNotNull { line ->
            optionRegex.matchEntire(line.trim())?.groupValues?.get(1)?.trim()?.takeIf(String::isNotEmpty)
        }.toList()
        return options.takeIf { it.size >= 2 }.orEmpty()
    }

    private fun extractedOptionsCount(text: String): Int = extractTextOptions(text).size
}
