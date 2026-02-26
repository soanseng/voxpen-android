package com.voxink.app.ui.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxink.app.ads.RewardedAdLoader
import com.voxink.app.billing.BillingManager
import com.voxink.app.billing.UsageLimiter
import com.voxink.app.data.local.ApiKeyManager
import com.voxink.app.data.local.PreferencesManager
import com.voxink.app.data.model.RecordingMode
import com.voxink.app.data.model.RefinementPrompt
import com.voxink.app.data.model.SttLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val apiKeyManager: ApiKeyManager,
        private val preferencesManager: PreferencesManager,
        private val billingManager: BillingManager,
        private val usageLimiter: UsageLimiter,
        private val rewardedAdLoader: RewardedAdLoader,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SettingsUiState())
        val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
        private var customPromptJob: Job? = null

        init {
            _uiState.update {
                it.copy(
                    isApiKeyConfigured = apiKeyManager.isGroqKeyConfigured(),
                    apiKeyDisplay = maskApiKey(apiKeyManager.getGroqApiKey()),
                    remainingVoiceInputs = usageLimiter.remainingVoiceInputs(),
                    remainingRefinements = usageLimiter.remainingRefinements(),
                    remainingFileTranscriptionSeconds = usageLimiter.remainingFileTranscriptionSeconds(),
                )
            }
            viewModelScope.launch {
                preferencesManager.languageFlow.collect { lang ->
                    _uiState.update { it.copy(language = lang) }
                    loadCustomPromptForLanguage(lang)
                }
            }
            viewModelScope.launch {
                preferencesManager.recordingModeFlow.collect { mode ->
                    _uiState.update { it.copy(recordingMode = mode) }
                }
            }
            viewModelScope.launch {
                preferencesManager.refinementEnabledFlow.collect { enabled ->
                    _uiState.update { it.copy(refinementEnabled = enabled) }
                }
            }
            viewModelScope.launch {
                preferencesManager.sttModelFlow.collect { model ->
                    _uiState.update { it.copy(sttModel = model) }
                }
            }
            viewModelScope.launch {
                preferencesManager.llmModelFlow.collect { model ->
                    _uiState.update { it.copy(llmModel = model) }
                }
            }
            viewModelScope.launch {
                billingManager.proStatus.collect { status ->
                    _uiState.update { it.copy(proStatus = status) }
                }
            }
        }

        fun saveApiKey(key: String) {
            apiKeyManager.setGroqApiKey(key)
            _uiState.update {
                it.copy(
                    isApiKeyConfigured = apiKeyManager.isGroqKeyConfigured(),
                    apiKeyDisplay = maskApiKey(key),
                )
            }
        }

        fun setLanguage(language: SttLanguage) {
            viewModelScope.launch { preferencesManager.setLanguage(language) }
        }

        fun setRecordingMode(mode: RecordingMode) {
            viewModelScope.launch { preferencesManager.setRecordingMode(mode) }
        }

        fun setRefinementEnabled(enabled: Boolean) {
            viewModelScope.launch { preferencesManager.setRefinementEnabled(enabled) }
        }

        fun setSttModel(model: String) {
            viewModelScope.launch { preferencesManager.setSttModel(model) }
        }

        fun setLlmModel(model: String) {
            viewModelScope.launch { preferencesManager.setLlmModel(model) }
        }

        fun restorePurchases() {
            billingManager.restorePurchases()
        }

        fun toggleDebugPro() {
            val current = billingManager.proStatus.value
            billingManager.debugOverrideProStatus(!current.isPro)
        }

        fun refreshUsage() {
            _uiState.update {
                it.copy(
                    remainingVoiceInputs = usageLimiter.remainingVoiceInputs(),
                    remainingRefinements = usageLimiter.remainingRefinements(),
                    remainingFileTranscriptionSeconds = usageLimiter.remainingFileTranscriptionSeconds(),
                )
            }
        }

        fun watchRewardedAd(activity: Activity) {
            _uiState.update { it.copy(isLoadingAd = true, adError = null) }
            rewardedAdLoader.loadAndShow(
                activity = activity,
                onRewarded = { _ ->
                    Timber.d("Reward granted, adding bonus voice inputs")
                    // Bonus voice inputs removed in monetization v2
                    refreshUsage()
                },
                onAdDismissed = {
                    Timber.d("Ad dismissed, refreshing usage")
                    _uiState.update { it.copy(isLoadingAd = false) }
                    refreshUsage()
                },
                onAdNotAvailable = {
                    _uiState.update { it.copy(isLoadingAd = false, adError = "Ad not available") }
                },
            )
        }

        fun clearAdError() {
            _uiState.update { it.copy(adError = null) }
        }

        fun updateCustomPromptDraft(draft: String) {
            _uiState.update { it.copy(customPromptDraft = draft) }
        }

        fun saveCustomPrompt() {
            val state = _uiState.value
            val langKey = PreferencesManager.languageToKey(state.language)
            val draft = state.customPromptDraft
            val defaultPrompt = RefinementPrompt.defaultForLanguage(state.language)
            val promptToSave = if (draft.isBlank() || draft == defaultPrompt) null else draft
            viewModelScope.launch {
                preferencesManager.setCustomPrompt(langKey, promptToSave)
                _uiState.update {
                    it.copy(
                        customPrompt = promptToSave,
                        promptSnackbar = "saved",
                    )
                }
            }
        }

        fun resetCustomPrompt() {
            val state = _uiState.value
            val langKey = PreferencesManager.languageToKey(state.language)
            val defaultPrompt = RefinementPrompt.defaultForLanguage(state.language)
            viewModelScope.launch {
                preferencesManager.setCustomPrompt(langKey, null)
                _uiState.update {
                    it.copy(
                        customPrompt = null,
                        customPromptDraft = defaultPrompt,
                        promptSnackbar = "reset",
                    )
                }
            }
        }

        fun clearPromptSnackbar() {
            _uiState.update { it.copy(promptSnackbar = null) }
        }

        private fun loadCustomPromptForLanguage(language: SttLanguage) {
            customPromptJob?.cancel()
            customPromptJob =
                viewModelScope.launch {
                    val langKey = PreferencesManager.languageToKey(language)
                    preferencesManager.customPromptFlow(langKey).collect { saved ->
                        val defaultPrompt = RefinementPrompt.defaultForLanguage(language)
                        _uiState.update {
                            it.copy(
                                customPrompt = saved,
                                customPromptDraft = saved ?: defaultPrompt,
                            )
                        }
                    }
                }
        }

        private fun maskApiKey(key: String?): String {
            if (key.isNullOrBlank()) return ""
            if (key.length <= 8) return "••••••••"
            return key.take(4) + "••••" + key.takeLast(4)
        }
    }
