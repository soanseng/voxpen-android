package com.voxink.app.data.repository

import com.google.common.truth.Truth.assertThat
import com.voxink.app.data.model.SttLanguage
import com.voxink.app.data.remote.GroqApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType

class LlmRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var api: GroqApi
    private lateinit var repository: LlmRepository

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true }
        api =
            Retrofit.Builder()
                .baseUrl(server.url("/"))
                .client(OkHttpClient())
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(GroqApi::class.java)
        repository = LlmRepository(api)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `should return refined text on success`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setBody(
                        """{"id":"c1","choices":[{"index":0,"message":{"role":"assistant","content":"Polished text"}}]}""",
                    )
                    .setHeader("Content-Type", "application/json"),
            )

            val result = repository.refine("raw text", SttLanguage.English, "test-key")

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isEqualTo("Polished text")
        }

    @Test
    fun `should send Bearer authorization header`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setBody(
                        """{"id":"c2","choices":[{"index":0,"message":{"role":"assistant","content":"ok"}}]}""",
                    )
                    .setHeader("Content-Type", "application/json"),
            )

            repository.refine("text", SttLanguage.Auto, "my-api-key")

            val request = server.takeRequest()
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer my-api-key")
        }

    @Test
    fun `should include system prompt and user text in request body`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setBody(
                        """{"id":"c3","choices":[{"index":0,"message":{"role":"assistant","content":"ok"}}]}""",
                    )
                    .setHeader("Content-Type", "application/json"),
            )

            repository.refine("test input", SttLanguage.Chinese, "key")

            val request = server.takeRequest()
            val body = request.body.readUtf8()
            assertThat(body).contains("\"role\":\"system\"")
            assertThat(body).contains("\"role\":\"user\"")
            assertThat(body).contains("test input")
            assertThat(body).contains("繁體中文")
        }

    @Test
    fun `should return failure on empty API key`() =
        runTest {
            val result = repository.refine("text", SttLanguage.Auto, "")
            assertThat(result.isFailure).isTrue()
        }

    @Test
    fun `should return failure on empty text`() =
        runTest {
            val result = repository.refine("", SttLanguage.Auto, "key")
            assertThat(result.isFailure).isTrue()
        }

    @Test
    fun `should return failure on server error`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(500))

            val result = repository.refine("text", SttLanguage.English, "key")

            assertThat(result.isFailure).isTrue()
        }
}
