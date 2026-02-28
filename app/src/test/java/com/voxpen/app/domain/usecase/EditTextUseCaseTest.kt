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

class EditTextUseCaseTest {
    private val chatCompletionApi: ChatCompletionApi = mockk()
    private val apiFactory: ChatCompletionApiFactory = mockk()
    private lateinit var useCase: EditTextUseCase

    @BeforeEach
    fun setUp() {
        every { apiFactory.create(any()) } returns chatCompletionApi
        useCase = EditTextUseCase(LlmRepository(apiFactory))
    }

    @Test
    fun `should return revised text on success`() =
        runTest {
            coEvery { chatCompletionApi.chatCompletion(any(), any()) } returns
                ChatCompletionResponse(
                    choices = listOf(
                        ChatChoice(message = ChatMessage(role = "assistant", content = "A formal Hello World.")),
                    ),
                )

            val result = useCase(
                selectedText = "hello world",
                instruction = "make it formal",
                language = SttLanguage.English,
                apiKey = "test-key",
            )

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isEqualTo("A formal Hello World.")
        }

    @Test
    fun `should fail when api key is blank`() =
        runTest {
            val result = useCase(
                selectedText = "hello",
                instruction = "make it formal",
                language = SttLanguage.English,
                apiKey = "",
            )
            assertThat(result.isFailure).isTrue()
        }

    @Test
    fun `should fail when selected text is blank`() =
        runTest {
            val result = useCase(
                selectedText = "",
                instruction = "make it formal",
                language = SttLanguage.English,
                apiKey = "test-key",
            )
            assertThat(result.isFailure).isTrue()
        }
}
