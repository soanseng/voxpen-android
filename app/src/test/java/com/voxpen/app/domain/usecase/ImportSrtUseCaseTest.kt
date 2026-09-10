package com.voxpen.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.voxpen.app.data.model.LlmProvider
import com.voxpen.app.data.model.SttLanguage
import com.voxpen.app.data.remote.ChatChoice
import com.voxpen.app.data.remote.ChatCompletionApi
import com.voxpen.app.data.remote.ChatCompletionApiFactory
import com.voxpen.app.data.remote.ChatCompletionResponse
import com.voxpen.app.data.remote.ChatMessage
import com.voxpen.app.data.repository.LlmRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

class ImportSrtUseCaseTest {
    private lateinit var chatCompletionApi: ChatCompletionApi
    private lateinit var useCase: ImportSrtUseCase

    private fun chatResponse(content: String) =
        ChatCompletionResponse(
            choices = listOf(ChatChoice(message = ChatMessage(role = "assistant", content = content))),
        )

    @BeforeEach
    fun setUp() {
        chatCompletionApi = mockk()
        val apiFactory = mockk<ChatCompletionApiFactory>()
        every { apiFactory.create(any()) } returns chatCompletionApi
        useCase = ImportSrtUseCase(RefineSegmentsUseCase(LlmRepository(apiFactory)))
    }

    @Test
    fun `should refine parsed cues and build result`() =
        runTest {
            val content =
                """
                1
                00:00:01,000 --> 00:00:02,000
                um, hello world

                2
                00:00:03,000 --> 00:00:04,000
                this is a test
                """.trimIndent()
            coEvery { chatCompletionApi.chatCompletion(any(), any()) } returns
                chatResponse("1|REFINED: um, hello world.\n2|REFINED: this is a test.")

            val result =
                useCase(content, "movie.srt", SttLanguage.English, "key", provider = LlmProvider.OpenAI)

            assertThat(result.isSuccess).isTrue()
            val imported = result.getOrThrow()
            assertThat(imported.fileName).isEqualTo("movie.srt")
            assertThat(imported.originalSrt).contains("um, hello world")
            assertThat(imported.refinedSrt).contains("REFINED: um, hello world.")
            assertThat(imported.refinedSrt).contains("00:00:01,000 --> 00:00:02,000")
            assertThat(imported.originalText).isEqualTo("um, hello world this is a test")
            assertThat(imported.refinedText).isEqualTo("REFINED: um, hello world. REFINED: this is a test.")
        }

    @Test
    fun `should fail on invalid SRT content`() =
        runTest {
            val result = useCase("not an srt at all", "bad.srt", SttLanguage.English, "key")

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()?.message).contains("Invalid SRT")
        }

    @Test
    fun `should fail when SRT exceeds five megabytes`() =
        runTest {
            val huge = "x".repeat(5 * 1024 * 1024 + 1)

            val result = useCase(huge, "huge.srt", SttLanguage.English, "key")

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()?.message).contains("too large")
        }

    @Test
    fun `should propagate refine failures`() =
        runTest {
            coEvery { chatCompletionApi.chatCompletion(any(), any()) } throws
                IOException("API key not configured")
            val result =
                useCase(
                    "1\n00:00:01,000 --> 00:00:02,000\ncue",
                    "a.srt",
                    SttLanguage.English,
                    "key",
                )

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()?.message).isEqualTo("API key not configured")
        }
}
