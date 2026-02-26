package com.voxpen.app.domain.usecase

import com.voxpen.app.data.model.LlmProvider
import com.voxpen.app.data.model.SttLanguage
import com.voxpen.app.data.model.ToneStyle
import com.voxpen.app.data.repository.LlmRepository
import javax.inject.Inject

class RefineTextUseCase
    @Inject
    constructor(
        private val llmRepository: LlmRepository,
    ) {
        suspend operator fun invoke(
            text: String,
            language: SttLanguage,
            apiKey: String,
            model: String = "llama-3.3-70b-versatile",
            vocabulary: List<String> = emptyList(),
            customPrompt: String? = null,
            tone: ToneStyle = ToneStyle.Casual,
            provider: LlmProvider = LlmProvider.Groq,
            customBaseUrl: String? = null,
        ): Result<String> = llmRepository.refine(
            text, language, apiKey, model, vocabulary, customPrompt, tone, provider, customBaseUrl,
        )
    }
