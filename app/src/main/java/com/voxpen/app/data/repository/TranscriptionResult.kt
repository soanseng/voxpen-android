package com.voxpen.app.data.repository

data class TranscriptionResult(
    val text: String,
    val segments: List<TranscriptionSegment> = emptyList(),
)

data class TranscriptionSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)
