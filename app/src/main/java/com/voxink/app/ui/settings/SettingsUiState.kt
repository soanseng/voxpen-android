package com.voxink.app.ui.settings

import com.voxink.app.data.model.RecordingMode
import com.voxink.app.data.model.SttLanguage

data class SettingsUiState(
    val isApiKeyConfigured: Boolean = false,
    val apiKeyDisplay: String = "",
    val language: SttLanguage = SttLanguage.Auto,
    val recordingMode: RecordingMode = RecordingMode.TAP_TO_TOGGLE,
)
