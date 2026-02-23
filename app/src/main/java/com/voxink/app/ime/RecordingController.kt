package com.voxink.app.ime

import com.voxink.app.billing.ProStatus
import com.voxink.app.billing.UsageLimiter
import com.voxink.app.data.local.ApiKeyManager
import com.voxink.app.data.local.PreferencesManager
import com.voxink.app.data.model.SttLanguage
import com.voxink.app.domain.usecase.RefineTextUseCase
import com.voxink.app.domain.usecase.TranscribeAudioUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RecordingController(
    private val transcribeUseCase: TranscribeAudioUseCase,
    private val refineTextUseCase: RefineTextUseCase,
    private val apiKeyManager: ApiKeyManager,
    private val preferencesManager: PreferencesManager,
    private val usageLimiter: UsageLimiter,
    private val proStatusProvider: () -> ProStatus,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var refinementEnabled: Boolean = PreferencesManager.DEFAULT_REFINEMENT_ENABLED

    init {
        scope.launch {
            preferencesManager.refinementEnabledFlow.collect { refinementEnabled = it }
        }
    }

    private val _uiState = MutableStateFlow<ImeUiState>(ImeUiState.Idle)
    val uiState: StateFlow<ImeUiState> = _uiState.asStateFlow()

    fun onStartRecording(startRecording: () -> Unit) {
        val proStatus = proStatusProvider()
        if (!proStatus.isPro && !usageLimiter.canUseVoiceInput()) {
            val remaining = usageLimiter.remainingVoiceInputs()
            _uiState.value =
                ImeUiState.Error("Daily limit reached ($remaining remaining). Upgrade to Pro for unlimited use.")
            return
        }
        startRecording()
        _uiState.value = ImeUiState.Recording
    }

    fun onStopRecording(
        stopRecording: () -> ByteArray,
        language: SttLanguage,
    ) {
        val pcmData = stopRecording()
        val apiKey = apiKeyManager.getGroqApiKey()

        if (apiKey.isNullOrBlank()) {
            _uiState.value = ImeUiState.Error("API key not configured")
            return
        }

        _uiState.value = ImeUiState.Processing
        scope.launch {
            val proStatus = proStatusProvider()
            val result = transcribeUseCase(pcmData, language, apiKey)
            result.fold(
                onSuccess = { originalText ->
                    // Increment voice input usage after successful transcription
                    if (!proStatus.isPro) {
                        usageLimiter.incrementVoiceInput()
                    }

                    val shouldRefine = refinementEnabled && canUseRefinement(proStatus)
                    if (!shouldRefine) {
                        _uiState.value = ImeUiState.Result(originalText)
                        return@launch
                    }
                    _uiState.value = ImeUiState.Refining(originalText)
                    if (!proStatus.isPro) {
                        usageLimiter.incrementRefinement()
                    }
                    val refinedResult = refineTextUseCase(originalText, language, apiKey)
                    _uiState.value =
                        refinedResult.fold(
                            onSuccess = { ImeUiState.Refined(originalText, it) },
                            onFailure = { ImeUiState.Result(originalText) },
                        )
                },
                onFailure = {
                    _uiState.value = ImeUiState.Error(it.message ?: "Transcription failed")
                },
            )
        }
    }

    private fun canUseRefinement(proStatus: ProStatus): Boolean = proStatus.isPro || usageLimiter.canUseRefinement()

    fun dismiss() {
        _uiState.value = ImeUiState.Idle
    }

    fun destroy() {
        scope.cancel()
    }
}
