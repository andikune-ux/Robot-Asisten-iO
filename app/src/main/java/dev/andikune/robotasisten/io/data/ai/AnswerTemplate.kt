package dev.andikune.robotasisten.io.data.ai

import dev.andikune.robotasisten.io.logic.QuestionType

/**
 * Fallback generator for answering using local logical sentence templates.
 */
object AnswerTemplate {

    fun generateTemplate(question: String, type: QuestionType): String {
        val lower = question.lowercase()

        // 1. Check basic arithmetic operations
        val mathMatch = "(\\d+)\\s*([\\+\\-\\*/×÷])\\s*(\\d+)".toRegex().find(question)
        if (mathMatch != null) {
            val a = mathMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            val op = mathMatch.groupValues[2]
            val b = mathMatch.groupValues[3].toDoubleOrNull() ?: 0.0
            val res = when (op) {
                "+", "tambah" -> a + b
                "-", "kurang" -> a - b
                "*", "×", "kali" -> a * b
                "/", "÷", "bagi" -> if (b != 0.0) a / b else 0.0
                else -> 0.0
            }
            return if (res % 1.0 == 0.0) res.toLong().toString() else "%.2f".format(res)
        }

        return when (type) {
            QuestionType.CALCULATION -> "Gunakan rumus standar untuk menghitung nilai variabel pada soal tersebut."
            QuestionType.FILL_IN_THE_BLANK -> "Istilah yang tepat untuk melengkapi bagian rumpang."
            QuestionType.ESSAY -> {
                when {
                    lower.contains("fungsi") || lower.contains("tujuan") ->
                        "Fungsi dan tujuannya adalah mempermudah proses serta menjaga keseimbangan sistem secara optimal."
                    lower.contains("dampak") || lower.contains("akibat") ->
                        "Dampaknya meliputi perubahan tatanan kondisi secara langsung serta mempengaruhi faktor pendukung di sekitarnya."
                    else ->
                        "Konsep tersebut mengacu pada prinsip dasar yang saling berkaitan untuk mencapai pemahaman komprehensif."
                }
            }
            QuestionType.MULTIPLE_CHOICE -> "A"
        }
    }
}
