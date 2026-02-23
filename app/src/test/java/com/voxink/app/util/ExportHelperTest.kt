package com.voxink.app.util

import com.google.common.truth.Truth.assertThat
import com.voxink.app.data.local.TranscriptionEntity
import org.junit.jupiter.api.Test

class ExportHelperTest {
    @Test
    fun `should export as plain text with original only`() {
        val entity = TranscriptionEntity(
            fileName = "test.wav",
            originalText = "Hello world",
            language = "en",
            createdAt = 1000L,
        )

        val text = ExportHelper.toPlainText(entity)

        assertThat(text).contains("test.wav")
        assertThat(text).contains("Hello world")
    }

    @Test
    fun `should export as plain text with both original and refined`() {
        val entity = TranscriptionEntity(
            fileName = "meeting.wav",
            originalText = "um hello world",
            refinedText = "Hello world.",
            language = "en",
            createdAt = 1000L,
        )

        val text = ExportHelper.toPlainText(entity)

        assertThat(text).contains("meeting.wav")
        assertThat(text).contains("Original:")
        assertThat(text).contains("um hello world")
        assertThat(text).contains("Refined:")
        assertThat(text).contains("Hello world.")
    }

    @Test
    fun `should export as SRT format`() {
        val entity = TranscriptionEntity(
            fileName = "test.wav",
            originalText = "This is the first sentence. This is the second sentence. And this is the third one.",
            language = "en",
            createdAt = 1000L,
        )

        val srt = ExportHelper.toSrt(entity)

        assertThat(srt).contains("1\n")
        assertThat(srt).contains("-->")
        assertThat(srt).contains("This is the first sentence.")
    }

    @Test
    fun `should use refined text for SRT when available`() {
        val entity = TranscriptionEntity(
            fileName = "test.wav",
            originalText = "um raw text",
            refinedText = "Polished text here.",
            language = "en",
            createdAt = 1000L,
        )

        val srt = ExportHelper.toSrt(entity)

        assertThat(srt).contains("Polished text here.")
        assertThat(srt).doesNotContain("um raw text")
    }

    @Test
    fun `should format SRT timestamps correctly`() {
        val timestamp = ExportHelper.formatSrtTimestamp(3661500L)
        assertThat(timestamp).isEqualTo("01:01:01,500")
    }

    @Test
    fun `should format zero timestamp`() {
        val timestamp = ExportHelper.formatSrtTimestamp(0L)
        assertThat(timestamp).isEqualTo("00:00:00,000")
    }

    @Test
    fun `should handle single sentence SRT`() {
        val entity = TranscriptionEntity(
            fileName = "short.wav",
            originalText = "Just one sentence.",
            language = "en",
            createdAt = 1000L,
        )

        val srt = ExportHelper.toSrt(entity)

        assertThat(srt).startsWith("1\n")
        assertThat(srt).contains("Just one sentence.")
    }
}
