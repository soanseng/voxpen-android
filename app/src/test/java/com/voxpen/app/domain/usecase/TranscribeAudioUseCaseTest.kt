package com.voxpen.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.voxpen.app.data.model.SttLanguage
import com.voxpen.app.data.remote.GroqApi
import com.voxpen.app.data.remote.SttApiFactory
import com.voxpen.app.data.remote.WhisperResponse
import com.voxpen.app.data.repository.SttRepository
import com.voxpen.app.util.AudioEncoder
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

class TranscribeAudioUseCaseTest {
    private val groqApi: GroqApi = mockk()
    private val sttApiFactory: SttApiFactory = mockk()
    private lateinit var sttRepository: SttRepository
    private lateinit var useCase: TranscribeAudioUseCase

    @BeforeEach
    fun setUp() {
        mockkObject(AudioEncoder)
        sttRepository = SttRepository(groqApi, sttApiFactory)
        useCase = TranscribeAudioUseCase(sttRepository)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(AudioEncoder)
    }

    @Test
    fun `should encode PCM to WAV and call repository`() =
        runTest {
            val pcm = ByteArray(100) { 1 }
            val wav = ByteArray(144) { 2 }
            every { AudioEncoder.pcmToWav(pcm, 16000, 1, 16) } returns wav
            coEvery { groqApi.transcribe(any(), any(), any(), any(), any(), any()) } returns
                WhisperResponse(text = "你好")

            val result = useCase(pcm, SttLanguage.Chinese, "key")

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isEqualTo("你好")
        }

    @Test
    fun `should propagate repository errors`() =
        runTest {
            val pcm = ByteArray(10)
            every { AudioEncoder.pcmToWav(any(), any(), any(), any()) } returns ByteArray(54)
            coEvery { groqApi.transcribe(any(), any(), any(), any(), any(), any()) } throws
                IOException("Network error")

            val result = useCase(pcm, SttLanguage.Auto, "key")

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()?.message).contains("Network error")
        }

    @Test
    fun `should use 16kHz mono 16-bit encoding defaults`() =
        runTest {
            val pcm = ByteArray(50)
            every { AudioEncoder.pcmToWav(pcm, 16000, 1, 16) } returns ByteArray(94)
            coEvery { groqApi.transcribe(any(), any(), any(), any(), any(), any()) } returns
                WhisperResponse(text = "test")

            useCase(pcm, SttLanguage.English, "key")

            verify { AudioEncoder.pcmToWav(pcm, 16000, 1, 16) }
        }
}
