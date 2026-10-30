package dev.andikune.robotasisten.io.data.ai

import android.content.Context
import dev.andikune.robotasisten.io.util.LogHelper

/**
 * Hardware acceleration & threading manager for local offline AI inference.
 * Allocates optimal CPU cores and configures inference parameters so that
 * the UI thread remains fluid while maximizing throughput.
 */
class LocalAIEngine(private val context: Context) {

    data class InferenceConfig(
        val threads: Int,
        val useGpuDelegate: Boolean,
        val temperature: Float,
        val maxTopK: Int,
        val maxTokens: Int
    )

    fun configureModelThreads(): Int {
        val availableCores = Runtime.getRuntime().availableProcessors()
        // Alokasikan 75% core CPU untuk inference agar UI thread tetap mulus
        val optimalThreads = (availableCores * 0.75).toInt().coerceAtLeast(2)
        LogHelper.d("LocalAIEngine", "Available cores: $availableCores, allocated inference threads: $optimalThreads")
        return optimalThreads
    }

    fun initInferenceParams(chatMode: RailxChatMode = RailxChatMode.SEDANG): InferenceConfig {
        val numThreads = configureModelThreads()
        val (temp, tokens) = when (chatMode) {
            RailxChatMode.FAST -> 0.6f to 256
            RailxChatMode.SEDANG -> 0.7f to 512
            RailxChatMode.PRO -> 0.8f to 1024
        }

        return InferenceConfig(
            threads = numThreads,
            useGpuDelegate = true,
            temperature = temp,
            maxTopK = 40,
            maxTokens = tokens
        )
    }
}
