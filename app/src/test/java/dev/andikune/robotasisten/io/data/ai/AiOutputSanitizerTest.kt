package dev.andikune.robotasisten.io.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiOutputSanitizerTest {
    @Test
    fun removesProtocolAndPrivateReasoningMarkersButKeepsUsefulTextAndCode() {
        val input = "assistant: Halo. <|im_end|>\n<think>penalaran internal</think>\n```kotlin\nprintln(\"hai\")\n```"
        assertEquals("Halo.\n\n```kotlin\nprintln(\"hai\")\n```", AiOutputSanitizer.clean(input))
    }

    @Test
    fun emptyControlOnlyResponseIsRejected() {
        assertNull(AiOutputSanitizer.clean("<|im_end|><|endoftext|>"))
    }

    @Test
    fun acceptsOnlyExactResumableContentRange() {
        assertTrue(isValidContentRange("bytes 100-999/1000", 100, 1000))
        assertFalse(isValidContentRange("bytes 99-999/1000", 100, 1000))
        assertFalse(isValidContentRange("bytes 100-998/1000", 100, 1000))
        assertFalse(isValidContentRange("bytes 100-999/1001", 100, 1000))
        assertFalse(isValidContentRange(null, 100, 1000))
    }
}
