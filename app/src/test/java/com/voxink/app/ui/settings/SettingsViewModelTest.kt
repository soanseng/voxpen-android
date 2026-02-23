package com.voxink.app.ui.settings

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.voxink.app.data.local.ApiKeyManager
import com.voxink.app.data.local.PreferencesManager
import com.voxink.app.data.model.RecordingMode
import com.voxink.app.data.model.SttLanguage
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { apiKeyManager.isGroqKeyConfigured() } returns false
        every { apiKeyManager.getGroqApiKey() } returns null
        every { preferencesManager.languageFlow } returns flowOf(SttLanguage.Auto)
        every { preferencesManager.recordingModeFlow } returns flowOf(RecordingMode.TAP_TO_TOGGLE)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SettingsViewModel(apiKeyManager, preferencesManager)

    @Test
    fun `should emit initial state with defaults`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertThat(state.isApiKeyConfigured).isFalse()
            assertThat(state.language).isEqualTo(SttLanguage.Auto)
            assertThat(state.recordingMode).isEqualTo(RecordingMode.TAP_TO_TOGGLE)
        }
    }

    @Test
    fun `should save API key and update state`() = runTest {
        val vm = createViewModel()
        every { apiKeyManager.isGroqKeyConfigured() } returns true

        vm.saveApiKey("gsk_test123")

        verify { apiKeyManager.setGroqApiKey("gsk_test123") }
        vm.uiState.test {
            assertThat(awaitItem().isApiKeyConfigured).isTrue()
        }
    }

    @Test
    fun `should update language`() = runTest {
        val vm = createViewModel()
        vm.setLanguage(SttLanguage.Chinese)
        coVerify { preferencesManager.setLanguage(SttLanguage.Chinese) }
    }

    @Test
    fun `should update recording mode`() = runTest {
        val vm = createViewModel()
        vm.setRecordingMode(RecordingMode.HOLD_TO_RECORD)
        coVerify { preferencesManager.setRecordingMode(RecordingMode.HOLD_TO_RECORD) }
    }
}
