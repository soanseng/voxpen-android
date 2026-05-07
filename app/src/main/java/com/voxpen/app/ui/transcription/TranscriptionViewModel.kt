package com.voxpen.app.ui.transcription

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxpen.app.billing.ProStatusResolver
import com.voxpen.app.billing.UsageLimiter
import com.voxpen.app.data.local.ApiKeyManager
import com.voxpen.app.data.local.PreferencesManager
import com.voxpen.app.data.local.TranscriptionEntity
import com.voxpen.app.data.model.SttLanguage
import com.voxpen.app.data.model.SttProvider
import com.voxpen.app.data.repository.TranscriptionRepository
import com.voxpen.app.domain.usecase.RetryTranscriptionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TranscriptionViewModel
    @Inject
    constructor(
        private val transcriptionRepository: TranscriptionRepository,
        private val proStatusResolver: ProStatusResolver,
        private val usageLimiter: UsageLimiter,
        private val retryTranscriptionUseCase: RetryTranscriptionUseCase,
        private val apiKeyManager: ApiKeyManager,
        private val preferencesManager: PreferencesManager,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(TranscriptionUiState())
        val uiState: StateFlow<TranscriptionUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                transcriptionRepository.getAll().collect { list ->
                    _uiState.update { it.copy(transcriptions = list) }
                }
            }
            viewModelScope.launch {
                proStatusResolver.proStatus.collect { status ->
                    _uiState.update {
                        it.copy(
                            proStatus = status,
                            canTranscribeFile = status.isPro || usageLimiter.canTranscribeFile(),
                            remainingFileTranscriptions = usageLimiter.remainingFileTranscriptions(),
                        )
                    }
                }
            }
        }

        fun selectTranscription(entity: TranscriptionEntity) {
            _uiState.update { it.copy(selectedTranscription = entity) }
        }

        fun clearSelection() {
            _uiState.update { it.copy(selectedTranscription = null) }
        }

        fun deleteTranscription(id: Long) {
            viewModelScope.launch {
                transcriptionRepository.deleteById(id)
                _uiState.update {
                    if (it.selectedTranscription?.id == id) {
                        it.copy(selectedTranscription = null)
                    } else {
                        it
                    }
                }
            }
        }

        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }

        fun onFileSelected(uri: Uri) {
            val proStatus = proStatusResolver.proStatus.value
            if (!proStatus.isPro && !usageLimiter.canTranscribeFile()) {
                _uiState.update { it.copy(showUpgradePrompt = true) }
                return
            }
            _uiState.update { it.copy(isTranscribing = true, progress = "Preparing…") }
        }

        fun onTranscriptionComplete(entity: TranscriptionEntity) {
            val proStatus = proStatusResolver.proStatus.value
            if (!proStatus.isPro) {
                usageLimiter.incrementFileTranscription()
            }
            _uiState.update {
                it.copy(
                    isTranscribing = false,
                    progress = "",
                    selectedTranscription = entity,
                    canTranscribeFile = proStatus.isPro || usageLimiter.canTranscribeFile(),
                    remainingFileTranscriptions = usageLimiter.remainingFileTranscriptions(),
                )
            }
        }

        fun dismissUpgradePrompt() {
            _uiState.update { it.copy(showUpgradePrompt = false) }
        }

        fun setLanguage(language: SttLanguage) {
            _uiState.update { it.copy(selectedLanguage = language) }
        }

        fun onTranscriptionError(message: String) {
            _uiState.update {
                it.copy(isTranscribing = false, progress = "", error = message)
            }
        }

        fun retryTranscription(id: Long) {
            viewModelScope.launch {
                _uiState.update { it.copy(retryingId = id, error = null) }
                val proStatus = proStatusResolver.proStatus.value
                if (!proStatus.isPro && !usageLimiter.canUseVoiceInput()) {
                    val remaining = usageLimiter.remainingVoiceInputs()
                    _uiState.update {
                        it.copy(
                            retryingId = null,
                            error = "Daily limit reached ($remaining remaining). Upgrade to Pro for unlimited use.",
                        )
                    }
                    return@launch
                }
                val provider = preferencesManager.sttProviderFlow.first()
                val model = preferencesManager.sttModelFlow.first().ifBlank { provider.defaultModelId }
                val customSttBaseUrl =
                    if (provider == SttProvider.Custom) {
                        preferencesManager.customSttBaseUrlFlow.first().ifBlank { null }
                    } else {
                        null
                    }
                val apiKey = apiKeyManager.getSttApiKey(provider)
                if (apiKey.isNullOrBlank()) {
                    _uiState.update {
                        it.copy(retryingId = null, error = "API key not configured")
                    }
                    return@launch
                }
                val result =
                    retryTranscriptionUseCase(
                        id = id,
                        apiKey = apiKey,
                        provider = provider,
                        model = model,
                        customSttBaseUrl = customSttBaseUrl,
                    )
                result.fold(
                    onSuccess = { entity ->
                        if (!proStatus.isPro) {
                            usageLimiter.incrementVoiceInput()
                        }
                        _uiState.update {
                            it.copy(retryingId = null, selectedTranscription = entity)
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(retryingId = null, error = error.message ?: "Retry failed")
                        }
                    },
                )
            }
        }
    }
