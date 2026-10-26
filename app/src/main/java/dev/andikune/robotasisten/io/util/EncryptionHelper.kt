package dev.andikune.robotasisten.io.util

import android.util.Base64
import java.security.MessageDigest

/**
 * Hashing and obfuscation utilities.
 */
object EncryptionHelper {
    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun maskKey(key: String): String {
        if (key.length <= 8) return "••••••••"
        return key.take(3) + "••••••••" + key.takeLast(4)
    }

    fun base64Encode(text: String): String {
        return Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    fun base64Decode(encoded: String): String {
        return String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8)
    }
}
