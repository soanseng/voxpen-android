package com.voxpen.app.ui.settings

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.voxpen.app.billing.BillingManager
import com.voxpen.app.billing.LicenseManager
import com.voxpen.app.billing.ProSource
import com.voxpen.app.billing.ProStatus
import com.voxpen.app.billing.ProStatusResolver
import com.voxpen.app.billing.UsageLimiter
import com.voxpen.app.data.local.ApiKeyManager
import com.voxpen.app.data.local.PreferencesManager
import com.voxpen.app.data.model.LlmProvider
import com.voxpen.app.data.model.RecordingMode
import com.voxpen.app.data.model.SttLanguage
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
class SettingsViewModelTest {
    private val apiKeyManager: ApiKeyManager = mockk(relaxed = true)
    private val preferencesManager: PreferencesManager = mockk(relaxed = true)
    private val billingManager: BillingManager = mockk(relaxed = true)
    private val usageLimiter = UsageLimiter()
    private val licenseManager: LicenseManager = mockk(relaxed = true)
    private val proStatusResolver: ProStatusResolver = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    private val proStatusFlow = MutableStateFlow<ProStatus>(ProStatus.Free)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { apiKeyManager.isGroqKeyConfigured() } returns false
        every { apiKeyManager.getGroqApiKey() } returns null
        every { preferencesManager.languageFlow } returns flowOf(SttLanguage.Auto)
        every { preferencesManager.recordingModeFlow } returns flowOf(RecordingMode.TAP_TO_TOGGLE)
        every { preferencesManager.refinementEnabledFlow } returns flowOf(true)
        every { preferencesManager.llmProviderFlow } returns flowOf(LlmProvider.Groq)
        every { preferencesManager.customLlmModelFlow } returns flowOf("")
        every { proStatusResolver.proStatus } returns proStatusFlow
        every { billingManager.proStatus } returns proStatusFlow
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        SettingsViewModel(apiKeyManager, preferencesManager, billingManager, usageLimiter, licenseManager, proStatusResolver)

    @Test
    fun `should emit initial state with defaults`() =
        runTest {
            val vm = createViewModel()
            vm.uiState.test {
                val state = awaitItem()
                assertThat(state.isApiKeyConfigured).isFalse()
                assertThat(state.language).isEqualTo(SttLanguage.Auto)
                assertThat(state.recordingMode).isEqualTo(RecordingMode.TAP_TO_TOGGLE)
                assertThat(state.proStatus).isEqualTo(ProStatus.Free)
                assertThat(state.remainingVoiceInputs).isEqualTo(UsageLimiter.FREE_VOICE_INPUT_LIMIT)
            }
        }

    @Test
    fun `should save API key and update state`() =
        runTest {
            val vm = createViewModel()
            every { apiKeyManager.isGroqKeyConfigured() } returns true

            vm.saveApiKey("gsk_test123")

            verify { apiKeyManager.setGroqApiKey("gsk_test123") }
            vm.uiState.test {
                assertThat(awaitItem().isApiKeyConfigured).isTrue()
            }
        }

    @Test
    fun `should update language`() =
        runTest {
            val vm = createViewModel()
            vm.setLanguage(SttLanguage.Chinese)
            coVerify { preferencesManager.setLanguage(SttLanguage.Chinese) }
        }

    @Test
    fun `should update recording mode`() =
        runTest {
            val vm = createViewModel()
            vm.setRecordingMode(RecordingMode.HOLD_TO_RECORD)
            coVerify { preferencesManager.setRecordingMode(RecordingMode.HOLD_TO_RECORD) }
        }

    @Test
    fun `should reflect Pro status from ProStatusResolver`() =
        runTest {
            val vm = createViewModel()
            proStatusFlow.value = ProStatus.Pro(ProSource.GOOGLE_PLAY)
            vm.uiState.test {
                assertThat(awaitItem().proStatus).isEqualTo(ProStatus.Pro(ProSource.GOOGLE_PLAY))
            }
        }

    @Test
    fun `refreshUsage should update remaining counts`() =
        runTest {
            val vm = createViewModel()
            usageLimiter.incrementVoiceInput()
            vm.refreshUsage()
            vm.uiState.test {
                val state = awaitItem()
                assertThat(state.remainingVoiceInputs).isEqualTo(UsageLimiter.FREE_VOICE_INPUT_LIMIT - 1)
            }
        }

    @Test
    fun `setTranslationEnabled should persist preference`() =
        runTest {
            val vm = createViewModel()
            vm.setTranslationEnabled(true)
            coVerify { preferencesManager.setTranslationEnabled(true) }
        }

    @Test
    fun `setTranslationTargetLanguage should persist preference`() =
        runTest {
            val vm = createViewModel()
            vm.setTranslationTargetLanguage(SttLanguage.Japanese)
            coVerify { preferencesManager.setTranslationTargetLanguage(SttLanguage.Japanese) }
        }

    @Test
    fun `default translationEnabled should be false`() =
        runTest {
            val vm = createViewModel()
            vm.uiState.test {
                assertThat(awaitItem().translationEnabled).isFalse()
            }
        }
}
