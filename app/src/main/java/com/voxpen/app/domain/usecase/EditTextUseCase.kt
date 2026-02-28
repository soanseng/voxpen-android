package com.voxpen.app.domain.usecase

import com.voxpen.app.data.model.EditPrompt
import com.voxpen.app.data.model.LlmProvider
import com.voxpen.app.data.model.SttLanguage
import com.voxpen.app.data.repository.LlmRepository
import javax.inject.Inject

class EditTextUseCase
    @Inject
    constructor(
        private val llmRepository: LlmRepository,
    ) {
        suspend operator fun invoke(
            selectedText: String,
            instruction: String,
            language: SttLanguage,
            apiKey: String,
            model: String = "llama-3.3-70b-versatile",
            provider: LlmProvider = LlmProvider.Groq,
            customBaseUrl: String? = null,
        ): Result<String> {
            if (apiKey.isBlank()) return Result.failure(IllegalStateException("API key not configured"))
            if (selectedText.isBlank()) return Result.failure(IllegalArgumentException("No text selected"))

            val userMessage = EditPrompt.build(selectedText, instruction, language)
            return llmRepository.editText(userMessage, apiKey, model, provider, customBaseUrl)
        }
    }
