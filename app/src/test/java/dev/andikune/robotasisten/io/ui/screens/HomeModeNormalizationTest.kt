package dev.andikune.robotasisten.io.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeModeNormalizationTest {
    @Test
    fun savedAiOfflineModeMapsToEnabledHomeSlug() {
        assertEquals("offline", normalizeHomeMode("AI_OFFLINE"))
        assertEquals("offline", normalizeHomeMode("offline"))
    }

    @Test
    fun savedApiKeyModeMapsToEnabledHomeSlug() {
        assertEquals("cloud", normalizeHomeMode("AI_OPEN_KEY"))
        assertEquals("cloud", normalizeHomeMode("cloud"))
    }

    @Test
    fun libraryAndUnknownModesAreHandled() {
        assertEquals("library", normalizeHomeMode("LIBRARY"))
        assertEquals("", normalizeHomeMode(""))
        assertEquals("", normalizeHomeMode("unsupported"))
    }
}
