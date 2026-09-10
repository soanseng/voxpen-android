package com.voxpen.app.util

import com.voxpen.app.data.local.TranscriptionEntity
import com.voxpen.app.data.repository.TranscriptionSegment
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

object ExportHelper {
    private const val ESTIMATED_SECONDS_PER_SENTENCE = 5L
    private const val MS_PER_SECOND = 1000L

    fun toPlainText(entity: TranscriptionEntity): String =
        buildString {
            appendLine("File: ${entity.fileName}")
            appendLine()
            if (entity.refinedText != null) {
                appendLine("Original:")
                appendLine(entity.originalText)
                appendLine()
                appendLine("Refined:")
                appendLine(entity.refinedText)
            } else {
                appendLine(entity.originalText)
            }
        }

    fun toSrt(entity: TranscriptionEntity): String {
        // Refined segments win when present (desktop export preference).
        parseSegments(entity.refinedSegmentsJson)?.let { return segmentsToSrt(it) }
        parseSegments(entity.segmentsJson)?.let { return segmentsToSrt(it) }

        // Fallback to estimated timestamps for old entries without segments
        val text = entity.displayText
        val sentences = splitIntoSentences(text)

        return buildString {
            sentences.forEachIndexed { index, sentence ->
                val startMs = index * ESTIMATED_SECONDS_PER_SENTENCE * MS_PER_SECOND
                val endMs = (index + 1) * ESTIMATED_SECONDS_PER_SENTENCE * MS_PER_SECOND
                appendLine("${index + 1}")
                appendLine("${formatSrtTimestamp(startMs)} --> ${formatSrtTimestamp(endMs)}")
                appendLine(sentence.trim())
                appendLine()
            }
        }
    }

    fun segmentsToSrt(segments: List<TranscriptionSegment>): String =
        buildString {
            segments.forEachIndexed { index, seg ->
                appendLine("${index + 1}")
                appendLine("${formatSrtTimestamp(seg.startMs)} --> ${formatSrtTimestamp(seg.endMs)}")
                appendLine(seg.text.trim())
                appendLine()
            }
        }

    /** Plain-text view of segments: trimmed non-empty texts joined by single spaces. */
    fun segmentsToText(segments: List<TranscriptionSegment>): String =
        segments.map { it.text.trim() }.filter { it.isNotEmpty() }.joinToString(" ")

    fun formatSrtTimestamp(ms: Long): String {
        val hours = ms / 3_600_000
        val minutes = (ms % 3_600_000) / 60_000
        val seconds = (ms % 60_000) / 1_000
        val millis = ms % 1_000
        return "%02d:%02d:%02d,%03d".format(hours, minutes, seconds, millis)
    }

    private fun parseSegments(json: String?): List<TranscriptionSegment>? {
        if (json.isNullOrBlank()) return null
        return try {
            Json.decodeFromString<List<StoredSegment>>(json).map {
                TranscriptionSegment(it.s, it.e, it.t)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun splitIntoSentences(text: String): List<String> {
        val sentences =
            text.split(Regex("(?<=[.!?。！？])[\\s]+"))
                .filter { it.isNotBlank() }
        return sentences.ifEmpty { listOf(text) }
    }
}


@Serializable
private data class StoredSegment(val s: Long, val e: Long, val t: String)
