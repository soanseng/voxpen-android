package com.voxpen.app.data.repository

import com.google.common.truth.Truth.assertThat
import com.voxpen.app.data.model.LlmProvider
import com.voxpen.app.data.model.SttLanguage
import com.voxpen.app.data.remote.ChatCompletionApiFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import kotlinx.serialization.encodeToString
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

    /** Enqueues a chat response whose content is JSON-encoded (handles multi-line content). */
    private fun enqueueContent(content: String) {
        val json = Json { explicitNulls = false }
        server.enqueue(
            MockResponse()
                .setBody(
                    "{\"id\":\"c1\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\"," +
                        "\"content\":${json.encodeToString(content)}}}]}",
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
    fun `should succeed without Bearer authorization when Custom key is blank`() =
        runTest {
            enqueueSuccess("keyless polished")
            val result =
                repository.refine(
                    "text", SttLanguage.Auto, "",
                    provider = LlmProvider.Custom,
                    customBaseUrl = server.url("/").toString(),
                )
            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isEqualTo("keyless polished")
            val request = server.takeRequest()
            assertThat(request.getHeader("Authorization") ?: "").doesNotContain("Bearer")
        }

    @Test
    fun `should proceed for Custom provider with blank key in editText`() =
        runTest {
            enqueueSuccess("edited")
            val result =
                repository.editText(
                    "make it formal", "",
                    provider = LlmProvider.Custom,
                    customBaseUrl = server.url("/").toString(),
                )
            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isEqualTo("edited")
            assertThat(server.takeRequest().getHeader("Authorization") ?: "").doesNotContain("Bearer")
        }

    @Test
    fun `should scale max tokens for long input text`() =
        runTest {
            enqueueSuccess("ok")
            repository.refine(
                "a".repeat(10_000), SttLanguage.Auto, "key",
                provider = LlmProvider.Custom,
                customBaseUrl = server.url("/").toString(),
            )
            val body = server.takeRequest().body.readUtf8()
            assertThat(body).contains("\"max_tokens\":16384")
        }

    @Test
    fun `should keep default max tokens for short input text`() =
        runTest {
            enqueueSuccess("ok")
            repository.refine(
                "short", SttLanguage.Auto, "key",
                provider = LlmProvider.Custom,
                customBaseUrl = server.url("/").toString(),
            )
            val body = server.takeRequest().body.readUtf8()
            assertThat(body).contains("\"max_tokens\":2048")
        }

    @Test
    fun `should strip echoed outer speech tags from output`() =
        runTest {
            enqueueSuccess("<speech>\ncleaned text\n</speech>")
            val result =
                repository.refine(
                    "raw", SttLanguage.Auto, "key",
                    provider = LlmProvider.Custom,
                    customBaseUrl = server.url("/").toString(),
                )
            assertThat(result.getOrNull()).isEqualTo("cleaned text")
        }

    @Test
    fun `should refine segments preserving original timestamps`() =
        runTest {
            enqueueContent("1|Hello world.\n2|This is a test.")
            val segments =
                listOf(
                    TranscriptionSegment(1000, 2000, "um, hello world"),
                    TranscriptionSegment(3000, 4000, "this is a test"),
                )

            val result =
                repository.refineSegments(
                    segments, SttLanguage.English, "key",
                    provider = LlmProvider.Custom,
                    customBaseUrl = server.url("/").toString(),
                )

            assertThat(result.isSuccess).isTrue()
            val refined = result.getOrThrow()
            assertThat(refined[0].text).isEqualTo("Hello world.")
            assertThat(refined[0].startMs).isEqualTo(1000)
            assertThat(refined[0].endMs).isEqualTo(2000)
            assertThat(refined[1].text).isEqualTo("This is a test.")
            assertThat(refined[1].startMs).isEqualTo(3000)
        }

    @Test
    fun `should keep original text when index missing from response`() =
        runTest {
            enqueueContent("1|Only first refined")
            val segments =
                listOf(
                    TranscriptionSegment(0, 1000, "first cue"),
                    TranscriptionSegment(1000, 2000, "second cue"),
                )

            val result =
                repository.refineSegments(
                    segments, SttLanguage.English, "key",
                    provider = LlmProvider.Custom,
                    customBaseUrl = server.url("/").toString(),
                )

            val refined = result.getOrThrow()
            assertThat(refined[0].text).isEqualTo("Only first refined")
            assertThat(refined[1].text).isEqualTo("second cue")
        }

    @Test
    fun `should fall back positionally for plain-line responses`() =
        runTest {
            enqueueContent("First refined.\nSecond refined.")
            val segments =
                listOf(
                    TranscriptionSegment(0, 1000, "first"),
                    TranscriptionSegment(1000, 2000, "second"),
                )

            val result =
                repository.refineSegments(
                    segments, SttLanguage.English, "key",
                    provider = LlmProvider.Custom,
                    customBaseUrl = server.url("/").toString(),
                )

            val refined = result.getOrThrow()

            assertThat(refined[0].text).isEqualTo("First refined.")
            assertThat(refined[1].text).isEqualTo("Second refined.")
        }

    @Test
    fun `should keep originals when plain line count mismatches`() =
        runTest {
            enqueueContent("Only one line")
            val segments =
                listOf(
                    TranscriptionSegment(0, 1000, "first"),
                    TranscriptionSegment(1000, 2000, "second"),
                )

            val result =
                repository.refineSegments(
                    segments, SttLanguage.English, "key",
                    provider = LlmProvider.Custom,
                    customBaseUrl = server.url("/").toString(),
                )

            val refined = result.getOrThrow()
            assertThat(refined[0].text).isEqualTo("first")
            assertThat(refined[1].text).isEqualTo("second")
        }

    @Test
    fun `should send numbered cue lines wrapped in speech tags`() =
        runTest {
            enqueueContent("1|Refined.")
            val segments = listOf(TranscriptionSegment(0, 1000, "um hello"))

            repository.refineSegments(
                segments, SttLanguage.English, "key",
                provider = LlmProvider.Custom,
                customBaseUrl = server.url("/").toString(),
            )

            val body = server.takeRequest().body.readUtf8()
            assertThat(body).contains("""<speech>\n1|um hello\n</speech>""")
            assertThat(body).contains("refining subtitle cues")
        }

    @Test
    fun `should batch sequentially with global indices`() =
        runTest {
            enqueueContent("41|Forty first refined.")
            enqueueContent("1|First refined.")
            val segments = (1..41).map { i -> TranscriptionSegment(i * 1000L, i * 1000L + 500, "cue $i") }

            val result =
                repository.refineSegments(
                    segments, SttLanguage.English, "key",
                    provider = LlmProvider.Custom,
                    customBaseUrl = server.url("/").toString(),
                )

            assertThat(result.isSuccess).isTrue()
            assertThat(server.requestCount).isEqualTo(2)
            val firstBody = server.takeRequest().body.readUtf8()
            val secondBody = server.takeRequest().body.readUtf8()
            assertThat(firstBody).contains("""1|cue 1\n2|cue 2""")
            assertThat(secondBody).contains("41|cue 41")
        }

    @Test
    fun `should strip code fences from response`() =
        runTest {
            enqueueContent("```\n1|Fenced refined.\n```")
            val segments = listOf(TranscriptionSegment(0, 1000, "original"))

            val result =
                repository.refineSegments(
                    segments, SttLanguage.English, "key",
                    provider = LlmProvider.Custom,
                    customBaseUrl = server.url("/").toString(),
                )

            assertThat(result.getOrThrow()[0].text).isEqualTo("Fenced refined.")
        }

    @Test
    fun `should accept bracketed indices`() =
        runTest {
            enqueueContent("[1] Bracketed refined.")
            val segments = listOf(TranscriptionSegment(0, 1000, "original"))

            val result =
                repository.refineSegments(
                    segments, SttLanguage.English, "key",
                    provider = LlmProvider.Custom,
                    customBaseUrl = server.url("/").toString(),
                )

            assertThat(result.getOrThrow()[0].text).isEqualTo("Bracketed refined.")
        }

    @Test
    fun `should not fall back to colon parsing when line contains pipe`() =
        runTest {
            // "1:30|x" has a pipe, so the non-numeric prefix must reject numbered parsing
            // entirely (desktop parity); the plain-line positional fallback then applies.
            enqueueContent("1:30|Only line")
            val segments = listOf(TranscriptionSegment(0, 1000, "original"))

            val result =
                repository.refineSegments(
                    segments, SttLanguage.English, "key",
                    provider = LlmProvider.Custom,
                    customBaseUrl = server.url("/").toString(),
                )

            assertThat(result.getOrThrow()[0].text).isEqualTo("1:30|Only line")
        }

    @Test
    fun `should skip LLM call when all cue texts are blank`() =
        runTest {
            val segments =
                listOf(
                    TranscriptionSegment(0, 1000, "  "),
                    TranscriptionSegment(1000, 2000, ""),
                )

            val result =
                repository.refineSegments(
                    segments, SttLanguage.English, "key",
                    provider = LlmProvider.Custom,
                    customBaseUrl = server.url("/").toString(),
                )

            assertThat(result.isSuccess).isTrue()
            assertThat(server.requestCount).isEqualTo(0)
            assertThat(result.getOrThrow()[0].text).isEqualTo("  ")
        }

    @Test
    fun `should fail whole call when a batch request fails`() =
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(500).setBody("boom"),
            )
            val segments = listOf(TranscriptionSegment(0, 1000, "original"))

            val result =
                repository.refineSegments(
                    segments, SttLanguage.English, "key",
                    provider = LlmProvider.Custom,
                    customBaseUrl = server.url("/").toString(),
                )

            assertThat(result.isFailure).isTrue()
        }

    @Test
    fun `should proceed keyless for Custom provider in refineSegments`() =
        runTest {
            enqueueContent("1|Keyless refined.")
            val segments = listOf(TranscriptionSegment(0, 1000, "original"))

            val result =
                repository.refineSegments(
                    segments, SttLanguage.English, "",
                    provider = LlmProvider.Custom,
                    customBaseUrl = server.url("/").toString(),
                )

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrThrow()[0].text).isEqualTo("Keyless refined.")
        }

    @Test
    fun `should keep inner speech tag mentions untouched`() =
        runTest {
            enqueueSuccess("talked about <speech> tags inline")
            val result =
                repository.refine(
                    "raw", SttLanguage.Auto, "key",
                    provider = LlmProvider.Custom,
                    customBaseUrl = server.url("/").toString(),
                )
            assertThat(result.getOrNull()).isEqualTo("talked about <speech> tags inline")
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
    fun `should fail gracefully when custom provider has no base URL`() =
        runTest {
            val result = repository.refine(
                "text", SttLanguage.Auto, "key",
                provider = LlmProvider.Custom,
                customBaseUrl = null,
            )
            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()?.message).contains("base URL")
        }

    @Test
    fun `should fail gracefully when custom provider base URL is blank`() =
        runTest {
            val result = repository.editText(
                "user message", "key",
                provider = LlmProvider.Custom,
                customBaseUrl = "   ",
            )
            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()?.message).contains("base URL")
        }

    @Test
    fun `should allow empty API key for custom provider in editText`() =
        runTest {
            enqueueSuccess("ok")
            val result = repository.editText(
                "user message", "",
                provider = LlmProvider.Custom,
                customBaseUrl = server.url("/").toString(),
            )
            assertThat(result.isSuccess).isTrue()
        }

    @Test
    fun `should still fail fast on empty API key for non-custom provider`() =
        runTest {
            val result = repository.editText("user message", "", provider = LlmProvider.Groq)
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
    fun `should hit v1 chat completions when base URL already ends with v1`() =
        runTest {
            enqueueSuccess("ok")
            val baseUrl = server.url("/v1").toString().removeSuffix("/")
            repository.refine(
                "text", SttLanguage.English, "key",
                provider = LlmProvider.Custom,
                customBaseUrl = baseUrl,
            )
            val request = server.takeRequest()
            assertThat(request.path).isEqualTo("/v1/chat/completions")
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
