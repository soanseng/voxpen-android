package com.voxpen.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.voxpen.app.data.local.RecordingStore
import com.voxpen.app.data.local.TranscriptionEntity
import com.voxpen.app.data.model.SttProvider
import com.voxpen.app.data.remote.SttApi
import com.voxpen.app.data.remote.SttApiFactory
import com.voxpen.app.data.remote.WhisperResponse
import com.voxpen.app.data.repository.SttRepository
import com.voxpen.app.data.repository.TranscriptionRepository
import com.voxpen.app.util.AudioEncoder
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RetryTranscriptionUseCaseTest {
    private val transcriptionRepository: TranscriptionRepository = mockk()
    private val recordingStore: RecordingStore = mockk()
    private val sttApi: SttApi = mockk()
    private val sttApiFactory: SttApiFactory = mockk()
    private lateinit var useCase: RetryTranscriptionUseCase

    @BeforeEach
    fun setUp() {
        every { sttApiFactory.createForProvider(any()) } returns sttApi
        useCase =
            RetryTranscriptionUseCase(
                transcriptionRepository = transcriptionRepository,
                recordingStore = recordingStore,
                sttRepository = SttRepository(sttApiFactory),
            )
    }

    @Test
    fun `retry reads saved wav chunks and marks same row completed`() =
        runTest {
            val failed =
                TranscriptionEntity(
                    id = 7,
                    fileName = "Failed voice recording",
                    originalText = "",
                    language = "en",
                    status = TranscriptionEntity.STATUS_FAILED,
                    errorMessage = "Network failed",
                    audioPath = "/recordings/live.wav",
                    provider = "groq",
                    createdAt = 100L,
                )
            val wav = AudioEncoder.pcmToWav(ByteArray(LIVE_AUDIO_CHUNK_BYTES + 128) { it.toByte() }, 16_000, 1, 16)
            val responses =
                listOf(
                    WhisperResponse(text = "first"),
                    WhisperResponse(text = "second"),
                )
            coEvery { transcriptionRepository.getById(7) } returns failed
            every { recordingStore.exists("/recordings/live.wav") } returns true
            every { recordingStore.read("/recordings/live.wav") } returns wav
            coEvery {
                sttApi.transcribe(any(), any(), any(), any(), any(), any())
            } returnsMany responses
            coEvery { transcriptionRepository.markCompletedAfterRetry(7, "first second") } returns
                failed.copy(
                    originalText = "first second",
                    status = TranscriptionEntity.STATUS_COMPLETED,
                    errorMessage = null,
                    audioPath = null,
                )

            val result = useCase(7, "key", SttProvider.Groq, "whisper-large-v3-turbo")

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()?.originalText).isEqualTo("first second")
            assertThat(result.getOrNull()?.audioPath).isNull()
        }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `retry defers saved wav read to IO dispatcher`() =
        runTest {
            val ioDispatcher = StandardTestDispatcher(testScheduler)
            useCase =
                RetryTranscriptionUseCase(
                    transcriptionRepository = transcriptionRepository,
                    recordingStore = recordingStore,
                    sttRepository = SttRepository(sttApiFactory),
                    ioDispatcher = ioDispatcher,
                )
            val failed =
                TranscriptionEntity(
                    id = 7,
                    fileName = "Failed voice recording",
                    originalText = "",
                    language = "en",
                    status = TranscriptionEntity.STATUS_FAILED,
                    errorMessage = "Network failed",
                    audioPath = "/recordings/live.wav",
                    provider = "groq",
                    createdAt = 100L,
                )
            val wav = AudioEncoder.pcmToWav(ByteArray(128) { it.toByte() }, 16_000, 1, 16)
            coEvery { transcriptionRepository.getById(7) } returns failed
            every { recordingStore.exists("/recordings/live.wav") } returns true
            every { recordingStore.read("/recordings/live.wav") } returns wav
            coEvery {
                sttApi.transcribe(any(), any(), any(), any(), any(), any())
            } returns WhisperResponse(text = "retry text")
            coEvery { transcriptionRepository.markCompletedAfterRetry(7, "retry text") } returns
                failed.copy(
                    originalText = "retry text",
                    status = TranscriptionEntity.STATUS_COMPLETED,
                    errorMessage = null,
                    audioPath = null,
                )

            val result =
                async(UnconfinedTestDispatcher(testScheduler)) {
                    useCase(7, "key", SttProvider.Groq, "whisper-large-v3-turbo")
                }

            verify(exactly = 0) { recordingStore.read(any()) }

            advanceUntilIdle()

            assertThat(result.await().isSuccess).isTrue()
            verify(exactly = 1) { recordingStore.read("/recordings/live.wav") }
        }

    @Test
    fun `retry fails when saved audio is missing`() =
        runTest {
            val failed =
                TranscriptionEntity(
                    id = 7,
                    fileName = "Failed voice recording",
                    originalText = "",
                    language = "en",
                    status = TranscriptionEntity.STATUS_FAILED,
                    errorMessage = "Network failed",
                    audioPath = "/recordings/live.wav",
                    createdAt = 100L,
                )
            coEvery { transcriptionRepository.getById(7) } returns failed
            every { recordingStore.exists("/recordings/live.wav") } returns false

            val result = useCase(7, "key", SttProvider.Groq, "whisper-large-v3-turbo")

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()?.message).contains("Saved audio")
        }

    private companion object {
        private const val LIVE_AUDIO_CHUNK_BYTES = 16_000 * 2 * 60
    }
}
