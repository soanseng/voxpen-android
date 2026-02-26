package com.voxpen.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.voxpen.app.data.local.TranscriptionEntity
import com.voxpen.app.data.model.LlmProvider
import com.voxpen.app.data.model.SttLanguage
import com.voxpen.app.data.remote.ChatChoice
import com.voxpen.app.data.remote.ChatCompletionApi
import com.voxpen.app.data.remote.ChatCompletionApiFactory
import com.voxpen.app.data.remote.ChatCompletionResponse
import com.voxpen.app.data.remote.ChatMessage
import com.voxpen.app.data.remote.GroqApi
import com.voxpen.app.data.remote.SttApiFactory
import com.voxpen.app.data.remote.WhisperResponse
import com.voxpen.app.data.repository.LlmRepository
import com.voxpen.app.data.repository.SttRepository
import com.voxpen.app.data.repository.TranscriptionRepository
import com.voxpen.app.util.AudioEncoder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TranscribeFileUseCaseTest {
    private lateinit var groqApi: GroqApi
    private lateinit var sttApiFactory: SttApiFactory
    private lateinit var sttRepository: SttRepository
    private lateinit var transcriptionRepository: TranscriptionRepository
    private lateinit var chatCompletionApi: ChatCompletionApi
    private lateinit var apiFactory: ChatCompletionApiFactory
    private lateinit var refineTextUseCase: RefineTextUseCase
    private lateinit var useCase: TranscribeFileUseCase

    private fun chatResponse(content: String) =
        ChatCompletionResponse(
            choices = listOf(ChatChoice(message = ChatMessage(role = "assistant", content = content))),
        )

    @BeforeEach
    fun setUp() {
        groqApi = mockk()
        sttApiFactory = mockk()
        sttRepository = SttRepository(groqApi, sttApiFactory)
        transcriptionRepository = mockk(relaxed = true)
        chatCompletionApi = mockk()
        apiFactory = mockk()
        every { apiFactory.create(any()) } returns chatCompletionApi
        val llmRepository = LlmRepository(apiFactory)
        refineTextUseCase = RefineTextUseCase(llmRepository)
        useCase = TranscribeFileUseCase(sttRepository, transcriptionRepository, refineTextUseCase)
    }

    @Test
    fun `should transcribe small WAV file in single chunk`() =
        runTest {
            val pcmData = ByteArray(1000) { (it % 256).toByte() }
            val wavBytes = AudioEncoder.pcmToWav(pcmData, 16000, 1, 16)

            coEvery { groqApi.transcribe(any(), any(), any(), any(), any(), any()) } returns
                WhisperResponse(text = "Hello world")
            coEvery { transcriptionRepository.insert(any()) } returns 1L

            val result =
                useCase(
                    fileBytes = wavBytes,
                    fileName = "test.wav",
                    language = SttLanguage.English,
                    apiKey = "test-key",
                )

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()?.originalText).isEqualTo("Hello world")
        }

    @Test
    fun `should merge results from multiple chunks`() =
        runTest {
            val pcmData = ByteArray(200_000) { (it % 256).toByte() }
            val wavBytes = AudioEncoder.pcmToWav(pcmData, 16000, 1, 16)

            var callCount = 0
            coEvery { groqApi.transcribe(any(), any(), any(), any(), any(), any()) } answers {
                callCount++
                WhisperResponse(text = "Part $callCount")
            }
            coEvery { transcriptionRepository.insert(any()) } returns 1L

            val result =
                useCase(
                    fileBytes = wavBytes,
                    fileName = "long.wav",
                    language = SttLanguage.Auto,
                    apiKey = "test-key",
                    maxChunkBytes = 50_000,
                )

            assertThat(result.isSuccess).isTrue()
            val text = result.getOrNull()?.originalText ?: ""
            assertThat(text).contains("Part 1")
            assertThat(callCount).isGreaterThan(1)
        }

    @Test
    fun `should save transcription to repository`() =
        runTest {
            val pcmData = ByteArray(100) { (it % 256).toByte() }
            val wavBytes = AudioEncoder.pcmToWav(pcmData, 16000, 1, 16)

            coEvery { groqApi.transcribe(any(), any(), any(), any(), any(), any()) } returns
                WhisperResponse(text = "Saved text")
            val entitySlot = slot<TranscriptionEntity>()
            coEvery { transcriptionRepository.insert(capture(entitySlot)) } returns 5L

            useCase(
                fileBytes = wavBytes,
                fileName = "meeting.wav",
                language = SttLanguage.Chinese,
                apiKey = "key",
            )

            coVerify { transcriptionRepository.insert(any()) }
            assertThat(entitySlot.captured.fileName).isEqualTo("meeting.wav")
            assertThat(entitySlot.captured.originalText).isEqualTo("Saved text")
            assertThat(entitySlot.captured.language).isEqualTo("zh")
        }

    @Test
    fun `should return failure when transcription fails`() =
        runTest {
            val pcmData = ByteArray(100) { (it % 256).toByte() }
            val wavBytes = AudioEncoder.pcmToWav(pcmData, 16000, 1, 16)

            coEvery { groqApi.transcribe(any(), any(), any(), any(), any(), any()) } throws
                java.io.IOException("Network error")

            val result =
                useCase(
                    fileBytes = wavBytes,
                    fileName = "test.wav",
                    language = SttLanguage.Auto,
                    apiKey = "key",
                )

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()?.message).isEqualTo("Network error")
        }

    @Test
    fun `should handle non-WAV files as raw chunks`() =
        runTest {
            val rawBytes = ByteArray(100) { it.toByte() }

            coEvery { groqApi.transcribe(any(), any(), any(), any(), any(), any()) } returns
                WhisperResponse(text = "Transcribed")
            coEvery { transcriptionRepository.insert(any()) } returns 1L

            val result =
                useCase(
                    fileBytes = rawBytes,
                    fileName = "audio.mp3",
                    language = SttLanguage.Auto,
                    apiKey = "key",
                )

            assertThat(result.isSuccess).isTrue()
        }

    @Test
    fun `should include file size in saved entity`() =
        runTest {
            val pcmData = ByteArray(500) { (it % 256).toByte() }
            val wavBytes = AudioEncoder.pcmToWav(pcmData, 16000, 1, 16)

            coEvery { groqApi.transcribe(any(), any(), any(), any(), any(), any()) } returns
                WhisperResponse(text = "text")
            val entitySlot = slot<TranscriptionEntity>()
            coEvery { transcriptionRepository.insert(capture(entitySlot)) } returns 1L

            useCase(
                fileBytes = wavBytes,
                fileName = "test.wav",
                language = SttLanguage.Auto,
                apiKey = "key",
            )

            assertThat(entitySlot.captured.fileSizeBytes).isEqualTo(wavBytes.size.toLong())
        }

    @Test
    fun `should refine transcription when refinement params provided`() =
        runTest {
            val pcmData = ByteArray(100) { (it % 256).toByte() }
            val wavBytes = AudioEncoder.pcmToWav(pcmData, 16000, 1, 16)

            coEvery { groqApi.transcribe(any(), any(), any(), any(), any(), any()) } returns
                WhisperResponse(text = "raw text")
            coEvery { chatCompletionApi.chatCompletion(any(), any()) } returns
                chatResponse("polished text")
            val entitySlot = slot<TranscriptionEntity>()
            coEvery { transcriptionRepository.insert(capture(entitySlot)) } returns 1L

            val result =
                useCase(
                    fileBytes = wavBytes,
                    fileName = "test.wav",
                    language = SttLanguage.English,
                    apiKey = "key",
                    refinementApiKey = "llm-key",
                    llmModel = "gpt-4o-mini",
                    llmProvider = LlmProvider.OpenAI,
                )

            assertThat(result.isSuccess).isTrue()
            assertThat(entitySlot.captured.refinedText).isEqualTo("polished text")
        }

    @Test
    fun `should skip refinement when refinementApiKey is null`() =
        runTest {
            val pcmData = ByteArray(100) { (it % 256).toByte() }
            val wavBytes = AudioEncoder.pcmToWav(pcmData, 16000, 1, 16)

            coEvery { groqApi.transcribe(any(), any(), any(), any(), any(), any()) } returns
                WhisperResponse(text = "raw text")
            val entitySlot = slot<TranscriptionEntity>()
            coEvery { transcriptionRepository.insert(capture(entitySlot)) } returns 1L

            val result =
                useCase(
                    fileBytes = wavBytes,
                    fileName = "test.wav",
                    language = SttLanguage.English,
                    apiKey = "key",
                )

            assertThat(result.isSuccess).isTrue()
            assertThat(entitySlot.captured.refinedText).isNull()
            coVerify(exactly = 0) { chatCompletionApi.chatCompletion(any(), any()) }
        }
}
