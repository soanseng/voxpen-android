package com.voxpen.app.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class WhisperResponse(
    val task: String? = null,
    val language: String? = null,
    val duration: Double? = null,
    val text: String,
    val segments: List<WhisperSegment>? = null,
)

@Serializable
data class WhisperSegment(
    val id: Int,
    val start: Double,
    val end: Double,
    val text: String,
)
