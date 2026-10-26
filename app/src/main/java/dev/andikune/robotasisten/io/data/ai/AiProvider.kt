package dev.andikune.robotasisten.io.data.ai

import com.google.gson.annotations.SerializedName
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.3,
    @SerializedName("max_tokens") val maxTokens: Int = 300
)

data class ChatChoice(
    val message: ChatMessage
)

data class ChatCompletionResponse(
    val choices: List<ChatChoice>?
)

data class ModelItem(
    val id: String
)

data class ModelListResponse(
    val data: List<ModelItem>?
)

interface OpenAiCompatibleApi {
    @GET
    suspend fun getModels(
        @Url url: String,
        @Header("Authorization") authHeader: String
    ): Response<ResponseBody>

    @POST
    suspend fun createChatCompletion(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Body request: ChatCompletionRequest
    ): Response<ResponseBody>
}
