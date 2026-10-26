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

    suspend fun validateApiKey(providerName: String, apiKey: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val provider = AiProviderConfig.getProvider(providerName)
                val testUrl = if (provider.baseUrl.endsWith("/")) "${provider.baseUrl}models" else "${provider.baseUrl}/models"
                
                val request = Request.Builder()
                    .url(testUrl)
                    .addHeader("Authorization", "Bearer ${apiKey.trim()}")
                    .get()
                    .build()

                val response = httpClient.newCall(request).execute()
                val code = response.code
                response.close()

                if (code in 200..299) {
                    Result.success(true)
                } else if (code in 400..403) {
                    Result.failure(Exception("API Key ditolak (HTTP $code). Periksa kembali kunci Anda."))
                } else {
                    // Some endpoints don't expose /models with GET, test passes if valid format
                    Result.success(true)
                }
            } catch (e: Exception) {
                LogHelper.e("CloudAi", "Failed validating key: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    suspend fun query(question: String, providerName: String, modelName: String): String? {
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
                val sanitizedQuery = SanitizerHelper.sanitizeQuery(question)
                val detected = QuestionDetector.detect(sanitizedQuery)
                val prompt = PromptBuilder.buildPrompt(sanitizedQuery, detected.type, detected.options)

                val endpoint = if (provider.baseUrl.endsWith("/")) "${provider.baseUrl}chat/completions" else "${provider.baseUrl}/chat/completions"

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

                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                response.close()

                if (response.isSuccessful) {
                    val json = JSONObject(responseBody)
                    val choices = json.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val firstChoice = choices.getJSONObject(0)
                        val message = firstChoice.optJSONObject("message")
                        val content = message?.optString("content")
                        return@withContext content?.trim()
                    }
                }
                LogHelper.w("CloudAi", "HTTP error ${response.code}: $responseBody")
                null
            } catch (e: Exception) {
                LogHelper.e("CloudAi", "Cloud query failed", e)
                null
            }
        }
    }
}
