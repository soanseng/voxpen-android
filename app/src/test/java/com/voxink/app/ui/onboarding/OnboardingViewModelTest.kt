package com.voxink.app.ui.onboarding

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.voxink.app.data.local.ApiKeyManager
import com.voxink.app.data.local.PreferencesManager
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var apiKeyManager: ApiKeyManager
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var viewModel: OnboardingViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        apiKeyManager = mockk(relaxed = true)
        preferencesManager = mockk(relaxed = true)
        every { apiKeyManager.isGroqKeyConfigured() } returns false
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): OnboardingViewModel =
        OnboardingViewModel(apiKeyManager, preferencesManager)

    @Test
    fun `should start at welcome step`() = runTest {
        viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.currentStep).isEqualTo(OnboardingStep.WELCOME)
        }
    }

    @Test
    fun `should advance from welcome to api key step`() = runTest {
        viewModel = createViewModel()

        viewModel.nextStep()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.currentStep).isEqualTo(OnboardingStep.API_KEY)
        }
    }

    @Test
    fun `should advance through all steps sequentially`() = runTest {
        viewModel = createViewModel()

        viewModel.nextStep() // WELCOME -> API_KEY
        assertThat(viewModel.uiState.value.currentStep).isEqualTo(OnboardingStep.API_KEY)

        viewModel.nextStep() // API_KEY -> ENABLE_KEYBOARD
        assertThat(viewModel.uiState.value.currentStep).isEqualTo(OnboardingStep.ENABLE_KEYBOARD)

        viewModel.nextStep() // ENABLE_KEYBOARD -> GRANT_PERMISSION
        assertThat(viewModel.uiState.value.currentStep).isEqualTo(OnboardingStep.GRANT_PERMISSION)

        viewModel.nextStep() // GRANT_PERMISSION -> DONE
        assertThat(viewModel.uiState.value.currentStep).isEqualTo(OnboardingStep.DONE)
    }

    @Test
    fun `should go back to previous step`() = runTest {
        viewModel = createViewModel()

        viewModel.nextStep() // -> API_KEY
        viewModel.nextStep() // -> ENABLE_KEYBOARD
        viewModel.previousStep() // -> API_KEY

        assertThat(viewModel.uiState.value.currentStep).isEqualTo(OnboardingStep.API_KEY)
    }

    @Test
    fun `should not go back from welcome step`() = runTest {
        viewModel = createViewModel()

        viewModel.previousStep()

        assertThat(viewModel.uiState.value.currentStep).isEqualTo(OnboardingStep.WELCOME)
    }

    @Test
    fun `should save api key`() = runTest {
        viewModel = createViewModel()

        // After setGroqApiKey is called, isGroqKeyConfigured should return true
        every { apiKeyManager.isGroqKeyConfigured() } returns true
        viewModel.saveApiKey("gsk_test_key_123")

        assertThat(viewModel.uiState.value.isApiKeyConfigured).isTrue()
    }

    @Test
    fun `should mark onboarding complete`() = runTest {
        viewModel = createViewModel()

        viewModel.completeOnboarding()

        coVerify { preferencesManager.setOnboardingCompleted(true) }
    }

    @Test
    fun `should update keyboard enabled state`() = runTest {
        viewModel = createViewModel()

        viewModel.updateKeyboardEnabled(true)

        assertThat(viewModel.uiState.value.isKeyboardEnabled).isTrue()
    }

    @Test
    fun `should update mic permission state`() = runTest {
        viewModel = createViewModel()

        viewModel.updateMicPermission(true)

        assertThat(viewModel.uiState.value.hasMicPermission).isTrue()
    }
}
