package com.voxink.app.ui.transcription

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxink.app.billing.BillingManager
import com.voxink.app.billing.UsageLimiter
import com.voxink.app.data.local.TranscriptionEntity
import com.voxink.app.data.repository.TranscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TranscriptionViewModel
    @Inject
    constructor(
        private val transcriptionRepository: TranscriptionRepository,
        private val billingManager: BillingManager,
        private val usageLimiter: UsageLimiter,
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
                billingManager.proStatus.collect { status ->
                    _uiState.update {
                        it.copy(
                            proStatus = status,
                            canTranscribeFile = status.isPro || usageLimiter.canTranscribeFile(0),
                            remainingFileTranscriptionSeconds = usageLimiter.remainingFileTranscriptionSeconds(),
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
            val proStatus = billingManager.proStatus.value
            if (!proStatus.isPro && !usageLimiter.canTranscribeFile(0)) {
                _uiState.update { it.copy(showRewardedAdPrompt = true) }
                return
            }
            _uiState.update { it.copy(isTranscribing = true, progress = "Preparing…") }
        }

        fun onTranscriptionComplete(entity: TranscriptionEntity) {
            val proStatus = billingManager.proStatus.value
            if (!proStatus.isPro) {
                usageLimiter.addFileTranscriptionDuration(0)
            }
            _uiState.update {
                it.copy(
                    isTranscribing = false,
                    progress = "",
                    selectedTranscription = entity,
                    canTranscribeFile = proStatus.isPro || usageLimiter.canTranscribeFile(0),
                    remainingFileTranscriptionSeconds = usageLimiter.remainingFileTranscriptionSeconds(),
                    showInterstitialAfterTranscription = !proStatus.isPro,
                )
            }
        }

        fun onRewardedAdWatched() {
            // Bonus voice inputs removed in monetization v2
            _uiState.update {
                it.copy(
                    showRewardedAdPrompt = false,
                    canTranscribeFile = billingManager.proStatus.value.isPro || usageLimiter.canTranscribeFile(0),
                    remainingFileTranscriptionSeconds = usageLimiter.remainingFileTranscriptionSeconds(),
                )
            }
        }

        fun dismissRewardedPrompt() {
            _uiState.update { it.copy(showRewardedAdPrompt = false) }
        }

        fun onInterstitialShown() {
            _uiState.update { it.copy(showInterstitialAfterTranscription = false) }
        }

        fun onTranscriptionError(message: String) {
            _uiState.update {
                it.copy(isTranscribing = false, progress = "", error = message)
            }
        }
    }
