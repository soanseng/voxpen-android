package com.voxink.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxink.app.data.local.ApiKeyManager
import com.voxink.app.data.local.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val apiKeyManager: ApiKeyManager,
        private val preferencesManager: PreferencesManager,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(OnboardingUiState())
        val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

        init {
            _uiState.update {
                it.copy(isApiKeyConfigured = apiKeyManager.isGroqKeyConfigured())
            }
        }

        fun nextStep() {
            _uiState.update { state ->
                val next = OnboardingStep.entries.getOrNull(state.currentStep.ordinal + 1)
                state.copy(currentStep = next ?: state.currentStep)
            }
        }

        fun previousStep() {
            _uiState.update { state ->
                val prev = OnboardingStep.entries.getOrNull(state.currentStep.ordinal - 1)
                state.copy(currentStep = prev ?: state.currentStep)
            }
        }

        fun saveApiKey(key: String) {
            apiKeyManager.setGroqApiKey(key)
            _uiState.update {
                it.copy(isApiKeyConfigured = apiKeyManager.isGroqKeyConfigured())
            }
        }

        fun updateKeyboardEnabled(enabled: Boolean) {
            _uiState.update { it.copy(isKeyboardEnabled = enabled) }
        }

        fun updateMicPermission(granted: Boolean) {
            _uiState.update { it.copy(hasMicPermission = granted) }
        }

        fun completeOnboarding() {
            viewModelScope.launch {
                preferencesManager.setOnboardingCompleted(true)
            }
        }
    }
