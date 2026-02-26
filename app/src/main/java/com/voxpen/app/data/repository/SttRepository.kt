package com.voxpen.app.data.repository

import com.voxpen.app.data.model.SttLanguage
import com.voxpen.app.data.remote.GroqApi
import com.voxpen.app.data.remote.SttApiFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SttRepository
    @Inject
    constructor(
        private val groqApi: GroqApi,
        private val sttApiFactory: SttApiFactory,
    ) {
        suspend fun transcribe(
            wavBytes: ByteArray,
            language: SttLanguage,
            apiKey: String,
            model: String = WHISPER_MODEL,
            vocabularyHint: String? = null,
            customSttBaseUrl: String? = null,
        ): Result<String> {
            if (apiKey.isBlank()) {
                return Result.failure(IllegalStateException("API key not configured"))
            }

            val api =
                if (!customSttBaseUrl.isNullOrBlank()) {
                    sttApiFactory.createForCustom(customSttBaseUrl)
                } else {
                    groqApi
                }

            return try {
                val filePart =
                    MultipartBody.Part.createFormData(
                        "file",
                        "recording.wav",
                        wavBytes.toRequestBody("audio/wav".toMediaType()),
                    )
                val modelBody = model.toRequestBody(TEXT_PLAIN)
                val format = RESPONSE_FORMAT.toRequestBody(TEXT_PLAIN)
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

                Result.success(response.text)
            } catch (e: IOException) {
                Result.failure(e)
            }
        }

        companion object {
            private const val WHISPER_MODEL = "whisper-large-v3-turbo"
            private const val RESPONSE_FORMAT = "verbose_json"
            private val TEXT_PLAIN = "text/plain".toMediaType()
        }
    }
