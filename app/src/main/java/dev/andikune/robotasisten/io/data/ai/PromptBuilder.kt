package dev.andikune.robotasisten.io.data.ai

import dev.andikune.robotasisten.io.logic.QuestionType

/** Builds language-adaptive prompts for screen questions and study requests. */
object PromptBuilder {
    private const val SAME_LANGUAGE = "Detect the language used in the question and answer in that same language. Do not default to Indonesian if another language is used. "

    fun buildPrompt(question: String, type: QuestionType, options: List<String> = emptyList()): String {
        return when (type) {
            QuestionType.MULTIPLE_CHOICE -> {
                val optionsText = if (options.isNotEmpty()) {
                    "\nOptions:\n" + options.mapIndexed { index, option -> "${'A' + index}. $option" }.joinToString("\n")
                } else ""
                SAME_LANGUAGE + "Choose the most accurate option. State its label and answer text, then give a short reason. Do not answer unrelated screen/interface text.\nQuestion: $question$optionsText"
            }
            QuestionType.CALCULATION -> SAME_LANGUAGE +
                "Solve the math/science problem accurately. Show a concise calculation and the final result.\nQuestion: $question"
            QuestionType.FILL_IN_THE_BLANK -> SAME_LANGUAGE +
                "Complete the blank accurately and concisely.\nQuestion: $question"
            QuestionType.ESSAY -> SAME_LANGUAGE +
                "Answer clearly, directly, and proportionally to the question. Include necessary reasoning and be honest when context is insufficient.\nQuestion: $question"
        }
    }
}
