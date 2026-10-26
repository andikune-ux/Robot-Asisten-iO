package dev.andikune.robotasisten.io.data.ai

import dev.andikune.robotasisten.io.logic.QuestionType
import dev.andikune.robotasisten.io.util.Constants

/**
 * Builds optimized prompts for multiple choice, calculation, fill in the blank, and essay questions.
 */
object PromptBuilder {

    fun buildPrompt(question: String, type: QuestionType, options: List<String> = emptyList()): String {
        return when (type) {
            QuestionType.MULTIPLE_CHOICE -> {
                val optionsText = if (options.isNotEmpty()) {
                    "\nPilihan:\n" + options.mapIndexed { idx, opt -> "${('A' + idx)}. $opt" }.joinToString("\n")
                } else ""
                "Pilihlah satu jawaban yang paling tepat untuk soal pilihan ganda berikut. Tuliskan HANYA huruf atau pilihan jawabannya secara ringkas tanpa basa-basi.\nSoal: $question$optionsText"
            }
            QuestionType.CALCULATION -> {
                "Selesaikan perhitungan matematika/sains berikut secara tepat. Berikan hasil akhirnya langsung, jika perlu sertakan 1 baris cara singkat.\nSoal: $question"
            }
            QuestionType.FILL_IN_THE_BLANK -> {
                "Lengkapilah kalimat atau bagian rumpang pada soal berikut secara tepat dan singkat.\nSoal: $question"
            }
            QuestionType.ESSAY -> {
                "${Constants.SYSTEM_PROMPT_ESSAY}$question"
            }
        }
    }
}
