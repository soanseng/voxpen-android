package com.voxink.app.data.model

data class LlmModelOption(
    val id: String,
    val label: String,
    val tag: String? = null,
    val isDefault: Boolean = false,
)

sealed class LlmProvider(
    val key: String,
    val baseUrl: String,
    val models: List<LlmModelOption>,
) {
    val defaultModelId: String
        get() = models.firstOrNull { it.isDefault }?.id ?: models.firstOrNull()?.id ?: ""

    data object Groq : LlmProvider(
        key = "groq",
        baseUrl = "https://api.groq.com/openai/",
        models = listOf(
            LlmModelOption("openai/gpt-oss-120b", "GPT-OSS 120B", tag = "recommended", isDefault = true),
            LlmModelOption("openai/gpt-oss-20b", "GPT-OSS 20B", tag = "fast"),
            LlmModelOption("qwen/qwen3-32b", "Qwen3 32B", tag = "best_chinese"),
            LlmModelOption("llama-3.3-70b-versatile", "LLaMA 3.3 70B"),
        ),
    )

    data object OpenAI : LlmProvider(
        key = "openai",
        baseUrl = "https://api.openai.com/",
        models = listOf(
            LlmModelOption("gpt-4o-mini", "GPT-4o Mini", tag = "recommended", isDefault = true),
            LlmModelOption("gpt-4.1-nano", "GPT-4.1 Nano", tag = "cheapest"),
            LlmModelOption("gpt-4.1-mini", "GPT-4.1 Mini"),
        ),
    )

    data object OpenRouter : LlmProvider(
        key = "openrouter",
        baseUrl = "https://openrouter.ai/api/",
        models = listOf(
            LlmModelOption("google/gemini-2.0-flash-001", "Gemini 2.0 Flash", tag = "recommended", isDefault = true),
            LlmModelOption("anthropic/claude-3.5-haiku", "Claude 3.5 Haiku", tag = "quality"),
            LlmModelOption("deepseek/deepseek-chat", "DeepSeek Chat", tag = "cheapest"),
        ),
    )

    data object Custom : LlmProvider(
        key = "custom",
        baseUrl = "",
        models = emptyList(),
    )

    companion object {
        val DEFAULT: LlmProvider get() = Groq
        val all: List<LlmProvider> get() = listOf(Groq, OpenAI, OpenRouter, Custom)

        fun fromKey(key: String): LlmProvider =
            when (key) {
                "groq" -> Groq
                "openai" -> OpenAI
                "openrouter" -> OpenRouter
                "custom" -> Custom
                else -> DEFAULT
            }
    }
}
