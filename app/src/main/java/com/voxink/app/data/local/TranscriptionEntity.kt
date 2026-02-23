package com.voxink.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transcriptions")
data class TranscriptionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val originalText: String,
    val refinedText: String? = null,
    val language: String,
    val durationMs: Long? = null,
    val fileSizeBytes: Long? = null,
    val createdAt: Long,
) {
    val displayText: String
        get() = refinedText ?: originalText
}
