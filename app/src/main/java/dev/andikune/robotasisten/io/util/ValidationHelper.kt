package dev.andikune.robotasisten.io.util

/**
 * Validates URLs, keys, and formats.
 */
object ValidationHelper {
    fun isValidApiKey(key: String): Boolean {
        return key.trim().length >= 8
    }

    fun isValidUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }

    fun isNonEmpty(text: String?): Boolean {
        return !text.isNullOrBlank()
    }
}
