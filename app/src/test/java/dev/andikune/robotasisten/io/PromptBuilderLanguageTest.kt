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
}
