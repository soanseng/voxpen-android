package com.voxink.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.voxink.app.data.model.SttLanguage
import com.voxink.app.data.remote.GroqApi
import com.voxink.app.data.repository.LlmRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class RefineTextUseCaseTest {
    private lateinit var server: MockWebServer
    private lateinit var api: GroqApi
    private lateinit var repository: LlmRepository
    private lateinit var useCase: RefineTextUseCase

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
        useCase = RefineTextUseCase(repository)
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
                        """{"id":"c1","choices":[{"index":0,"message":{"role":"assistant","content":"Clean text"}}]}""",
                    )
                    .setHeader("Content-Type", "application/json"),
            )

            val result = useCase("um raw text", SttLanguage.English, "key")

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isEqualTo("Clean text")
        }

    @Test
    fun `should propagate failure from repository`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(500))

            val result = useCase("text", SttLanguage.Auto, "key")

            assertThat(result.isFailure).isTrue()
        }

    @Test
    fun `should fail for blank text`() =
        runTest {
            val result = useCase("", SttLanguage.English, "key")

            assertThat(result.isFailure).isTrue()
        }
}
