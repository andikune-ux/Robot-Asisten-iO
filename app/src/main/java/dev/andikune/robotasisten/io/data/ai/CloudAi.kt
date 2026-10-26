package dev.andikune.robotasisten.io.data.ai

import dev.andikune.robotasisten.io.data.security.ApiKeyStore
import dev.andikune.robotasisten.io.logic.QuestionDetector
import dev.andikune.robotasisten.io.util.LogHelper
import dev.andikune.robotasisten.io.util.RateLimiter
import dev.andikune.robotasisten.io.util.SanitizerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Mode 3: Cloud AI Open Key engine supporting OpenAI, Gemini, Claude, Groq, DeepSeek, etc.
 */
class CloudAi(
    private val apiKeyStore: ApiKeyStore,
    private val rateLimiter: RateLimiter
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun validateApiKey(providerName: String, apiKey: String, customBaseUrl: String? = null): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val provider = AiProviderConfig.getProvider(providerName)
                require(provider.supportsOpenAiChatCompletions) { "Protokol provider $providerName belum didukung" }
                val baseUrl = resolveBaseUrl(provider, customBaseUrl)
                val testUrl = "${baseUrl}models"
                
                val request = Request.Builder()
                    .url(testUrl)
                    .addHeader("Authorization", "Bearer ${apiKey.trim()}")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    when (response.code) {
                        in 200..299 -> Result.success(true)
                        401, 403 -> Result.failure(IllegalArgumentException("API Key ditolak (HTTP ${response.code}). Periksa kembali kunci Anda."))
                        else -> Result.failure(IllegalStateException("Validasi provider gagal (HTTP ${response.code}); endpoint/protokol tidak dapat diverifikasi."))
                    }
                }
            } catch (e: Exception) {
                LogHelper.e("CloudAi", "Failed validating key: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    suspend fun query(question: String, providerName: String, modelName: String, customBaseUrl: String? = null): String? {
        if (!rateLimiter.canProceed()) {
            LogHelper.w("CloudAi", "Rate limit exceeded, delaying query")
            return null
        }

        val apiKey = apiKeyStore.getApiKey(providerName)
        if (apiKey.isNullOrBlank()) {
            LogHelper.w("CloudAi", "No API key found for provider $providerName")
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                val provider = AiProviderConfig.getProvider(providerName)
                require(provider.supportsOpenAiChatCompletions) { "Protokol provider $providerName belum didukung" }
                require(modelName.isNotBlank()) { "Model AI belum dipilih" }
                val baseUrl = resolveBaseUrl(provider, customBaseUrl)
                val sanitizedQuery = SanitizerHelper.sanitizeQuery(question)
                val detected = QuestionDetector.detect(sanitizedQuery)
                val prompt = PromptBuilder.buildPrompt(sanitizedQuery, detected.type, detected.options)

                val endpoint = "${baseUrl}chat/completions"

                val payload = JSONObject().apply {
                    put("model", modelName)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", "You are an expert study assistant. Answer clearly, accurately, and concisely in Indonesian.")
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                    put("temperature", 0.2)
                    put("max_tokens", 300)
                }

                val body = payload.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val choices = JSONObject(responseBody).optJSONArray("choices")
                        val content = choices?.takeIf { it.length() > 0 }
                            ?.getJSONObject(0)?.optJSONObject("message")?.optString("content")
                            ?.takeIf { it.isNotBlank() }
                        return@withContext content
                    }
                    LogHelper.w("CloudAi", "HTTP error ${response.code}: $responseBody")
                    null
                }
            } catch (e: Exception) {
                LogHelper.e("CloudAi", "Cloud query failed", e)
                null
            }
        }
    }

    private fun resolveBaseUrl(provider: AiProviderItem, customBaseUrl: String?): String {
        val candidate = if (provider.isCustom) customBaseUrl?.trim().orEmpty() else provider.baseUrl
        require(candidate.isNotBlank()) { "Masukkan Base URL HTTPS untuk provider kustom" }
        val parsed = candidate.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Base URL tidak valid")
        require(parsed.scheme == "https") { "Base URL harus menggunakan HTTPS agar API key tidak dikirim tanpa enkripsi" }
        require(parsed.username.isEmpty() && parsed.password.isEmpty() && parsed.query == null && parsed.fragment == null) {
            "Base URL tidak boleh berisi kredensial, query, atau fragment"
        }
        val normalizedPath = parsed.encodedPath.trimEnd('/') + "/"
        return parsed.newBuilder().encodedPath(normalizedPath).build().toString()
    }
}
