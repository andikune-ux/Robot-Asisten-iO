package dev.andikune.robotasisten.io.data.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderConfigTest {
    @Test fun unsupportedNativeProtocolsAreMarkedUnsupported() {
        assertFalse(AiProviderConfig.getProvider("Google Gemini").supportsOpenAiChatCompletions)
        assertFalse(AiProviderConfig.getProvider("Anthropic Claude").supportsOpenAiChatCompletions)
        assertTrue(AiProviderConfig.getProvider("OpenAI").supportsOpenAiChatCompletions)
    }

    @Test fun unknownProviderDoesNotSilentlySelectOpenAi() {
        assertThrows(IllegalArgumentException::class.java) { AiProviderConfig.getProvider("typo") }
    }
}
