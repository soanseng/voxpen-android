package com.voxpen.app.data.local

import androidx.room.ColumnInfo
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
    val segmentsJson: String? = null,
    @ColumnInfo(defaultValue = "'completed'")
    val status: String = STATUS_COMPLETED,
    val errorMessage: String? = null,
    val audioPath: String? = null,
    val provider: String? = null,
    val createdAt: Long,
) {
    val displayText: String
        get() = refinedText ?: originalText.ifBlank { errorMessage.orEmpty() }

    val isFailed: Boolean
        get() = status == STATUS_FAILED

    companion object {
        const val STATUS_COMPLETED = "completed"
        const val STATUS_FAILED = "failed"
    }
}
