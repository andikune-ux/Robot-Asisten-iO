package dev.andikune.robotasisten.io.logic

/**
 * Question types identified by QuestionDetector.
 */
enum class QuestionType {
    MULTIPLE_CHOICE,
    FILL_IN_THE_BLANK,
    CALCULATION,
    ESSAY
}

/**
 * Detected question information package.
 */
data class DetectedQuestion(
    val rawText: String,
    val type: QuestionType,
    val questionText: String,
    val options: List<String> = emptyList(),
    val hasInputField: Boolean = false,
    val confidence: Float = 0.9f
)

/**
 * Classifies screen content into question formats and extracts options and input prompts.
 */
object QuestionDetector {

    private val CALC_REGEX = "(\\d+[\\+\\-\\*/×÷]\\d+|berapa hasil dari|hitunglah|berapakah nilai|rumus|luas|keliling)".toRegex(RegexOption.IGNORE_CASE)
    private val BLANK_REGEX = "(__{2,}|\\.{3,}|\\[\\s*\\]|titik-titik|isian di bawah)".toRegex(RegexOption.IGNORE_CASE)
    private val ESSAY_REGEX = "(jelaskan|uraikan|mengapa|bagaimana|apa alasan|analisislah|sebutkan dan jelaskan)".toRegex(RegexOption.IGNORE_CASE)

    fun detect(text: String, nodeOptions: List<String> = emptyList(), hasInput: Boolean = false): DetectedQuestion {
        val clean = text.trim()

        val type = when {
            nodeOptions.size >= 2 || hasOptionIndicators(clean) -> QuestionType.MULTIPLE_CHOICE
            clean.contains(BLANK_REGEX) -> QuestionType.FILL_IN_THE_BLANK
            clean.contains(CALC_REGEX) -> QuestionType.CALCULATION
            clean.contains(ESSAY_REGEX) || clean.length > 120 -> QuestionType.ESSAY
            hasInput -> QuestionType.FILL_IN_THE_BLANK
            else -> QuestionType.MULTIPLE_CHOICE
        }

        val extractedOptions = if (nodeOptions.isNotEmpty()) {
            nodeOptions
        } else {
            extractTextOptions(clean)
        }

        return DetectedQuestion(
            rawText = clean,
            type = type,
            questionText = clean,
            options = extractedOptions,
            hasInputField = hasInput
        )
    }

    private fun hasOptionIndicators(text: String): Boolean {
        return (text.contains("A.") || text.contains("A)") || text.contains("a.")) &&
               (text.contains("B.") || text.contains("B)") || text.contains("b."))
    }

    private fun extractTextOptions(text: String): List<String> {
        val lines = text.split("\n")
        val options = mutableListOf<String>()
        val optionRegex = "^[A-Ea-e][\\.\\)]\\s*(.+)".toRegex()

        for (line in lines) {
            val match = optionRegex.find(line.trim())
            if (match != null) {
                options.add(match.groupValues[1].trim())
            }
        }
        return options
    }
}
