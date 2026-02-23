package com.voxink.app.ime

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.voxink.app.data.local.ApiKeyManager
import com.voxink.app.data.model.SttLanguage
import com.voxink.app.data.remote.GroqApi
import com.voxink.app.data.remote.WhisperResponse
import com.voxink.app.data.repository.SttRepository
import com.voxink.app.domain.usecase.TranscribeAudioUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingControllerTest {
    private val groqApi: GroqApi = mockk()
    private val apiKeyManager: ApiKeyManager = mockk()
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var controller: RecordingController

    private var fakeRecordedAudio: ByteArray = ByteArray(100) { it.toByte() }
    private var isRecording = false
    private val startRecording: () -> Unit = { isRecording = true }
    private val stopRecording: () -> ByteArray = {
        isRecording = false
        fakeRecordedAudio
    }

    @BeforeEach
    fun setUp() {
        every { apiKeyManager.getGroqApiKey() } returns "test-key"
        val sttRepository = SttRepository(groqApi)
        val transcribeUseCase = TranscribeAudioUseCase(sttRepository)
        controller =
            RecordingController(
                transcribeUseCase = transcribeUseCase,
                apiKeyManager = apiKeyManager,
                ioDispatcher = testDispatcher,
            )
    }

    @Test
    fun `should start in Idle state`() =
        runTest {
            controller.uiState.test {
                assertThat(awaitItem()).isEqualTo(ImeUiState.Idle)
            }
        }

    @Test
    fun `should transition to Recording on start`() =
        runTest {
            controller.uiState.test {
                assertThat(awaitItem()).isEqualTo(ImeUiState.Idle)
                controller.onStartRecording(startRecording)
                assertThat(awaitItem()).isEqualTo(ImeUiState.Recording)
            }
        }

    @Test
    fun `should transition to Processing then Result on stop`() =
        runTest {
            coEvery { groqApi.transcribe(any(), any(), any(), any(), any(), any()) } returns
                WhisperResponse(text = "你好世界")

            controller.uiState.test {
                assertThat(awaitItem()).isEqualTo(ImeUiState.Idle)
                controller.onStartRecording(startRecording)
                assertThat(awaitItem()).isEqualTo(ImeUiState.Recording)

                controller.onStopRecording(stopRecording, SttLanguage.Chinese)
                assertThat(awaitItem()).isEqualTo(ImeUiState.Processing)
                assertThat(awaitItem()).isEqualTo(ImeUiState.Result("你好世界"))
            }
        }

    @Test
    fun `should transition to Error on transcription failure`() =
        runTest {
            coEvery { groqApi.transcribe(any(), any(), any(), any(), any(), any()) } throws
                IOException("API error")

            controller.uiState.test {
                assertThat(awaitItem()).isEqualTo(ImeUiState.Idle)
                controller.onStartRecording(startRecording)
                skipItems(1)

                controller.onStopRecording(stopRecording, SttLanguage.Auto)
                skipItems(1)
                val error = awaitItem()
                assertThat(error).isInstanceOf(ImeUiState.Error::class.java)
                assertThat((error as ImeUiState.Error).message).contains("API error")
            }
        }

    @Test
    fun `should show error when API key not configured`() =
        runTest {
            every { apiKeyManager.getGroqApiKey() } returns null

            controller.uiState.test {
                assertThat(awaitItem()).isEqualTo(ImeUiState.Idle)
                controller.onStartRecording(startRecording)
                skipItems(1)

                controller.onStopRecording(stopRecording, SttLanguage.Auto)
                val state = awaitItem()
                assertThat(state).isInstanceOf(ImeUiState.Error::class.java)
                assertThat((state as ImeUiState.Error).message).contains("API key")
            }
        }

    @Test
    fun `should return to Idle on dismiss`() =
        runTest {
            controller.uiState.test {
                assertThat(awaitItem()).isEqualTo(ImeUiState.Idle)
                controller.onStartRecording(startRecording)
                skipItems(1)
                controller.dismiss()
                assertThat(awaitItem()).isEqualTo(ImeUiState.Idle)
            }
        }
}
