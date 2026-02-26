package com.voxink.app.ime

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.voxink.app.billing.ProSource
import com.voxink.app.billing.ProStatus
import com.voxink.app.billing.UsageLimiter
import com.voxink.app.data.local.ApiKeyManager
import com.voxink.app.data.local.PreferencesManager
import com.voxink.app.data.model.SttLanguage
import com.voxink.app.data.model.ToneStyle
import com.voxink.app.data.remote.ChatChoice
import com.voxink.app.data.remote.ChatCompletionResponse
import com.voxink.app.data.remote.ChatMessage
import com.voxink.app.data.remote.GroqApi
import com.voxink.app.data.remote.WhisperResponse
import com.voxink.app.data.repository.DictionaryRepository
import com.voxink.app.data.repository.LlmRepository
import com.voxink.app.data.repository.SttRepository
import com.voxink.app.domain.usecase.RefineTextUseCase
import com.voxink.app.domain.usecase.TranscribeAudioUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingControllerTest {
    private val groqApi: GroqApi = mockk()
    private val apiKeyManager: ApiKeyManager = mockk()
    private val preferencesManager: PreferencesManager = mockk()
    private val dictionaryRepository: DictionaryRepository = mockk()
    private val testDispatcher = UnconfinedTestDispatcher()
    private val usageLimiter = UsageLimiter()
    private var proStatus: ProStatus = ProStatus.Free
    private lateinit var controller: RecordingController

    private val refinementEnabledFlow = MutableStateFlow(true)
    private val sttModelFlow = MutableStateFlow(PreferencesManager.DEFAULT_STT_MODEL)
    private val llmModelFlow = MutableStateFlow(PreferencesManager.DEFAULT_LLM_MODEL)
    private val toneStyleFlow = MutableStateFlow<ToneStyle>(ToneStyle.Casual)
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
        every { preferencesManager.refinementEnabledFlow } returns refinementEnabledFlow
        every { preferencesManager.sttModelFlow } returns sttModelFlow
        every { preferencesManager.llmModelFlow } returns llmModelFlow
        every { preferencesManager.toneStyleFlow } returns toneStyleFlow
        every { preferencesManager.customPromptFlow(any()) } returns MutableStateFlow(null)
        coEvery { dictionaryRepository.getWords(any()) } returns listOf("語墨", "Claude")

        val sttRepository = SttRepository(groqApi)
        val llmRepository = LlmRepository(groqApi)
        val transcribeUseCase = TranscribeAudioUseCase(sttRepository)
        val refineTextUseCase = RefineTextUseCase(llmRepository)

        controller =
            RecordingController(
                transcribeUseCase = transcribeUseCase,
                refineTextUseCase = refineTextUseCase,
                apiKeyManager = apiKeyManager,
                preferencesManager = preferencesManager,
                dictionaryRepository = dictionaryRepository,
                usageLimiter = usageLimiter,
                proStatusProvider = { proStatus },
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
    fun `should transition through Refining to Refined when enabled`() =
        runTest {
            coEvery {
                groqApi.transcribe(any(), any(), any(), any(), any(), any())
            } returns WhisperResponse(text = "嗯那個明天開會")
            coEvery {
                groqApi.chatCompletion(any(), any())
            } returns chatResponse("明天開會")

            controller.uiState.test {
                assertThat(awaitItem()).isEqualTo(ImeUiState.Idle)
                controller.onStartRecording(startRecording)
                assertThat(awaitItem()).isEqualTo(ImeUiState.Recording)

                controller.onStopRecording(stopRecording, SttLanguage.Chinese)
                // StateFlow conflates intermediate states; Refining may be skipped
                val states = mutableListOf(awaitItem())
                states.add(awaitItem())
                val finalState = states.last()
                assertThat(finalState).isEqualTo(
                    ImeUiState.Refined("嗯那個明天開會", "明天開會"),
                )
            }
        }

    @Test
    fun `should go to Result when refinement disabled`() =
        runTest {
            refinementEnabledFlow.value = false
            coEvery {
                groqApi.transcribe(any(), any(), any(), any(), any(), any())
            } returns WhisperResponse(text = "hello world")

            controller.uiState.test {
                assertThat(awaitItem()).isEqualTo(ImeUiState.Idle)
                controller.onStartRecording(startRecording)
                skipItems(1)

                controller.onStopRecording(stopRecording, SttLanguage.English)
                assertThat(awaitItem()).isEqualTo(ImeUiState.Processing)
                assertThat(awaitItem()).isEqualTo(ImeUiState.Result("hello world"))
            }
        }

    @Test
    fun `should fall back to Result when refinement fails`() =
        runTest {
            coEvery {
                groqApi.transcribe(any(), any(), any(), any(), any(), any())
            } returns WhisperResponse(text = "raw text")
            coEvery {
                groqApi.chatCompletion(any(), any())
            } throws IOException("LLM error")

            controller.uiState.test {
                assertThat(awaitItem()).isEqualTo(ImeUiState.Idle)
                controller.onStartRecording(startRecording)
                skipItems(1)

                controller.onStopRecording(stopRecording, SttLanguage.Auto)
                // StateFlow conflates; Refining may be skipped when LLM fails fast
                val states = mutableListOf(awaitItem())
                states.add(awaitItem())
                val finalState = states.last()
                assertThat(finalState).isEqualTo(ImeUiState.Result("raw text"))
            }
        }

    @Test
    fun `should transition to Error on transcription failure`() =
        runTest {
            coEvery {
                groqApi.transcribe(any(), any(), any(), any(), any(), any())
            } throws IOException("API error")

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

    @Test
    fun `should block recording when voice input limit reached for Free users`() =
        runTest {
            repeat(UsageLimiter.FREE_VOICE_INPUT_LIMIT) { usageLimiter.incrementVoiceInput() }

            controller.uiState.test {
                assertThat(awaitItem()).isEqualTo(ImeUiState.Idle)
                controller.onStartRecording(startRecording)
                val state = awaitItem()
                assertThat(state).isInstanceOf(ImeUiState.Error::class.java)
                assertThat((state as ImeUiState.Error).message).contains("Daily limit")
            }
        }

    @Test
    fun `should allow recording when Pro even at limit`() =
        runTest {
            proStatus = ProStatus.Pro(ProSource.GOOGLE_PLAY)
            repeat(UsageLimiter.FREE_VOICE_INPUT_LIMIT) { usageLimiter.incrementVoiceInput() }

            controller.uiState.test {
                assertThat(awaitItem()).isEqualTo(ImeUiState.Idle)
                controller.onStartRecording(startRecording)
                assertThat(awaitItem()).isEqualTo(ImeUiState.Recording)
            }
        }

    @Test
    fun `should skip refinement when refinement limit reached for Free users`() =
        runTest {
            coEvery {
                groqApi.transcribe(any(), any(), any(), any(), any(), any())
            } returns WhisperResponse(text = "hello")

            repeat(UsageLimiter.FREE_REFINEMENT_LIMIT) { usageLimiter.incrementRefinement() }

            controller.uiState.test {
                assertThat(awaitItem()).isEqualTo(ImeUiState.Idle)
                controller.onStartRecording(startRecording)
                skipItems(1)

                controller.onStopRecording(stopRecording, SttLanguage.Auto)
                skipItems(1) // Processing
                assertThat(awaitItem()).isEqualTo(ImeUiState.Result("hello"))
            }
        }

    @Test
    fun `should fetch vocabulary and pass to transcription and refinement`() =
        runTest {
            coEvery {
                groqApi.transcribe(any(), any(), any(), any(), any(), any())
            } returns WhisperResponse(text = "語末你好")
            coEvery {
                groqApi.chatCompletion(any(), any())
            } returns chatResponse("語墨你好")

            controller.uiState.test {
                assertThat(awaitItem()).isEqualTo(ImeUiState.Idle)
                controller.onStartRecording(startRecording)
                skipItems(1)
                controller.onStopRecording(stopRecording, SttLanguage.Chinese)
                val states = mutableListOf(awaitItem())
                states.add(awaitItem())
                val finalState = states.last()
                assertThat(finalState).isEqualTo(
                    ImeUiState.Refined("語末你好", "語墨你好"),
                )
            }

            coVerify { dictionaryRepository.getWords(any()) }
        }

    private fun chatResponse(content: String) =
        ChatCompletionResponse(
            id = "test",
            choices = listOf(ChatChoice(message = ChatMessage("assistant", content))),
        )
}
