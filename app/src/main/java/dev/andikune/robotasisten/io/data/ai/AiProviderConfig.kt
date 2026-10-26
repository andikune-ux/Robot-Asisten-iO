package dev.andikune.robotasisten.io.data.ai

/**
 * Metadata representation of supported cloud AI providers.
 */
data class ProviderModelInfo(
    val id: String,
    val displayName: String,
    val contextLength: String = "128k",
    val description: String = "Fast & accurate"
)

data class AiProviderItem(
    val id: String,
    val name: String,
    val baseUrl: String,
    val defaultModel: String,
    val models: List<ProviderModelInfo>,
    val isCustom: Boolean = false
)

object AiProviderConfig {
    val supportedProviders: List<AiProviderItem> = listOf(
        AiProviderItem(
            id = "OpenAI",
            name = "OpenAI",
            baseUrl = "https://api.openai.com/v1/",
            defaultModel = "gpt-4o-mini",
            models = listOf(
                ProviderModelInfo("gpt-4o-mini", "GPT-4o Mini", "128k", "Recommended for low cost & high speed"),
                ProviderModelInfo("gpt-4o", "GPT-4o", "128k", "Full flagship model"),
                ProviderModelInfo("gpt-3.5-turbo", "GPT-3.5 Turbo", "16k", "Legacy fast model")
            )
        ),
        AiProviderItem(
            id = "Google Gemini",
            name = "Google Gemini",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/",
            defaultModel = "gemini-1.5-flash",
            models = listOf(
                ProviderModelInfo("gemini-1.5-flash", "Gemini 1.5 Flash", "1M", "Extremely fast & massive context"),
                ProviderModelInfo("gemini-1.5-pro", "Gemini 1.5 Pro", "2M", "Highest reasoning quality"),
                ProviderModelInfo("gemini-2.0-flash", "Gemini 2.0 Flash", "1M", "Next-gen experimental")
            )
        ),
        AiProviderItem(
            id = "Groq",
            name = "Groq",
            baseUrl = "https://api.groq.com/openai/v1/",
            defaultModel = "llama-3.3-70b-versatile",
            models = listOf(
                ProviderModelInfo("llama-3.3-70b-versatile", "Llama 3.3 70B", "128k", "Ultra-fast inference LPUs"),
                ProviderModelInfo("llama-3.1-8b-instant", "Llama 3.1 8B", "128k", "Instant speed"),
                ProviderModelInfo("mixtral-8x7b-32768", "Mixtral 8x7B", "32k", "MoE high efficiency")
            )
        ),
        AiProviderItem(
            id = "DeepSeek",
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com/v1/",
            defaultModel = "deepseek-chat",
            models = listOf(
                ProviderModelInfo("deepseek-chat", "DeepSeek-V3", "64k", "Great reasoning at very low cost"),
                ProviderModelInfo("deepseek-reasoner", "DeepSeek-R1", "64k", "CoT reasoning model")
            )
        ),
        AiProviderItem(
            id = "Anthropic Claude",
            name = "Anthropic Claude",
            baseUrl = "https://api.anthropic.com/v1/",
            defaultModel = "claude-3-5-haiku-20241022",
            models = listOf(
                ProviderModelInfo("claude-3-5-haiku-20241022", "Claude 3.5 Haiku", "200k", "Fastest Claude"),
                ProviderModelInfo("claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet", "200k", "State of the art intelligence")
            )
        ),
        AiProviderItem(
            id = "OpenRouter",
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1/",
            defaultModel = "meta-llama/llama-3.2-3b-instruct:free",
            models = listOf(
                ProviderModelInfo("meta-llama/llama-3.2-3b-instruct:free", "Llama 3.2 3B (Free)", "131k", "Community free tier"),
                ProviderModelInfo("google/gemini-flash-1.5", "Gemini Flash 1.5", "1M", "OpenRouter aggregator")
            )
        ),
        AiProviderItem(
            id = "Mistral",
            name = "Mistral AI",
            baseUrl = "https://api.mistral.ai/v1/",
            defaultModel = "mistral-small-latest",
            models = listOf(
                ProviderModelInfo("mistral-small-latest", "Mistral Small", "32k", "Fast & cost-effective"),
                ProviderModelInfo("mistral-large-latest", "Mistral Large", "128k", "Top-tier capability")
            )
        ),
        AiProviderItem(
            id = "Together AI",
            name = "Together AI",
            baseUrl = "https://api.together.xyz/v1/",
            defaultModel = "meta-llama/Meta-Llama-3.1-8B-Instruct-Turbo",
            models = listOf(
                ProviderModelInfo("meta-llama/Meta-Llama-3.1-8B-Instruct-Turbo", "Llama 3.1 8B Turbo", "128k", "Fast serverless inference")
            )
        ),
        AiProviderItem(
            id = "Fireworks AI",
            name = "Fireworks AI",
            baseUrl = "https://api.fireworks.ai/inference/v1/",
            defaultModel = "accounts/fireworks/models/llama-v3p1-8b-instruct",
            models = listOf(
                ProviderModelInfo("accounts/fireworks/models/llama-v3p1-8b-instruct", "Llama 3.1 8B", "128k", "Optimized inference")
            )
        ),
        AiProviderItem(
            id = "Custom",
            name = "Custom (OpenAI-Compatible)",
            baseUrl = "http://localhost:11434/v1/",
            defaultModel = "custom-model",
            models = listOf(
                ProviderModelInfo("custom-model", "Custom Model", "32k", "User specified endpoint")
            ),
            isCustom = true
        )
    )

    fun getProvider(id: String): AiProviderItem {
        return supportedProviders.find { it.id.equals(id, ignoreCase = true) }
            ?: supportedProviders.first()
    }
}
