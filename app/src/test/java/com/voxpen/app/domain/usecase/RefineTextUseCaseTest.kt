package com.voxpen.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.voxpen.app.data.model.LlmProvider
import com.voxpen.app.data.model.SttLanguage
import com.voxpen.app.data.remote.ChatCompletionApiFactory
import com.voxpen.app.data.repository.LlmRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RefineTextUseCaseTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: LlmRepository
    private lateinit var useCase: RefineTextUseCase

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true }
        val factory = ChatCompletionApiFactory(OkHttpClient(), json)
        repository = LlmRepository(factory)
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

            val result = useCase(
                "um raw text", SttLanguage.English, "key",
                provider = LlmProvider.Custom,
                customBaseUrl = server.url("/").toString(),
            )

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isEqualTo("Clean text")
        }

    @Test
    fun `should propagate failure from repository`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(500))

            val result = useCase(
                "text", SttLanguage.Auto, "key",
                provider = LlmProvider.Custom,
                customBaseUrl = server.url("/").toString(),
            )

            assertThat(result.isFailure).isTrue()
        }

    @Test
    fun `should fail for blank text`() =
        runTest {
            val result = useCase("", SttLanguage.English, "key")

            assertThat(result.isFailure).isTrue()
        }
}
