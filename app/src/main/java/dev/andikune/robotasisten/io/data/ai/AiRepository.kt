package dev.andikune.robotasisten.io.data.ai

import dev.andikune.robotasisten.io.data.repo.SettingsRepository

/**
 * Unified AI repository managing all 3 modes: Library, AI Offline, and AI Open Key.
 */
class AiRepository(
    private val libraryAi: LibraryAi,
    private val offlineAi: OfflineAi,
    private val cloudAi: CloudAi,
    private val settingsRepository: SettingsRepository
) {

    suspend fun queryLibrary(question: String): String? {
        return libraryAi.answer(question)
    }

    suspend fun queryOffline(question: String): String? {
        val model = settingsRepository.selectedOfflineModel.value
        return offlineAi.generateAnswer(question, model)
    }

    suspend fun queryCloud(question: String): String? {
        val provider = settingsRepository.selectedProvider.value
        val model = settingsRepository.selectedModel.value
        return cloudAi.query(question, provider, model, settingsRepository.customBaseUrl.value)
    }

    suspend fun chatReply(
        mode: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        persona: String,
        memory: String,
        instructions: String
    ): String? = when (mode.trim().uppercase()) {
        "AI_OFFLINE", "OFFLINE" -> offlineAi.generateChatReply(
            selectedModelName = settingsRepository.selectedOfflineModel.value,
            history = history,
            userMessage = userMessage,
            persona = persona,
            memory = memory,
            instructions = instructions
        )
        "AI_OPEN_KEY", "CLOUD" -> cloudAi.queryConversation(
            history = history,
            userMessage = userMessage,
            providerName = settingsRepository.selectedProvider.value,
            modelName = settingsRepository.selectedModel.value,
            customBaseUrl = settingsRepository.customBaseUrl.value,
            persona = persona,
            memory = memory,
            instructions = instructions
        )
        else -> null
    }

    suspend fun validateCloudKey(provider: String, key: String): Result<Boolean> {
        return cloudAi.validateApiKey(provider, key, settingsRepository.customBaseUrl.value)
    }
}
