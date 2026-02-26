package com.voxpen.app.data.repository

import com.voxpen.app.data.model.LlmProvider
import com.voxpen.app.data.model.RefinementPrompt
import com.voxpen.app.data.model.SttLanguage
import com.voxpen.app.data.model.ToneStyle
import com.voxpen.app.data.remote.ChatCompletionApiFactory
import com.voxpen.app.data.remote.ChatCompletionRequest
import com.voxpen.app.data.remote.ChatMessage
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmRepository
    @Inject
    constructor(
        private val apiFactory: ChatCompletionApiFactory,
    ) {
        suspend fun refine(
            text: String,
            language: SttLanguage,
            apiKey: String,
            model: String = LLM_MODEL,
            vocabulary: List<String> = emptyList(),
            customPrompt: String? = null,
            tone: ToneStyle = ToneStyle.Casual,
            provider: LlmProvider = LlmProvider.Groq,
            customBaseUrl: String? = null,
        ): Result<String> {
            if (apiKey.isBlank()) {
                return Result.failure(IllegalStateException("API key not configured"))
            }
            if (text.isBlank()) {
                return Result.failure(IllegalArgumentException("Text is empty"))
            }

            return try {
                val api = if (provider == LlmProvider.Custom && !customBaseUrl.isNullOrBlank()) {
                    apiFactory.createForCustom(customBaseUrl)
                } else {
                    apiFactory.create(provider)
                }
                val systemPrompt = RefinementPrompt.forLanguage(language, vocabulary, customPrompt, tone)
                val request =
                    ChatCompletionRequest(
                        model = model,
                        messages =
                            listOf(
                                ChatMessage(role = "system", content = systemPrompt),
                                ChatMessage(role = "user", content = text),
                            ),
                        temperature = TEMPERATURE,
                        maxTokens = MAX_TOKENS,
                    )
                val response = api.chatCompletion("Bearer $apiKey", request)
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
