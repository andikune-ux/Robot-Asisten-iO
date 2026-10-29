package dev.andikune.robotasisten.io

import dev.andikune.robotasisten.io.data.ai.PromptBuilder
import dev.andikune.robotasisten.io.logic.QuestionType
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderLanguageTest {
    @Test fun promptsAskModelToFollowQuestionLanguage() {
        QuestionType.values().forEach { type ->
            val prompt = PromptBuilder.buildPrompt("What is the answer?", type, listOf("Yes", "No"))
            assertTrue("Missing language direction for $type", prompt.contains("same language"))
            assertTrue("Question should be preserved for $type", prompt.contains("What is the answer?"))
            org.junit.Assert.assertFalse(prompt.contains("Bahasa Indonesia yang baik"))
        }
    }

    @Test fun testToolSpecificPrompts() {
        // Tool 1: Siapkan Jawaban (Direct answer, view only)
        val directPrompt = PromptBuilder.buildDirectAnswerPrompt(
            question = "Apa ibu kota Indonesia?",
            type = QuestionType.MULTIPLE_CHOICE,
            options = listOf("Jakarta", "Nusantara")
        )
        assertTrue(directPrompt.contains("FORMAT JAWABAN"))
        assertTrue(directPrompt.contains("DILARANG mengarang fakta"))
        assertTrue(directPrompt.contains("Nusantara"))

        // Tool 2: Analisis Layar (Deep rationale)
        val deepPrompt = PromptBuilder.buildDeepAnalysisPrompt(
            question = "Mengapa fotosintesis penting?",
            type = QuestionType.ESSAY
        )
        assertTrue(deepPrompt.contains("JAWABAN TEPAT"))
        assertTrue(deepPrompt.contains("PEMBAHASAN MENDALAM & RATIONALE"))

        // Tool 3: RAILX Power (Explicit target)
        val powerPrompt = PromptBuilder.buildPowerAutoFillPrompt(
            question = "Pilih rumus luas lingkaran",
            type = QuestionType.MULTIPLE_CHOICE,
            options = listOf("πr²", "2πr")
        )
        assertTrue(powerPrompt.contains("TARGET"))
    }
}
