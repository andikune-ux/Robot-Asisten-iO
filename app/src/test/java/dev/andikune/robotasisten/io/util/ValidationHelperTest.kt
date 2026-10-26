package dev.andikune.robotasisten.io.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationHelperTest {
    @Test fun acceptsOnlyAbsoluteHttpUrlsWithHost() {
        assertTrue(ValidationHelper.isValidUrl("https://example.com/v1"))
        assertTrue(ValidationHelper.isValidUrl("http://localhost:11434/v1"))
        assertFalse(ValidationHelper.isValidUrl("https://"))
        assertFalse(ValidationHelper.isValidUrl("file:///tmp/key"))
        assertFalse(ValidationHelper.isValidUrl("not a url"))
    }
}
