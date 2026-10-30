package dev.andikune.robotasisten.io.data.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import dev.andikune.robotasisten.io.util.FileHelper
import dev.andikune.robotasisten.io.util.LogHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Mode 2: Local on-device AI Model via MediaPipe GenAI LLM Inference.
 * Implements strict context bounding, asynchronous execution on Dispatchers.IO,
 * Mutex locking against race conditions, and graceful memory recovery against OOM.
 */
class OfflineAi(
    private val context: Context,
    private val modelDownloader: ModelDownloader
) {
    private val inferenceMutex = Mutex()
    @Volatile private var cachedModelPath: String? = null
    @Volatile private var cachedEngine: LlmInference? = null

    suspend fun releaseCachedEngine() = withContext(Dispatchers.IO) {
        inferenceMutex.withLock {
            runCatching { cachedEngine?.close() }
            cachedEngine = null
            cachedModelPath = null
            System.gc()
        }
    }

    suspend fun generateAnswer(question: String, selectedModelName: String): String? =
        generateChatReply(selectedModelName, emptyList(), question, DEFAULT_STUDY_PERSONA, "", "")

    suspend fun generateChatReply(
        selectedModelName: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        persona: String,
        memory: String,
        instructions: String,
        reasoningDepth: String = "balanced",
        chatMode: RailxChatMode = RailxChatMode.SEDANG
    ): String? = withContext(Dispatchers.IO) {
        val modelSpec = modelDownloader.availableModels.firstOrNull {
            it.id.equals(selectedModelName, ignoreCase = true) ||
            it.name.equals(selectedModelName, ignoreCase = true) ||
            (selectedModelName.equals("Qwen 2.5 0.5B", ignoreCase = true) && it.id == ModelDownloader.MODEL_ID)
        } ?: modelDownloader.availableModels.first()

        if (userMessage.isBlank()) {
            return@withContext null
        }

        if (!modelDownloader.isModelDownloaded(modelSpec)) {
            LogHelper.w("OfflineAi", "Model ${modelSpec.name} belum diunduh")
            return@withContext "Model AI Offline (${modelSpec.name}) belum diunduh atau belum selesai. Silakan unduh model di halaman Mode AI Offline terlebih dahulu, atau gunakan Mode AI Cloud."
        }

        inferenceMutex.withLock {
            try {
                val modelFile = File(FileHelper.getModelsDir(context), modelSpec.filename)
                if (!modelFile.exists() || modelFile.length() != modelSpec.sizeBytes) {
                    return@withLock "File model tidak lengkap di penyimpanan perangkat. Silakan buka halaman Mode AI Offline untuk mengunduh ulang model."
                }
                val engine = getEngine(modelFile, chatMode)

                val userAiSettings = UserAiSettings(
                    customInstruction = listOfNotNull(
                        instructions.takeIf { it.isNotBlank() },
                        persona.takeIf { it.isNotBlank() }
                    ).joinToString("\n"),
                    memoryKnowledge = memory,
                    chatMode = chatMode
                )
                val chatConfig = RailxChatEngine.buildChatConfig(userAiSettings)
                val system = chatConfig.systemPrompt

                val safeMessage = clampMessageText(userMessage)

                val prompt = buildString {
                    if (modelSpec.promptStyle == "TINYLLAMA_CHAT") {
                        append("<|system|>\n$system\n")
                        history.takeLast(MAX_HISTORY_MESSAGES).forEach { (role, content) ->
                            val safeRole = if (role == "assistant") "assistant" else "user"
                            append("<$safeRole>\n${clampMessageText(content)}\n")
                        }
                        append("<|user|>\n$safeMessage\n<|assistant|>\n")
                    } else {
                        append("<|im_start|>system\n")
                        append(system)
                        append("<|im_end|>\n")
                        history.takeLast(MAX_HISTORY_MESSAGES).forEach { (role, content) ->
                            val safeRole = if (role == "assistant") "assistant" else "user"
                            append("<|im_start|>$safeRole\n${clampMessageText(content)}\n<|im_end|>\n")
                        }
                        append("<|im_start|>user\n$safeMessage\n<|im_end|>\n<|im_start|>assistant\n")
                    }
                }

                val rawResponse = engine.generateResponse(prompt)
                val cleaned = AiOutputSanitizer.clean(rawResponse)

                // Explicit GC after heavy memory operation
                if (userMessage.length > 2000) {
                    System.gc()
                }

                cleaned
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                LogHelper.e("OfflineAi", "MediaPipe on-device inference error: ${t.message}", t)
                // Release memory and GC on failure to prevent repeated crashes
                runCatching { cachedEngine?.close() }
                cachedEngine = null
                cachedModelPath = null
                System.gc()
                "Model AI Offline kehabisan memori RAM saat memproses teks ini. Silakan gunakan pertanyaan lebih ringkas atau beralih ke Mode AI Cloud."
            }
        }
    }

    private fun clampMessageText(text: String): String {
        val clean = text.trim()
        if (clean.length <= MAX_MESSAGE_CHARS) return clean
        // Retain start and end (question and options)
        val head = clean.take(800)
        val tail = clean.takeLast(1200)
        return "$head\n[...]\n$tail"
    }

    private fun getEngine(file: File, chatMode: RailxChatMode = RailxChatMode.SEDANG): LlmInference {
        val path = file.absolutePath
        val localEngine = LocalAIEngine(context)
        val config = localEngine.initInferenceParams(chatMode)

        cachedEngine?.let {
            if (cachedModelPath == path) return it
        }

        runCatching { cachedEngine?.close() }
        cachedEngine = null

        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(path)
            .setMaxTokens(config.maxTokens)
            .setMaxTopK(config.maxTopK)
            .build()

        return LlmInference.createFromOptions(context.applicationContext, options).also {
            cachedModelPath = path
            cachedEngine = it
        }
    }

    companion object {
        private const val MAX_MESSAGE_CHARS = 2_200
        private const val MAX_CONTEXT_CHARS = 1_000
        private const val MAX_HISTORY_MESSAGES = 6
        private const val MAX_GENERATED_TOKENS = 512
        private const val DEFAULT_STUDY_PERSONA = "A clear, supportive tutor"
    }
}
