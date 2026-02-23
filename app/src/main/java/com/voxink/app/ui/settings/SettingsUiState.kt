package com.voxink.app.ui.settings

import com.voxink.app.billing.ProStatus
import com.voxink.app.data.model.RecordingMode
import com.voxink.app.data.model.SttLanguage

data class SettingsUiState(
    val isApiKeyConfigured: Boolean = false,
    val apiKeyDisplay: String = "",
    val language: SttLanguage = SttLanguage.Auto,
    val recordingMode: RecordingMode = RecordingMode.TAP_TO_TOGGLE,
    val refinementEnabled: Boolean = true,
    val proStatus: ProStatus = ProStatus.Free,
    val remainingVoiceInputs: Int = 0,
    val remainingRefinements: Int = 0,
    val remainingFileTranscriptions: Int = 0,
)
