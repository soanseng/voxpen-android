package com.voxink.app.domain.usecase

import com.voxink.app.data.model.SttLanguage
import com.voxink.app.data.model.ToneStyle
import com.voxink.app.data.repository.LlmRepository
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
        ): Result<String> = llmRepository.refine(text, language, apiKey, model, vocabulary, customPrompt, tone)
    }
