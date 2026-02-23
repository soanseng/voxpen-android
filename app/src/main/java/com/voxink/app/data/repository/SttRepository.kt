package com.voxink.app.data.repository

import com.voxink.app.data.model.SttLanguage
import com.voxink.app.data.remote.GroqApi
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
    ) {
        suspend fun transcribe(
            wavBytes: ByteArray,
            language: SttLanguage,
            apiKey: String,
        ): Result<String> {
            if (apiKey.isBlank()) {
                return Result.failure(IllegalStateException("API key not configured"))
            }

            return try {
                val filePart =
                    MultipartBody.Part.createFormData(
                        "file",
                        "recording.wav",
                        wavBytes.toRequestBody("audio/wav".toMediaType()),
                    )
                val model = WHISPER_MODEL.toRequestBody(TEXT_PLAIN)
                val format = RESPONSE_FORMAT.toRequestBody(TEXT_PLAIN)
                val langBody = language.code?.toRequestBody(TEXT_PLAIN)
                val promptBody = language.prompt.toRequestBody(TEXT_PLAIN)

                val response =
                    groqApi.transcribe(
                        authorization = "Bearer $apiKey",
                        file = filePart,
                        model = model,
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
