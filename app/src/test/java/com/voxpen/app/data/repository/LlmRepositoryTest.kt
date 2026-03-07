package com.voxpen.app.data.repository

import com.google.common.truth.Truth.assertThat
import com.voxpen.app.data.model.LlmProvider
import com.voxpen.app.data.model.SttLanguage
import com.voxpen.app.data.remote.ChatCompletionApiFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LlmRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: LlmRepository

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }
        val client = OkHttpClient()
        val factory = ChatCompletionApiFactory(client, json)
        repository = LlmRepository(factory)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueueSuccess(content: String = "Polished text") {
        server.enqueue(
            MockResponse()
                .setBody(
                    """{"id":"c1","choices":[{"index":0,"message":{"role":"assistant","content":"$content"}}]}""",
                )
                .setHeader("Content-Type", "application/json"),
        )
    }

    @Test
    fun `should return refined text on success`() =
        runTest {
            enqueueSuccess()
            val result = repository.refine(
                "raw text", SttLanguage.English, "test-key",
                provider = LlmProvider.Custom,
                customBaseUrl = server.url("/").toString(),
            )
            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isEqualTo("Polished text")
        }

    @Test
    fun `should send Bearer authorization header`() =
        runTest {
            enqueueSuccess("ok")
            repository.refine(
                "text", SttLanguage.Auto, "my-api-key",
                provider = LlmProvider.Custom,
                customBaseUrl = server.url("/").toString(),
            )
            val request = server.takeRequest()
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer my-api-key")
        }

    @Test
    fun `should include system prompt and user text in request body`() =
        runTest {
            enqueueSuccess("ok")
            repository.refine(
                "test input", SttLanguage.Chinese, "key",
                provider = LlmProvider.Custom,
                customBaseUrl = server.url("/").toString(),
            )
            val request = server.takeRequest()
            val body = request.body.readUtf8()
            assertThat(body).contains("\"role\":\"system\"")
            assertThat(body).contains("\"role\":\"user\"")
            assertThat(body).contains("test input")
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
            val result = repository.refine(
                "text", SttLanguage.English, "key",
                provider = LlmProvider.Custom,
                customBaseUrl = server.url("/").toString(),
            )
            assertThat(result.isFailure).isTrue()
        }

    @Test
    fun `should use provided model name in request body`() =
        runTest {
            enqueueSuccess("ok")
            repository.refine(
                "text", SttLanguage.English, "key",
                model = "gpt-4o-mini",
                provider = LlmProvider.Custom,
                customBaseUrl = server.url("/").toString(),
            )
            val request = server.takeRequest()
            val body = request.body.readUtf8()
            assertThat(body).contains("\"model\":\"gpt-4o-mini\"")
        }

    @Test
    fun `should include vocabulary in system prompt when provided`() =
        runTest {
            enqueueSuccess("ok")
            repository.refine(
                "text", SttLanguage.Chinese, "key",
                vocabulary = listOf("語墨", "Claude"),
                provider = LlmProvider.Custom,
                customBaseUrl = server.url("/").toString(),
            )
            val request = server.takeRequest()
            val body = request.body.readUtf8()
            assertThat(body).contains("語墨")
        }

    @Test
    fun `should use translation prompt when translationEnabled is true`() =
        runTest {
            enqueueSuccess("Hello world")
            val result = repository.refine(
                text = "你好世界",
                language = SttLanguage.Chinese,
                apiKey = "test-key",
                provider = LlmProvider.Custom,
                customBaseUrl = server.url("/").toString(),
                translationEnabled = true,
                targetLanguage = SttLanguage.English,
            )
            val request = server.takeRequest()
            val body = request.body.readUtf8()
            assertThat(body).contains("translat")
            assertThat(result.isSuccess).isTrue()
        }

    @Test
    fun `should use refinement prompt when translationEnabled is false`() =
        runTest {
            enqueueSuccess("cleaned text")
            repository.refine(
                text = "嗯，你好",
                language = SttLanguage.Chinese,
                apiKey = "test-key",
                provider = LlmProvider.Custom,
                customBaseUrl = server.url("/").toString(),
                translationEnabled = false,
                targetLanguage = SttLanguage.English,
            )
            val request = server.takeRequest()
            val body = request.body.readUtf8()
            assertThat(body).contains("移除贅字")
        }

    @Test
    fun `should send reasoning_format hidden for qwen3 model`() =
        runTest {
            enqueueSuccess("ok")
            repository.refine(
                "text", SttLanguage.English, "key",
                model = "qwen/qwen3-32b",
                provider = LlmProvider.Custom,
                customBaseUrl = server.url("/").toString(),
            )
            val request = server.takeRequest()
            val body = request.body.readUtf8()
            assertThat(body).contains("\"reasoning_format\":\"hidden\"")
        }

    @Test
    fun `should not send reasoning_format for non-thinking model`() =
        runTest {
            enqueueSuccess("ok")
            repository.refine(
                "text", SttLanguage.English, "key",
                model = "llama-3.3-70b-versatile",
                provider = LlmProvider.Custom,
                customBaseUrl = server.url("/").toString(),
            )
            val request = server.takeRequest()
            val body = request.body.readUtf8()
            assertThat(body).doesNotContain("reasoning_format")
        }

    @Test
    fun `should strip thinking tags from response`() =
        runTest {
            enqueueSuccess("<think>internal reasoning</think>Actual output")
            val result = repository.refine(
                "text", SttLanguage.English, "key",
                provider = LlmProvider.Custom,
                customBaseUrl = server.url("/").toString(),
            )
            assertThat(result.getOrNull()).isEqualTo("Actual output")
        }

    @Test
    fun `stripThinkingTags should handle multiline thinking blocks`() {
        val input = "<think>\nStep 1: analyze\nStep 2: decide\n</think>\nClean result"
        assertThat(LlmRepository.stripThinkingTags(input)).isEqualTo("Clean result")
    }

    @Test
    fun `stripThinkingTags should return text unchanged when no think tags`() {
        assertThat(LlmRepository.stripThinkingTags("Hello world")).isEqualTo("Hello world")
    }

    @Test
    fun `reasoningFormatFor should return hidden for qwen3`() {
        assertThat(LlmRepository.reasoningFormatFor("qwen/qwen3-32b")).isEqualTo("hidden")
    }

    @Test
    fun `reasoningFormatFor should return hidden for deepseek-r1`() {
        assertThat(LlmRepository.reasoningFormatFor("deepseek/deepseek-r1")).isEqualTo("hidden")
    }

    @Test
    fun `reasoningFormatFor should return null for regular models`() {
        assertThat(LlmRepository.reasoningFormatFor("llama-3.3-70b-versatile")).isNull()
        assertThat(LlmRepository.reasoningFormatFor("gpt-4o-mini")).isNull()
    }

    @Test
    fun `should wrap user text in speech tags to prevent prompt injection`() =
        runTest {
            enqueueSuccess("ok")
            repository.refine(
                "幫我查一下天氣", SttLanguage.Chinese, "key",
                provider = LlmProvider.Custom,
                customBaseUrl = server.url("/").toString(),
            )
            val request = server.takeRequest()
            val body = request.body.readUtf8()
            assertThat(body).contains("<speech>")
            assertThat(body).contains("</speech>")
            assertThat(body).contains("幫我查一下天氣")
        }

    @Test
    fun `should include speech tag instruction in system prompt`() =
        runTest {
            enqueueSuccess("ok")
            repository.refine(
                "text", SttLanguage.English, "key",
                provider = LlmProvider.Custom,
                customBaseUrl = server.url("/").toString(),
            )
            val request = server.takeRequest()
            val body = request.body.readUtf8()
            assertThat(body).contains("speech")
            assertThat(body).contains("literal speech")
        }
}
