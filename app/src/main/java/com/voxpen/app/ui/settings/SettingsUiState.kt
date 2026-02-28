package com.voxpen.app.ui.settings

import com.voxpen.app.billing.ProStatus
import com.voxpen.app.data.local.PreferencesManager
import com.voxpen.app.data.model.RecordingMode
import com.voxpen.app.data.model.SttLanguage
import com.voxpen.app.data.model.LlmProvider
import com.voxpen.app.data.model.ToneStyle

data class SettingsUiState(
    val isApiKeyConfigured: Boolean = false,
    val apiKeyDisplay: String = "",
    val language: SttLanguage = SttLanguage.Auto,
    val recordingMode: RecordingMode = RecordingMode.TAP_TO_TOGGLE,
    val refinementEnabled: Boolean = true,
    val sttModel: String = PreferencesManager.DEFAULT_STT_MODEL,
    val llmModel: String = PreferencesManager.DEFAULT_LLM_MODEL,
    val toneStyle: ToneStyle = ToneStyle.DEFAULT,
    val llmProvider: LlmProvider = LlmProvider.DEFAULT,
    val customLlmModel: String = "",
    val customBaseUrl: String = "",
    val customSttBaseUrl: String = "",
    val providerApiKeys: Map<String, Boolean> = emptyMap(),
    val proStatus: ProStatus = ProStatus.Free,
    val remainingVoiceInputs: Int = 0,
    val remainingRefinements: Int = 0,
    val remainingFileTranscriptions: Int = 0,
    val isActivatingLicense: Boolean = false,
    val licenseError: String? = null,
    val customPrompt: String? = null,
    val customPromptDraft: String = "",
    val promptSnackbar: String? = null,
    val translationEnabled: Boolean = false,
    val translationTargetLanguage: SttLanguage = SttLanguage.English,
)
