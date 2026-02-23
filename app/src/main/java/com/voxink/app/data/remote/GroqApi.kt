package com.voxink.app.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface GroqApi {
    @Multipart
    @POST("openai/v1/audio/transcriptions")
    suspend fun transcribe(
        @Header("Authorization") authorization: String,
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody,
        @Part("response_format") responseFormat: RequestBody,
        @Part("language") language: RequestBody? = null,
        @Part("prompt") prompt: RequestBody? = null,
    ): WhisperResponse

    @POST("openai/v1/chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest,
    ): ChatCompletionResponse
}
