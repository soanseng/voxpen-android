package com.voxink.app.ui.transcription

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.voxink.app.billing.ProSource
import com.voxink.app.billing.ProStatus
import com.voxink.app.billing.ProStatusResolver
import com.voxink.app.billing.UsageLimiter
import com.voxink.app.data.local.TranscriptionEntity
import com.voxink.app.data.repository.TranscriptionRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TranscriptionViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var transcriptionRepository: TranscriptionRepository
    private lateinit var proStatusResolver: ProStatusResolver
    private lateinit var usageLimiter: UsageLimiter
    private lateinit var viewModel: TranscriptionViewModel
    private val proStatusFlow = MutableStateFlow<ProStatus>(ProStatus.Free)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        transcriptionRepository = mockk(relaxed = true)
        proStatusResolver = mockk(relaxed = true)
        usageLimiter = UsageLimiter()
        every { transcriptionRepository.getAll() } returns flowOf(emptyList())
        every { proStatusResolver.proStatus } returns proStatusFlow
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): TranscriptionViewModel =
        TranscriptionViewModel(transcriptionRepository, proStatusResolver, usageLimiter)

    @Test
    fun `should start with empty transcription list`() =
        runTest {
            viewModel = createViewModel()

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.transcriptions).isEmpty()
                assertThat(state.isTranscribing).isFalse()
                assertThat(state.error).isNull()
            }
        }

    @Test
    fun `should collect transcriptions from repository`() =
        runTest {
            val entities =
                listOf(
                    TranscriptionEntity(
                        id = 1,
                        fileName = "a.wav",
                        originalText = "Hello",
                        language = "en",
                        createdAt = 2000L,
                    ),
                    TranscriptionEntity(
                        id = 2,
                        fileName = "b.wav",
                        originalText = "World",
                        language = "zh",
                        createdAt = 1000L,
                    ),
                )
            every { transcriptionRepository.getAll() } returns flowOf(entities)
            viewModel = createViewModel()

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.transcriptions).hasSize(2)
                assertThat(state.transcriptions[0].fileName).isEqualTo("a.wav")
            }
        }

    @Test
    fun `should select transcription for detail view`() =
        runTest {
            val entity =
                TranscriptionEntity(
                    id = 1,
                    fileName = "a.wav",
                    originalText = "Hello",
                    language = "en",
                    createdAt = 1000L,
                )
            every { transcriptionRepository.getAll() } returns flowOf(listOf(entity))
            viewModel = createViewModel()

            viewModel.selectTranscription(entity)

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.selectedTranscription).isNotNull()
                assertThat(state.selectedTranscription?.fileName).isEqualTo("a.wav")
            }
        }

    @Test
    fun `should clear selection`() =
        runTest {
            val entity =
                TranscriptionEntity(
                    id = 1,
                    fileName = "a.wav",
                    originalText = "Hello",
                    language = "en",
                    createdAt = 1000L,
                )
            every { transcriptionRepository.getAll() } returns flowOf(listOf(entity))
            viewModel = createViewModel()

            viewModel.selectTranscription(entity)
            viewModel.clearSelection()

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.selectedTranscription).isNull()
            }
        }

    @Test
    fun `should delete transcription by id`() =
        runTest {
            viewModel = createViewModel()

            viewModel.deleteTranscription(1L)

            coVerify { transcriptionRepository.deleteById(1L) }
        }

    @Test
    fun `should clear error`() =
        runTest {
            viewModel = createViewModel()

            viewModel.clearError()

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.error).isNull()
            }
        }

    @Test
    fun `should show upgrade prompt when limit reached for Free users`() =
        runTest {
            usageLimiter.addFileTranscriptionDuration(UsageLimiter.FREE_FILE_TRANSCRIPTION_DURATION + 1)
            viewModel = createViewModel()

            viewModel.onFileSelected(mockk())

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.showUpgradePrompt).isTrue()
                assertThat(state.isTranscribing).isFalse()
            }
        }

    @Test
    fun `should allow file transcription for Pro users even at limit`() =
        runTest {
            proStatusFlow.value = ProStatus.Pro(ProSource.GOOGLE_PLAY)
            usageLimiter.addFileTranscriptionDuration(UsageLimiter.FREE_FILE_TRANSCRIPTION_DURATION + 1)
            viewModel = createViewModel()

            viewModel.onFileSelected(mockk())

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.isTranscribing).isTrue()
                assertThat(state.error).isNull()
            }
        }

    @Test
    fun `should increment usage after transcription complete for Free users`() =
        runTest {
            viewModel = createViewModel()
            val entity =
                TranscriptionEntity(
                    id = 1,
                    fileName = "a.wav",
                    originalText = "Hello",
                    language = "en",
                    createdAt = 1000L,
                )

            viewModel.onTranscriptionComplete(entity)

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.remainingFileTranscriptionSeconds)
                    .isEqualTo(UsageLimiter.FREE_FILE_TRANSCRIPTION_DURATION)
            }
        }
}
