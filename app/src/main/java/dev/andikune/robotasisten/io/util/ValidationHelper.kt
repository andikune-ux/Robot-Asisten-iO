package dev.andikune.robotasisten.io.util

import java.net.URI

/** Validates URLs, keys, and formats. */
object ValidationHelper {
    fun isValidApiKey(key: String): Boolean = key.trim().length >= 8

    fun isValidUrl(url: String): Boolean = try {
        val uri = URI(url.trim())
        (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) &&
            !uri.host.isNullOrBlank()
    } catch (_: Exception) {
        false
    }

    fun isNonEmpty(text: String?): Boolean = !text.isNullOrBlank()
}
