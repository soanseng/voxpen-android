package com.voxink.app.ime

import com.voxink.app.data.local.ApiKeyManager
import com.voxink.app.data.model.SttLanguage
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
    private val apiKeyManager: ApiKeyManager,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val _uiState = MutableStateFlow<ImeUiState>(ImeUiState.Idle)
    val uiState: StateFlow<ImeUiState> = _uiState.asStateFlow()

    fun onStartRecording(startRecording: () -> Unit) {
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
            val result = transcribeUseCase(pcmData, language, apiKey)
            _uiState.value =
                result.fold(
                    onSuccess = { ImeUiState.Result(it) },
                    onFailure = { ImeUiState.Error(it.message ?: "Transcription failed") },
                )
        }
    }

    fun dismiss() {
        _uiState.value = ImeUiState.Idle
    }

    fun destroy() {
        scope.cancel()
    }
}
