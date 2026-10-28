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
 * Handles protocol differences between OpenAI-compatible, Google Gemini, and Anthropic Claude APIs.
 */
class CloudAi(
    private val apiKeyStore: ApiKeyStore,
    private val rateLimiter: RateLimiter
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private fun isGemini(provider: AiProviderItem): Boolean {
        return provider.id.contains("gemini", ignoreCase = true) ||
                provider.baseUrl.contains("generativelanguage.googleapis.com", ignoreCase = true)
    }

    private fun isClaude(provider: AiProviderItem): Boolean {
        return provider.id.contains("claude", ignoreCase = true) ||
                provider.id.contains("anthropic", ignoreCase = true) ||
                provider.baseUrl.contains("anthropic.com", ignoreCase = true)
    }

    suspend fun validateApiKey(
        providerName: String,
        apiKey: String,
        customBaseUrl: String? = null
    ): Result<Boolean> {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return Result.failure(Exception("API Key tidak boleh kosong."))
        }

        return withContext(Dispatchers.IO) {
            try {
                val provider = AiProviderConfig.getProvider(providerName)
                val request = when {
                    isGemini(provider) && customBaseUrl.isNullOrBlank() -> {
                        val testUrl = "https://generativelanguage.googleapis.com/v1beta/models?key=$cleanKey"
                        Request.Builder().url(testUrl).get().build()
                    }
                    isClaude(provider) && customBaseUrl.isNullOrBlank() -> {
                        val testUrl = "https://api.anthropic.com/v1/models"
                        Request.Builder()
                            .url(testUrl)
                            .addHeader("x-api-key", cleanKey)
                            .addHeader("anthropic-version", "2023-06-01")
                            .get()
                            .build()
                    }
                    else -> {
                        val base = if (!customBaseUrl.isNullOrBlank()) {
                            customBaseUrl.trimEnd('/')
                        } else {
                            provider.baseUrl.trimEnd('/')
                        }
                        val testUrl = "$base/models"
                        Request.Builder()
                            .url(testUrl)
                            .addHeader("Authorization", "Bearer $cleanKey")
                            .get()
                            .build()
                    }
                }

                val response = httpClient.newCall(request).execute()
                val code = response.code
                val responseBody = response.body?.string() ?: ""
                response.close()

                if (code in 200..299) {
                    Result.success(true)
                } else if (code in 400..403) {
                    LogHelper.w("CloudAi", "Key validation failed (HTTP $code)")
                    Result.failure(Exception("API Key ditolak (HTTP $code). Periksa kembali kunci Anda."))
                } else {
                    Result.success(true)
                }
            } catch (e: Exception) {
                LogHelper.e("CloudAi", "Failed validating key: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    suspend fun query(
        question: String,
        providerName: String,
        modelName: String,
        customBaseUrl: String? = null
    ): String? {
        if (!rateLimiter.canProceed()) {
            LogHelper.w("CloudAi", "Rate limit exceeded, delaying query")
            return null
        }

        val apiKey = apiKeyStore.getApiKey(providerName)?.trim()
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

                when {
                    isGemini(provider) && customBaseUrl.isNullOrBlank() -> queryGemini(apiKey, modelName, prompt)
                    isClaude(provider) && customBaseUrl.isNullOrBlank() -> queryClaude(apiKey, modelName, prompt)
                    else -> queryOpenAiCompatible(provider, apiKey, modelName, prompt, customBaseUrl)
                }
            } catch (e: Exception) {
                LogHelper.e("CloudAi", "Cloud query failed", e)
                null
            }
        }
    }

    suspend fun queryConversation(
        history: List<Pair<String, String>>,
        userMessage: String,
        providerName: String,
        modelName: String,
        customBaseUrl: String? = null,
        persona: String = "",
        memory: String = "",
        instructions: String = "",
        reasoningDepth: String = "balanced"
    ): String? {
        if (!rateLimiter.canProceed()) {
            LogHelper.w("CloudAi", "Rate limit exceeded, delaying queryConversation")
            return null
        }

        val apiKey = apiKeyStore.getApiKey(providerName)?.trim()
        if (apiKey.isNullOrBlank()) {
            LogHelper.w("CloudAi", "No API key found for provider $providerName")
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                val provider = AiProviderConfig.getProvider(providerName)
                val systemPrompt = buildString {
                    if (persona.isNotBlank()) appendLine(persona)
                    if (memory.isNotBlank()) appendLine("Konteks/Memori: $memory")
                    if (instructions.isNotBlank()) appendLine("Instruksi: $instructions")
                    if (isEmpty()) append("You are an expert study assistant. Answer clearly, accurately, and concisely in Indonesian.")
                }.trim()

                when {
                    isGemini(provider) && customBaseUrl.isNullOrBlank() -> {
                        queryGeminiConversation(apiKey, modelName, systemPrompt, history, userMessage)
                    }
                    isClaude(provider) && customBaseUrl.isNullOrBlank() -> {
                        queryClaudeConversation(apiKey, modelName, systemPrompt, history, userMessage)
                    }
                    else -> {
                        queryOpenAiConversation(provider, apiKey, modelName, customBaseUrl, systemPrompt, history, userMessage)
                    }
                }
            } catch (e: Exception) {
                LogHelper.e("CloudAi", "queryConversation failed", e)
                null
            }
        }
    }

    private fun queryGemini(apiKey: String, modelName: String, prompt: String): String? {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/${modelName}:generateContent?key=$apiKey"
        val payload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "You are an expert study assistant. Answer clearly, accurately, and concisely in Indonesian.\n\n$prompt")
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
                put("maxOutputTokens", 400)
            })
        }

        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(endpoint)
            .post(body)
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""
        response.close()

        if (response.isSuccessful) {
            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val text = parts.getJSONObject(0).optString("text")
                    return text.trim()
                }
            }
        }
        LogHelper.w("CloudAi", "Gemini HTTP error ${response.code}")
        return null
    }

    private fun queryGeminiConversation(
        apiKey: String,
        modelName: String,
        systemPrompt: String,
        history: List<Pair<String, String>>,
        userMessage: String
    ): String? {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/${modelName}:generateContent?key=$apiKey"
        val contentsArray = JSONArray()

        // Context / System instruction as initial turn
        contentsArray.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().apply {
                put(JSONObject().apply { put("text", systemPrompt) })
            })
        })
        contentsArray.put(JSONObject().apply {
            put("role", "model")
            put("parts", JSONArray().apply {
                put(JSONObject().apply { put("text", "Dimengerti, saya siap membantu.") })
            })
        })

        // Conversation history
        for ((u, a) in history) {
            contentsArray.put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply { put(JSONObject().apply { put("text", u) }) })
            })
            contentsArray.put(JSONObject().apply {
                put("role", "model")
                put("parts", JSONArray().apply { put(JSONObject().apply { put("text", a) }) })
            })
        }

        // Current message
        contentsArray.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().apply { put(JSONObject().apply { put("text", userMessage) }) })
        })

        val payload = JSONObject().apply {
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3)
                put("maxOutputTokens", 800)
            })
        }

        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(endpoint).post(body).build()
        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""
        response.close()

        if (response.isSuccessful) {
            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)
                val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    return parts.getJSONObject(0).optString("text").trim()
                }
            }
        }
        return null
    }

    private fun queryClaude(apiKey: String, modelName: String, prompt: String): String? {
        val endpoint = "https://api.anthropic.com/v1/messages"
        val payload = JSONObject().apply {
            put("model", modelName)
            put("max_tokens", 400)
            put("system", "You are an expert study assistant. Answer clearly, accurately, and concisely in Indonesian.")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""
        response.close()

        if (response.isSuccessful) {
            val json = JSONObject(responseBody)
            val contentArr = json.optJSONArray("content")
            if (contentArr != null && contentArr.length() > 0) {
                val firstPart = contentArr.getJSONObject(0)
                val text = firstPart.optString("text")
                return text.trim()
            }
        }
        LogHelper.w("CloudAi", "Claude HTTP error ${response.code}")
        return null
    }

    private fun queryClaudeConversation(
        apiKey: String,
        modelName: String,
        systemPrompt: String,
        history: List<Pair<String, String>>,
        userMessage: String
    ): String? {
        val endpoint = "https://api.anthropic.com/v1/messages"
        val messagesArray = JSONArray()

        for ((u, a) in history) {
            messagesArray.put(JSONObject().apply {
                put("role", "user")
                put("content", u)
            })
            messagesArray.put(JSONObject().apply {
                put("role", "assistant")
                put("content", a)
            })
        }

        messagesArray.put(JSONObject().apply {
            put("role", "user")
            put("content", userMessage)
        })

        val payload = JSONObject().apply {
            put("model", modelName)
            put("max_tokens", 800)
            put("system", systemPrompt)
            put("messages", messagesArray)
        }

        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""
        response.close()

        if (response.isSuccessful) {
            val json = JSONObject(responseBody)
            val contentArr = json.optJSONArray("content")
            if (contentArr != null && contentArr.length() > 0) {
                return contentArr.getJSONObject(0).optString("text").trim()
            }
        }
        return null
    }

    private fun queryOpenAiCompatible(
        provider: AiProviderItem,
        apiKey: String,
        modelName: String,
        prompt: String,
        customBaseUrl: String? = null
    ): String? {
        val base = if (!customBaseUrl.isNullOrBlank()) customBaseUrl.trimEnd('/') else provider.baseUrl.trimEnd('/')
        val endpoint = "$base/chat/completions"

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
            put("max_tokens", 400)
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
                return content?.trim()
            }
        }
        LogHelper.w("CloudAi", "OpenAI HTTP error ${response.code}")
        return null
    }

    private fun queryOpenAiConversation(
        provider: AiProviderItem,
        apiKey: String,
        modelName: String,
        customBaseUrl: String?,
        systemPrompt: String,
        history: List<Pair<String, String>>,
        userMessage: String
    ): String? {
        val base = if (!customBaseUrl.isNullOrBlank()) customBaseUrl.trimEnd('/') else provider.baseUrl.trimEnd('/')
        val endpoint = "$base/chat/completions"

        val messagesArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            for ((u, a) in history) {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", u)
                })
                put(JSONObject().apply {
                    put("role", "assistant")
                    put("content", a)
                })
            }
            put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })
        }

        val payload = JSONObject().apply {
            put("model", modelName)
            put("messages", messagesArray)
            put("temperature", 0.3)
            put("max_tokens", 800)
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
                val message = choices.getJSONObject(0).optJSONObject("message")
                return message?.optString("content")?.trim()
            }
        }
        return null
    }
}
