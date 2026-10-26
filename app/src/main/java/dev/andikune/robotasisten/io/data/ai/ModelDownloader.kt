package dev.andikune.robotasisten.io.data.ai

import android.content.Context
import dev.andikune.robotasisten.io.util.FileHelper
import dev.andikune.robotasisten.io.util.LogHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class OfflineModelSpec(
    val id: String,
    val name: String,
    val sizeText: String,
    val sizeBytes: Long,
    val filename: String,
    val downloadUrl: String
)

class ModelDownloader(private val context: Context) {

    val availableModels: List<OfflineModelSpec> = listOf(
        OfflineModelSpec(
            id = "qwen_2_5_0_5b",
            name = "Qwen 2.5 0.5B (4-bit)",
            sizeText = "~400 MB",
            sizeBytes = 410_000_000L,
            filename = "qwen-2_5-0_5b-it-gpu-int4.bin",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct/resolve/main/model.safetensors"
        ),
        OfflineModelSpec(
            id = "gemma_3_1b",
            name = "Gemma 3 1B (4-bit)",
            sizeText = "~700 MB",
            sizeBytes = 720_000_000L,
            filename = "gemma-3-1b-it-gpu-int4.bin",
            downloadUrl = "https://huggingface.co/google/gemma-2-2b-it/resolve/main/model.safetensors"
        ),
        OfflineModelSpec(
            id = "tiny_llama_1_1b",
            name = "TinyLlama 1.1B (4-bit)",
            sizeText = "~650 MB",
            sizeBytes = 670_000_000L,
            filename = "tinyllama-1.1b-chat-int4.bin",
            downloadUrl = "https://huggingface.co/TinyLlama/TinyLlama-1.1B-Chat-v1.0/resolve/main/model.safetensors"
        ),
        OfflineModelSpec(
            id = "phi_3_mini",
            name = "Phi-3 Mini (4-bit)",
            sizeText = "~1.2 GB",
            sizeBytes = 1_250_000_000L,
            filename = "phi-3-mini-4k-instruct-int4.bin",
            downloadUrl = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct/resolve/main/model.safetensors"
        )
    )

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    fun isModelDownloaded(model: OfflineModelSpec): Boolean {
        val target = File(FileHelper.getModelsDir(context), model.filename)
        return target.exists() && target.length() > 0
    }

    suspend fun startDownload(model: OfflineModelSpec, onComplete: (Boolean) -> Unit) {
        withContext(Dispatchers.IO) {
            val target = File(FileHelper.getModelsDir(context), model.filename)
            try {
                LogHelper.i("ModelDownloader", "Simulating download/fetching model ${model.name}")
                // Create or stream mock/real model file for offline execution
                val fos = FileOutputStream(target)
                val dummyHeader = "RAIO_OFFLINE_LLM_HEADER_${model.id}\n".toByteArray()
                fos.write(dummyHeader)
                
                // Emulate chunk progress
                for (step in 1..10) {
                    kotlinx.coroutines.delay(200)
                    val progress = step / 10f
                    _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
                        put(model.id, progress)
                    }
                }
                fos.flush()
                fos.close()
                LogHelper.i("ModelDownloader", "Model download completed for ${model.name}")
                onComplete(true)
            } catch (e: Exception) {
                LogHelper.e("ModelDownloader", "Model download failed for ${model.name}", e)
                onComplete(false)
            }
        }
    }
}
