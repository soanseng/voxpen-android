package com.voxpen.app.data.local

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TranscriptionEntityTest {
    @Test
    fun `should create entity with required fields`() {
        val entity =
            TranscriptionEntity(
                fileName = "meeting.wav",
                originalText = "Hello world",
                language = "auto",
                createdAt = 1000L,
            )

        assertThat(entity.id).isEqualTo(0)
        assertThat(entity.fileName).isEqualTo("meeting.wav")
        assertThat(entity.originalText).isEqualTo("Hello world")
        assertThat(entity.refinedText).isNull()
        assertThat(entity.language).isEqualTo("auto")
        assertThat(entity.durationMs).isNull()
        assertThat(entity.fileSizeBytes).isNull()
        assertThat(entity.createdAt).isEqualTo(1000L)
    }

    @Test
    fun `should create entity with all optional fields`() {
        val entity =
            TranscriptionEntity(
                id = 42,
                fileName = "interview.mp3",
                originalText = "Raw text",
                refinedText = "Polished text",
                language = "zh",
                durationMs = 120_000L,
                fileSizeBytes = 5_000_000L,
                createdAt = 2000L,
            )

        assertThat(entity.id).isEqualTo(42)
        assertThat(entity.refinedText).isEqualTo("Polished text")
        assertThat(entity.durationMs).isEqualTo(120_000L)
        assertThat(entity.fileSizeBytes).isEqualTo(5_000_000L)
    }

    @Test
    fun `should have correct display text when refined is available`() {
        val entity =
            TranscriptionEntity(
                fileName = "test.wav",
                originalText = "um hello",
                refinedText = "Hello",
                language = "en",
                createdAt = 1000L,
            )

        assertThat(entity.displayText).isEqualTo("Hello")
    }

    @Test
    fun `should have correct display text when refined is null`() {
        val entity =
            TranscriptionEntity(
                fileName = "test.wav",
                originalText = "Hello",
                language = "en",
                createdAt = 1000L,
            )

        assertThat(entity.displayText).isEqualTo("Hello")
    }
}
