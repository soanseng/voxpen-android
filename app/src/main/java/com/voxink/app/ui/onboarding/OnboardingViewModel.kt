package com.voxink.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxink.app.data.local.ApiKeyManager
import com.voxink.app.data.local.PreferencesManager
import com.voxink.app.domain.usecase.RefineTextUseCase
import com.voxink.app.domain.usecase.TranscribeAudioUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val apiKeyManager: ApiKeyManager,
        private val preferencesManager: PreferencesManager,
        private val transcribeUseCase: TranscribeAudioUseCase,
        private val refineTextUseCase: RefineTextUseCase,
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

        fun setPracticeResult(
            original: String,
            refined: String?,
        ) {
            _uiState.update {
                it.copy(
                    practiceOriginal = original,
                    practiceRefined = refined,
                    hasPracticed = true,
                    isPracticing = false,
                )
            }
        }

        fun clearPractice() {
            _uiState.update {
                it.copy(hasPracticed = false, practiceOriginal = null, practiceRefined = null)
            }
        }

        fun startPractice(audioData: ByteArray) {
            _uiState.update { it.copy(isPracticing = true) }
            viewModelScope.launch {
                val apiKey = apiKeyManager.getGroqApiKey() ?: return@launch
                val language = preferencesManager.languageFlow.first()
                val sttModel = preferencesManager.sttModelFlow.first()
                val llmModel = preferencesManager.llmModelFlow.first()

                val sttResult = transcribeUseCase(audioData, language, apiKey, sttModel)
                sttResult.fold(
                    onSuccess = { original ->
                        val refineResult = refineTextUseCase(original, language, apiKey, llmModel)
                        setPracticeResult(original, refineResult.getOrNull())
                    },
                    onFailure = {
                        _uiState.update { state ->
                            state.copy(isPracticing = false)
                        }
                    },
                )
            }
        }
    }
