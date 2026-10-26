package dev.andikune.robotasisten.io.data.ai

import dev.andikune.robotasisten.io.logic.QuestionType
import java.util.Locale

/**
 * Fallback generator for answering using local logical sentence templates.
 */
object AnswerTemplate {

    fun generateTemplate(question: String, type: QuestionType): String {
        val mathMatch = "(\\d+)\\s*([\\+\\-\\*/×÷])\\s*(\\d+)".toRegex().find(question)
        if (mathMatch != null) {
            val a = mathMatch.groupValues[1].toDoubleOrNull() ?: return unableToAnswer()
            val op = mathMatch.groupValues[2]
            val b = mathMatch.groupValues[3].toDoubleOrNull() ?: return unableToAnswer()
            if ((op == "/" || op == "÷") && b == 0.0) return "Tidak dapat menghitung: pembagian dengan nol tidak terdefinisi."
            val res = when (op) {
                "+" -> a + b
                "-" -> a - b
                "*", "×" -> a * b
                "/", "÷" -> a / b
                else -> return unableToAnswer()
            }
            return if (res % 1.0 == 0.0) res.toLong().toString() else String.format(Locale.ROOT, "%.2f", res)
        }

        return when (type) {
            QuestionType.CALCULATION,
            QuestionType.FILL_IN_THE_BLANK,
            QuestionType.ESSAY,
            QuestionType.MULTIPLE_CHOICE -> unableToAnswer()
        }
    }

    private fun unableToAnswer() = "Jawaban belum dapat ditentukan. Tambahkan aturan jawaban atau gunakan provider AI yang didukung."
}
