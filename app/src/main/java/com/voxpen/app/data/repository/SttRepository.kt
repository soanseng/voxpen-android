package com.voxpen.app.data.repository

import com.voxpen.app.data.model.SttLanguage
import com.voxpen.app.data.model.SttProvider
import com.voxpen.app.data.remote.SttApi
import com.voxpen.app.data.remote.SttApiFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class SttRepository
    @Inject
    constructor(
        private val sttApiFactory: SttApiFactory,
    ) {
        suspend fun transcribe(
            wavBytes: ByteArray,
            language: SttLanguage,
            apiKey: String,
            model: String = SttProvider.DEFAULT.defaultModelId,
            vocabularyHint: String? = null,
            provider: SttProvider = SttProvider.DEFAULT,
            customSttBaseUrl: String? = null,
        ): Result<TranscriptionResult> {
            if (apiKey.isBlank()) {
                return Result.failure(IllegalStateException("API key not configured"))
            }

            val api =
                if (provider == SttProvider.Custom) {
                    if (customSttBaseUrl.isNullOrBlank()) {
                        return Result.failure(IllegalStateException("Custom STT base URL not configured"))
                    }
                    sttApiFactory.createForCustom(customSttBaseUrl)
                } else {
                    sttApiFactory.createForProvider(provider)
                }

            return runTranscriptionWithRetry(
                api = api,
                wavBytes = wavBytes,
                language = language,
                apiKey = apiKey,
                model = model,
                vocabularyHint = vocabularyHint,
                provider = provider,
            )
        }

        private suspend fun runTranscriptionWithRetry(
            api: SttApi,
            wavBytes: ByteArray,
            language: SttLanguage,
            apiKey: String,
            model: String,
            vocabularyHint: String?,
            provider: SttProvider,
        ): Result<TranscriptionResult> {
            repeat(MAX_ATTEMPTS) { attempt ->
                try {
                    return Result.success(
                        executeTranscription(
                            api = api,
                            wavBytes = wavBytes,
                            language = language,
                            apiKey = apiKey,
                            model = model,
                            vocabularyHint = vocabularyHint,
                            provider = provider,
                        ),
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val lastAttempt = attempt == MAX_ATTEMPTS - 1
                    val retryable = isRetryable(e)
                    logAttemptFailure(e, provider, model, attempt + 1, wavBytes.size, retryable && !lastAttempt)
                    if (lastAttempt || !retryable) {
                        return Result.failure(normalizeError(e, provider))
                    }
                    delay(retryDelayMillis(e))
                }
            }
            return Result.failure(IllegalStateException("Transcription failed"))
        }

        private suspend fun executeTranscription(
            api: SttApi,
            wavBytes: ByteArray,
            language: SttLanguage,
            apiKey: String,
            model: String,
            vocabularyHint: String?,
            provider: SttProvider,
        ): TranscriptionResult {
                val filePart =
                    MultipartBody.Part.createFormData(
                        "file",
                        "recording.wav",
                        wavBytes.toRequestBody("audio/wav".toMediaType()),
                    )
                val modelBody = model.toRequestBody(TEXT_PLAIN)
            val format = responseFormatFor(provider, model).toRequestBody(TEXT_PLAIN)
                val langBody = language.code?.toRequestBody(TEXT_PLAIN)
                val promptBody = (vocabularyHint ?: language.prompt).toRequestBody(TEXT_PLAIN)

                val response =
                    api.transcribe(
                        authorization = "Bearer $apiKey",
                        file = filePart,
                        model = modelBody,
                        responseFormat = format,
                        language = langBody,
                        prompt = promptBody,
                    )

            val segments = response.segments?.map { seg ->
                    TranscriptionSegment(
                        startMs = (seg.start * 1000).toLong(),
                        endMs = (seg.end * 1000).toLong(),
                        text = seg.text,
                    )
                } ?: emptyList()
            return TranscriptionResult(response.text, segments)
        }

        private fun responseFormatFor(
            provider: SttProvider,
            model: String,
        ): String =
            if (provider == SttProvider.OpenAI && model.startsWith("gpt-4o")) {
                "json"
            } else {
                RESPONSE_FORMAT_VERBOSE_JSON
            }

        private fun isRetryable(error: Exception): Boolean =
            when (error) {
                is IOException -> true
                is HttpException -> error.code() == 429 || error.code() in 500..599
                else -> false
            }

        private fun retryDelayMillis(error: Exception): Long {
            val retryAfterSeconds =
                (error as? HttpException)
                    ?.response()
                    ?.headers()
                    ?.get("Retry-After")
                    ?.toLongOrNull()
            return retryAfterSeconds
                ?.times(1000)
                ?.coerceAtMost(MAX_RETRY_DELAY_MILLIS)
                ?: DEFAULT_RETRY_DELAY_MILLIS
        }

        private fun normalizeError(
            error: Exception,
            provider: SttProvider,
        ): Exception {
            val message =
                when (error) {
                    is HttpException -> {
                        val body = error.response()?.errorBody()?.string()?.take(MAX_ERROR_CHARS)
                        "${provider.displayName} STT failed (${error.code()}): ${body ?: error.message()}"
                    }
                    else -> "${provider.displayName} STT failed: ${error.message ?: error::class.java.simpleName}"
                }
            return IOException(message, error)
        }

        private fun logAttemptFailure(
            error: Exception,
            provider: SttProvider,
            model: String,
            attempt: Int,
            bytes: Int,
            willRetry: Boolean,
        ) {
            val status = (error as? HttpException)?.code()
            Timber.w(
                error,
                "stt_failed provider=%s model=%s status=%s attempt=%d bytes=%d retry=%s",
                provider.key,
                model,
                status ?: "n/a",
                attempt,
                bytes,
                willRetry,
            )
        }

        companion object {
            private const val RESPONSE_FORMAT_VERBOSE_JSON = "verbose_json"
            private const val MAX_ATTEMPTS = 2
            private const val DEFAULT_RETRY_DELAY_MILLIS = 0L
            private const val MAX_RETRY_DELAY_MILLIS = 3_000L
            private const val MAX_ERROR_CHARS = 500
            private val TEXT_PLAIN = "text/plain".toMediaType()
        }
    }
