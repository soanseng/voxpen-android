package com.voxpen.app.data.repository

import com.voxpen.app.data.model.LlmProvider
import com.voxpen.app.data.model.RefinementPrompt
import com.voxpen.app.data.model.SttLanguage
import com.voxpen.app.data.model.ToneStyle
import com.voxpen.app.data.model.TranslationPrompt
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
            translationEnabled: Boolean = false,
            targetLanguage: SttLanguage = SttLanguage.English,
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
                val systemPrompt = if (translationEnabled) {
                    TranslationPrompt.build(language, targetLanguage)
                } else {
                    RefinementPrompt.forLanguage(language, vocabulary, customPrompt, tone)
                }
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
                        reasoningFormat = reasoningFormatFor(model),
                    )
                val response = api.chatCompletion("Bearer $apiKey", request)
                val raw =
                    response.choices.firstOrNull()?.message?.content
                        ?: return Result.failure(IllegalStateException("No response content"))
                Result.success(stripThinkingTags(raw))
            } catch (e: IOException) {
                Result.failure(e)
            } catch (e: retrofit2.HttpException) {
                Result.failure(e)
            }
        }

        /** Sends a fully composed user message to the LLM and returns the response. Used for speak-to-edit. */
        suspend fun editText(
            userMessage: String,
            apiKey: String,
            model: String = LLM_MODEL,
            provider: LlmProvider = LlmProvider.Groq,
            customBaseUrl: String? = null,
        ): Result<String> {
            if (apiKey.isBlank()) return Result.failure(IllegalStateException("API key not configured"))
            if (userMessage.isBlank()) return Result.failure(IllegalArgumentException("Message is empty"))

            return try {
                val api = if (provider == LlmProvider.Custom && !customBaseUrl.isNullOrBlank()) {
                    apiFactory.createForCustom(customBaseUrl)
                } else {
                    apiFactory.create(provider)
                }
                val request = ChatCompletionRequest(
                    model = model,
                    messages = listOf(ChatMessage(role = "user", content = userMessage)),
                    temperature = TEMPERATURE,
                    maxTokens = MAX_TOKENS,
                    reasoningFormat = reasoningFormatFor(model),
                )
                val response = api.chatCompletion("Bearer $apiKey", request)
                val raw = response.choices.firstOrNull()?.message?.content
                    ?: return Result.failure(IllegalStateException("No response content"))
                Result.success(stripThinkingTags(raw))
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

            private val THINKING_TAG_REGEX = Regex("<think>[\\s\\S]*?</think>\\s*")

            /** Returns "hidden" for known thinking models, null otherwise. */
            fun reasoningFormatFor(model: String): String? =
                if (model.contains("qwen3", ignoreCase = true) ||
                    model.contains("deepseek-r1", ignoreCase = true)
                ) {
                    "hidden"
                } else {
                    null
                }

            /** Strips `<think>…</think>` blocks from LLM output (safety net for custom models). */
            fun stripThinkingTags(text: String): String =
                THINKING_TAG_REGEX.replace(text, "").trim()
        }
    }
