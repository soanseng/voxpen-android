package com.voxink.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.voxink.app.data.local.TranscriptionEntity
import com.voxink.app.data.model.SttLanguage
import com.voxink.app.data.remote.GroqApi
import com.voxink.app.data.remote.WhisperResponse
import com.voxink.app.data.repository.SttRepository
import com.voxink.app.data.repository.TranscriptionRepository
import com.voxink.app.util.AudioEncoder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TranscribeFileUseCaseTest {
    private lateinit var groqApi: GroqApi
    private lateinit var sttRepository: SttRepository
    private lateinit var transcriptionRepository: TranscriptionRepository
    private lateinit var useCase: TranscribeFileUseCase

    @BeforeEach
    fun setUp() {
        groqApi = mockk()
        sttRepository = SttRepository(groqApi)
        transcriptionRepository = mockk(relaxed = true)
        useCase = TranscribeFileUseCase(sttRepository, transcriptionRepository)
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
}
