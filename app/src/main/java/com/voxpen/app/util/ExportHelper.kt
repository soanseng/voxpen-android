package com.voxpen.app.util

import com.voxpen.app.data.local.TranscriptionEntity

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

    fun formatSrtTimestamp(ms: Long): String {
        val hours = ms / 3_600_000
        val minutes = (ms % 3_600_000) / 60_000
        val seconds = (ms % 60_000) / 1_000
        val millis = ms % 1_000
        return "%02d:%02d:%02d,%03d".format(hours, minutes, seconds, millis)
    }

    private fun splitIntoSentences(text: String): List<String> {
        val sentences =
            text.split(Regex("(?<=[.!?。！？])[\\s]+"))
                .filter { it.isNotBlank() }
        return sentences.ifEmpty { listOf(text) }
    }
}
