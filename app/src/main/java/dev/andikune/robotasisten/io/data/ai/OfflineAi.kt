package dev.andikune.robotasisten.io.data.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import dev.andikune.robotasisten.io.util.FileHelper
import dev.andikune.robotasisten.io.util.LogHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** On-device inference using the verified MediaPipe-compatible Qwen .task model. */
class OfflineAi(
    private val context: Context,
    private val modelDownloader: ModelDownloader
) {
    private val inferenceMutex = Mutex()
    @Volatile private var cachedModelPath: String? = null
    @Volatile private var cachedEngine: LlmInference? = null

    suspend fun generateAnswer(question: String, selectedModelName: String): String? = withContext(Dispatchers.IO) {
        val modelSpec = modelDownloader.availableModels.firstOrNull {
            it.id.equals(selectedModelName, ignoreCase = true) ||
                it.name.equals(selectedModelName, ignoreCase = true) ||
                (selectedModelName.equals("Qwen 2.5 0.5B", ignoreCase = true) && it.id == ModelDownloader.MODEL_ID)
        } ?: return@withContext null
        if (question.isBlank() || !modelDownloader.isModelDownloaded(modelSpec)) {
            LogHelper.w("OfflineAi", "Pertanyaan kosong atau model belum terverifikasi")
            return@withContext null
        }
        inferenceMutex.withLock {
            try {
                val modelFile = File(FileHelper.getModelsDir(context), modelSpec.filename)
                val engine = getEngine(modelFile)
                val prompt = "<|im_start|>system\nKamu adalah asisten AI yang membantu. Jawab dalam bahasa Indonesia dengan jelas dan ringkas. Jika tidak tahu, katakan dengan jujur.\n<|im_end|>\n<|im_start|>user\n${question.take(MAX_QUESTION_CHARS)}\n<|im_end|>\n<|im_start|>assistant\n"
                engine.generateResponse(prompt).trim().takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                LogHelper.w("OfflineAi", "Inferensi MediaPipe gagal: ${e.message}")
                null
            }
        }
    }

    private fun getEngine(file: File): LlmInference {
        val path = file.absolutePath
        cachedEngine?.let { if (cachedModelPath == path) return it }
        cachedEngine?.close()
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
        private const val MAX_QUESTION_CHARS = 4_000
        private const val MAX_GENERATED_TOKENS = 256
    }
}
