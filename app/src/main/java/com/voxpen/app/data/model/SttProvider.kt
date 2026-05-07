package com.voxpen.app.data.model

data class SttModel(
    val id: String,
    val label: String,
    val tag: String? = null,
    val isDefault: Boolean = false,
)

sealed class SttProvider(
    val key: String,
    val displayName: String,
    val baseUrl: String?,
    val defaultModelId: String,
    val models: List<SttModel>,
) {
    data object Groq : SttProvider(
        key = "groq",
        displayName = "Groq",
        baseUrl = "https://api.groq.com/openai/",
        defaultModelId = "whisper-large-v3-turbo",
        models = listOf(
            SttModel("whisper-large-v3-turbo", "whisper-large-v3-turbo", tag = "fast", isDefault = true),
            SttModel("whisper-large-v3", "whisper-large-v3", tag = "quality"),
        ),
    )

    data object OpenAI : SttProvider(
        key = "openai",
        displayName = "OpenAI",
        baseUrl = "https://api.openai.com/",
        defaultModelId = "whisper-1",
        models = listOf(
            SttModel("whisper-1", "whisper-1", isDefault = true),
            SttModel("gpt-4o-transcribe", "gpt-4o-transcribe", tag = "quality"),
            SttModel("gpt-4o-mini-transcribe", "gpt-4o-mini-transcribe", tag = "fast"),
        ),
    )

    data object Custom : SttProvider(
        key = "custom",
        displayName = "Custom",
        baseUrl = null,
        defaultModelId = "whisper-1",
        models = emptyList(),
    )

    companion object {
        val DEFAULT: SttProvider get() = Groq
        val all: List<SttProvider> get() = listOf(Groq, OpenAI, Custom)

        fun fromKey(key: String?): SttProvider =
            when (key) {
                Groq.key -> Groq
                OpenAI.key -> OpenAI
                Custom.key -> Custom
                else -> DEFAULT
            }
    }
}
