package com.voxink.app.ui.transcription

import com.voxink.app.billing.ProStatus
import com.voxink.app.data.local.TranscriptionEntity

data class TranscriptionUiState(
    val transcriptions: List<TranscriptionEntity> = emptyList(),
    val selectedTranscription: TranscriptionEntity? = null,
    val isTranscribing: Boolean = false,
    val progress: String = "",
    val error: String? = null,
    val proStatus: ProStatus = ProStatus.Free,
    val canTranscribeFile: Boolean = true,
    val remainingFileTranscriptions: Int = 0,
    val showRewardedAdPrompt: Boolean = false,
    val showInterstitialAfterTranscription: Boolean = false,
)
