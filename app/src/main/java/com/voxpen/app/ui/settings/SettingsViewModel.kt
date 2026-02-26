package com.voxpen.app.ui.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxpen.app.billing.BillingManager
import com.voxpen.app.billing.LicenseManager
import com.voxpen.app.billing.ProStatusResolver
import com.voxpen.app.billing.UsageLimiter
import com.voxpen.app.data.local.ApiKeyManager
import com.voxpen.app.data.local.PreferencesManager
import com.voxpen.app.data.model.RecordingMode
import com.voxpen.app.data.model.RefinementPrompt
import com.voxpen.app.data.model.SttLanguage
import com.voxpen.app.data.model.LlmProvider
import com.voxpen.app.data.model.ToneStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val apiKeyManager: ApiKeyManager,
        private val preferencesManager: PreferencesManager,
        private val billingManager: BillingManager,
        private val usageLimiter: UsageLimiter,
        private val licenseManager: LicenseManager,
        private val proStatusResolver: ProStatusResolver,
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
                    providerApiKeys = LlmProvider.all.associate { p ->
                        p.key to apiKeyManager.isKeyConfigured(p)
                    },
                    customBaseUrl = apiKeyManager.getCustomBaseUrl() ?: "",
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
                preferencesManager.toneStyleFlow.collect { tone ->
                    _uiState.update { it.copy(toneStyle = tone) }
                }
            }
            viewModelScope.launch {
                preferencesManager.llmProviderFlow.collect { provider ->
                    _uiState.update { it.copy(llmProvider = provider) }
                }
            }
            viewModelScope.launch {
                preferencesManager.customLlmModelFlow.collect { model ->
                    _uiState.update { it.copy(customLlmModel = model) }
                }
            }
            viewModelScope.launch {
                preferencesManager.customSttBaseUrlFlow.collect { url ->
                    _uiState.update { it.copy(customSttBaseUrl = url) }
                }
            }
            viewModelScope.launch {
                proStatusResolver.proStatus.collect { status ->
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

        fun setToneStyle(tone: ToneStyle) {
            viewModelScope.launch { preferencesManager.setToneStyle(tone) }
        }

        fun setLlmProvider(provider: LlmProvider) {
            viewModelScope.launch {
                preferencesManager.setLlmProvider(provider)
                preferencesManager.setLlmModel(provider.defaultModelId)
            }
        }

        fun saveProviderApiKey(provider: LlmProvider, key: String) {
            apiKeyManager.setApiKey(provider, key)
            _uiState.update {
                it.copy(
                    providerApiKeys = it.providerApiKeys + (provider.key to key.isNotBlank()),
                    isApiKeyConfigured = if (provider == LlmProvider.Groq) key.isNotBlank() else it.isApiKeyConfigured,
                    apiKeyDisplay = if (provider == LlmProvider.Groq) maskApiKey(key) else it.apiKeyDisplay,
                )
            }
        }

        fun setCustomLlmModel(model: String) {
            viewModelScope.launch { preferencesManager.setCustomLlmModel(model) }
        }

        fun setCustomSttBaseUrl(url: String) {
            viewModelScope.launch { preferencesManager.setCustomSttBaseUrl(url) }
        }

        fun setCustomBaseUrl(url: String) {
            apiKeyManager.setCustomBaseUrl(url)
            _uiState.update { it.copy(customBaseUrl = url) }
        }

        fun launchPurchaseFlow(activity: Activity) {
            billingManager.launchPurchaseFlow(activity)
        }

        fun restorePurchases() {
            billingManager.restorePurchases()
        }

        fun toggleDebugPro() {
            if (!com.voxpen.app.BuildConfig.DEBUG) return
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

        fun activateLicense(key: String) {
            viewModelScope.launch {
                _uiState.update { it.copy(isActivatingLicense = true, licenseError = null) }
                val result = licenseManager.activateLicense(key)
                result.fold(
                    onSuccess = {
                        _uiState.update { it.copy(isActivatingLicense = false) }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(isActivatingLicense = false, licenseError = error.message)
                        }
                    },
                )
            }
        }

        fun deactivateLicense() {
            viewModelScope.launch {
                licenseManager.deactivateLicense()
            }
        }

        fun clearLicenseError() {
            _uiState.update { it.copy(licenseError = null) }
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
