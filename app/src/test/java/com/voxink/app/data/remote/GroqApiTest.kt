package com.voxink.app.data.remote

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class GroqApiTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: GroqApi

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val json = Json { ignoreUnknownKeys = true }
        api = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GroqApi::class.java)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `should send transcription request with correct headers and parts`() = runTest {
        val responseJson = """{"text": "你好"}"""
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(responseJson)
                .addHeader("Content-Type", "application/json"),
        )

        val fakeAudio = ByteArray(100) { 0 }
        val filePart = MultipartBody.Part.createFormData(
            "file",
            "recording.wav",
            fakeAudio.toRequestBody("audio/wav".toMediaType()),
        )
        val model = "whisper-large-v3-turbo".toRequestBody("text/plain".toMediaType())
        val format = "verbose_json".toRequestBody("text/plain".toMediaType())
        val language = "zh".toRequestBody("text/plain".toMediaType())
        val prompt = "繁體中文轉錄。".toRequestBody("text/plain".toMediaType())

        val response = api.transcribe(
            authorization = "Bearer test-key",
            file = filePart,
            model = model,
            responseFormat = format,
            language = language,
            prompt = prompt,
        )

        assertThat(response.text).isEqualTo("你好")

        val request = mockWebServer.takeRequest()
        assertThat(request.path).isEqualTo("/openai/v1/audio/transcriptions")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-key")
        assertThat(request.getHeader("Content-Type")).contains("multipart/form-data")
    }

    @Test
    fun `should handle auto-detect with null language`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"text": "hello world"}""")
                .addHeader("Content-Type", "application/json"),
        )

        val filePart = MultipartBody.Part.createFormData(
            "file",
            "recording.wav",
            ByteArray(50).toRequestBody("audio/wav".toMediaType()),
        )

        val response = api.transcribe(
            authorization = "Bearer key",
            file = filePart,
            model = "whisper-large-v3-turbo".toRequestBody("text/plain".toMediaType()),
            responseFormat = "verbose_json".toRequestBody("text/plain".toMediaType()),
            language = null,
            prompt = null,
        )

        assertThat(response.text).isEqualTo("hello world")
    }
}
