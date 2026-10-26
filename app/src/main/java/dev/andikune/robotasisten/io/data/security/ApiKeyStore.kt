package dev.andikune.robotasisten.io.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dev.andikune.robotasisten.io.util.LogHelper

/**
 * Secure persistent store for user-provided Cloud AI API Keys using Android Keystore / EncryptedSharedPreferences.
 */
class ApiKeyStore(context: Context) {

    private val sharedPreferences: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "raio_secure_keys",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        LogHelper.e("ApiKeyStore", "Failed initializing EncryptedSharedPreferences, falling back", e)
        context.getSharedPreferences("raio_secure_keys_fallback", Context.MODE_PRIVATE)
    }

    fun saveApiKey(provider: String, key: String) {
        sharedPreferences.edit()
            .putString(getProviderKey(provider), key.trim())
            .apply()
    }

    fun getApiKey(provider: String): String? {
        return sharedPreferences.getString(getProviderKey(provider), null)?.takeIf { it.isNotBlank() }
    }

    fun hasApiKey(provider: String): Boolean {
        return !getApiKey(provider).isNullOrBlank()
    }

    fun removeApiKey(provider: String) {
        sharedPreferences.edit()
            .remove(getProviderKey(provider))
            .apply()
    }

    fun clearAllKeys() {
        sharedPreferences.edit().clear().apply()
    }

    private fun getProviderKey(provider: String): String = "key_${provider.lowercase()}"
}
