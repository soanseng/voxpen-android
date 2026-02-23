package com.voxink.app.data.repository

import com.voxink.app.data.model.RefinementPrompt
import com.voxink.app.data.model.SttLanguage
import com.voxink.app.data.remote.ChatCompletionRequest
import com.voxink.app.data.remote.ChatMessage
import com.voxink.app.data.remote.GroqApi
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmRepository
    @Inject
    constructor(
        private val groqApi: GroqApi,
    ) {
        suspend fun refine(
            text: String,
            language: SttLanguage,
            apiKey: String,
        ): Result<String> {
            if (apiKey.isBlank()) {
                return Result.failure(IllegalStateException("API key not configured"))
            }
            if (text.isBlank()) {
                return Result.failure(IllegalArgumentException("Text is empty"))
            }

            return try {
                val systemPrompt = RefinementPrompt.forLanguage(language)
                val request =
                    ChatCompletionRequest(
                        model = LLM_MODEL,
                        messages =
                            listOf(
                                ChatMessage(role = "system", content = systemPrompt),
                                ChatMessage(role = "user", content = text),
                            ),
                        temperature = TEMPERATURE,
                        maxTokens = MAX_TOKENS,
                    )
                val response = groqApi.chatCompletion("Bearer $apiKey", request)
                val content =
                    response.choices.firstOrNull()?.message?.content
                        ?: return Result.failure(IllegalStateException("No response content"))
                Result.success(content)
            } catch (e: IOException) {
                Result.failure(e)
            } catch (e: retrofit2.HttpException) {
                Result.failure(e)
            }
        }

        companion object {
            private const val LLM_MODEL = "llama-3.3-70b-versatile"
            private const val TEMPERATURE = 0.3
            private const val MAX_TOKENS = 2048
        }
    }
