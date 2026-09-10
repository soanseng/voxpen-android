package com.voxpen.app.domain.usecase

import com.voxpen.app.data.model.LlmProvider
import com.voxpen.app.data.model.SttLanguage
import com.voxpen.app.data.model.ToneStyle
import com.voxpen.app.data.repository.LlmRepository
import com.voxpen.app.data.repository.TranscriptionSegment
import javax.inject.Inject

class RefineSegmentsUseCase
    @Inject
    constructor(
        private val llmRepository: LlmRepository,
    ) {
        suspend operator fun invoke(
            segments: List<TranscriptionSegment>,
            language: SttLanguage,
            apiKey: String,
            model: String = "llama-3.3-70b-versatile",
            vocabulary: List<String> = emptyList(),
            customPrompt: String? = null,
            tone: ToneStyle = ToneStyle.Casual,
            provider: LlmProvider = LlmProvider.Groq,
            customBaseUrl: String? = null,
            translationEnabled: Boolean = false,
            targetLanguage: SttLanguage = SttLanguage.English,
        ): Result<List<TranscriptionSegment>> =
            llmRepository.refineSegments(
                segments, language, apiKey, model, vocabulary, customPrompt, tone, provider, customBaseUrl,
                translationEnabled, targetLanguage,
            )
    }
