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
        reasoningDepth: String = "balanced"
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
                val engine = getEngine(modelFile)

                val system = buildString {
                    appendLine("You are RELX AI, a helpful assistant.")
                    appendLine("For study requests containing a Question: field, detect the language of the actual question after that label and answer in that language. For a RELX Power request containing a Tugas pengguna: field, answer in the language of that task; if it starts with AUTO_LANGUAGE, infer the language from the actual question/screen text. For general chat, answer in the language of the user's latest natural-language message. Do not default to Indonesian when another language is used. Preserve requested language unless the user asks to switch.")
                    appendLine("Sound natural, warm, and direct, like a skilled human assistant. Avoid stiff literal translation, repetitive openings, generic filler, and awkward phrasing. Be accurate and honest about uncertainty. Never claim to have performed actions you did not perform.")
                    appendLine("Think carefully and check assumptions before answering. Give the useful conclusion and concise key steps, not private hidden chain-of-thought. Use the requested depth: ${reasoningDepth.take(20)}.")
                    appendLine("For coding requests, identify the language/platform, explain important assumptions, provide a complete usable solution when enough information is available, and put code or copy-ready templates in a labeled fenced code block. Do not output model control tokens or raw prompt metadata.")
                    appendLine("When the user provides screen/OCR text, ignore navigation, buttons, advertisements, and unrelated interface labels. Find the complete actual question/task and its options first; if no question is visible, say what is missing rather than answering unrelated screen text.")
                    if (persona.isNotBlank()) appendLine("Persona: ${persona.take(MAX_CONTEXT_CHARS)}")
                    if (instructions.isNotBlank()) appendLine("Instruksi: ${instructions.take(MAX_CONTEXT_CHARS)}")
                    if (memory.isNotBlank()) appendLine("Memori: ${memory.take(MAX_CONTEXT_CHARS)}")
                }

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

    private fun getEngine(file: File): LlmInference {
        val path = file.absolutePath
        cachedEngine?.let {
            if (cachedModelPath == path) return it
        }

        runCatching { cachedEngine?.close() }
        cachedEngine = null

        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(path)
            .setMaxTokens(MAX_GENERATED_TOKENS)
            .setMaxTopK(40)
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
