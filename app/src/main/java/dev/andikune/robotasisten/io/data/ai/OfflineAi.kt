package dev.andikune.robotasisten.io.data.ai

import android.content.Context
import dev.andikune.robotasisten.io.util.FileHelper
import dev.andikune.robotasisten.io.util.LogHelper
import java.io.File

/** Mode 2: local inference, currently unsupported until a real inference runtime/model is installed. */
class OfflineAi(
    private val context: Context,
    private val modelDownloader: ModelDownloader
) {
    suspend fun generateAnswer(question: String, selectedModelName: String): String? {
        val modelSpec = modelDownloader.availableModels.firstOrNull {
            it.id.equals(selectedModelName, ignoreCase = true) ||
                it.name.equals(selectedModelName, ignoreCase = true) ||
                it.name.startsWith(selectedModelName, ignoreCase = true)
        }
        if (modelSpec == null) {
            LogHelper.w("OfflineAi", "Unsupported offline model: $selectedModelName")
            return null
        }
        val modelFile = File(FileHelper.getModelsDir(context), modelSpec.filename)
        if (!modelFile.exists() || modelFile.length() == 0L) {
            LogHelper.w("OfflineAi", "Model file ${modelSpec.filename} not downloaded")
            return null
        }
        LogHelper.w("OfflineAi", "Offline inference runtime is not implemented; declining fabricated answer")
        return null
    }
}
