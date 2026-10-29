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
    private val QUESTION_MARKER_REGEX = "(\\?|\\b(apa|apakah|siapa|kapan|dimana|di mana|mengapa|bagaimana|berapa|manakah|pilih|tentukan|hitung|jelaskan|uraikan|sebutkan|what|which|who|whom|whose|when|where|why|how|calculate|solve|explain|describe|compare|define|identify|name|find|que|quien|quién|cuando|cuándo|donde|dónde|por que|por qué|como|cómo|cuanto|cuánto|calcula|resuelve|explica|quelle|quel|qui|quand|pourquoi|comment|combien|berechne|erklare|erkläre|warum|wann|wo|was|quem|quando|porque|quanto|calcule|o que|chi|quando|perché|come|quale)\\b)".toRegex(RegexOption.IGNORE_CASE)
    private val optionRegex = "^([A-Za-z]|[0-9]{1,2})[.)]\\s*(.+)$".toRegex()
    private val chromeLineRegex = "^(home|beranda|riwayat|pengaturan|settings?|menu|kembali|batal|tutup|kirim|salin|mulai|berhenti|relx\\s*ai|relx\\.io)$".toRegex(RegexOption.IGNORE_CASE)

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
        val focusedText = focusQuestionText(clean)
        val extractedOptions = if (nodeOptions.isNotEmpty()) nodeOptions.map(String::trim).filter(String::isNotEmpty) else extractTextOptions(focusedText)
        val type = when {
            extractedOptions.size >= 2 -> QuestionType.MULTIPLE_CHOICE
            focusedText.contains(BLANK_REGEX) -> QuestionType.FILL_IN_THE_BLANK
            focusedText.contains(CALC_REGEX) -> QuestionType.CALCULATION
            focusedText.contains(ESSAY_REGEX) || focusedText.length > 120 -> QuestionType.ESSAY
            hasInput -> QuestionType.FILL_IN_THE_BLANK
            else -> QuestionType.ESSAY
        }
        return DetectedQuestion(clean, type, focusedText, extractedOptions, hasInput)
    }

    private fun focusQuestionText(text: String): String {
        val lines = text.lineSequence().map(String::trim).filter(String::isNotEmpty)
            .filterNot { chromeLineRegex.matches(it) }.toList()
        if (lines.isEmpty()) return text
        val anchors = lines.indices.filter { index ->
            val line = lines[index]
            line.contains(CALC_REGEX) || line.contains(BLANK_REGEX) || line.contains(ESSAY_REGEX) ||
                line.contains(QUESTION_MARKER_REGEX)
        }
        val optionIndices = lines.indices.filter { optionRegex.matches(lines[it]) }
        if (anchors.isEmpty()) {
            return if (optionIndices.size >= 2) optionIndices.joinToString("\n") { lines[it] } else text
        }

        val selected = sortedSetOf<Int>()
        anchors.forEach { anchor ->
            if (anchor > 0 && lines[anchor - 1].length > 8 && !lines[anchor - 1].contains(QUESTION_MARKER_REGEX)) {
                selected.add(anchor - 1)
            }
            selected.add(anchor)
            optionIndices.filter { it > anchor && it <= anchor + MAX_OPTION_DISTANCE }.forEach(selected::add)
        }
        return selected.joinToString("\n") { lines[it] }.take(MAX_FOCUSED_CHARS).ifBlank { text }
    }

    private fun extractTextOptions(text: String): List<String> {
        val options = text.lineSequence().mapNotNull { line ->
            optionRegex.matchEntire(line.trim())?.groupValues?.get(2)?.trim()?.takeIf(String::isNotEmpty)
        }.toList()
        return options.takeIf { it.size >= 2 }.orEmpty()
    }

    private fun extractedOptionsCount(text: String): Int = extractTextOptions(text).size

    fun detectAll(text: String): List<DetectedQuestion> {
        val clean = text.trim()
        if (clean.isBlank()) return emptyList()

        val questionNumberRegex = "(?m)^(?:(?:Soal|Question|Pertanyaan|No\\.?)\\s*\\d+|\\d+)[.)]\\s+".toRegex(RegexOption.IGNORE_CASE)
        val matches = questionNumberRegex.findAll(clean).toList()

        if (matches.size >= 2) {
            val questions = mutableListOf<DetectedQuestion>()
            for (i in matches.indices) {
                val start = matches[i].range.first
                val end = if (i + 1 < matches.size) matches[i + 1].range.first else clean.length
                val chunk = clean.substring(start, end).trim()
                if (chunk.isNotEmpty()) {
                    questions.add(detect(chunk))
                }
            }
            if (questions.isNotEmpty()) return questions
        }

        val paragraphs = clean.split("\n\n+").map(String::trim).filter(String::isNotEmpty)
        val detectedParagraphs = paragraphs.filter { isLikelyQuestion(it) }
        if (detectedParagraphs.size >= 2) {
            return detectedParagraphs.map { detect(it) }
        }

        return listOf(detect(clean))
    }

    private const val MAX_OPTION_DISTANCE = 10
    private const val MAX_FOCUSED_CHARS = 8_000
}

