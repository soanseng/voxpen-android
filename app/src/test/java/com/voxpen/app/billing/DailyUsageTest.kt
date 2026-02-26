package com.voxpen.app.billing

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DailyUsageTest {
    @Test
    fun `default counts should be zero`() {
        val usage = DailyUsage(date = LocalDate.of(2026, 2, 24))
        assertThat(usage.voiceInputCount).isEqualTo(0)
        assertThat(usage.refinementCount).isEqualTo(0)
        assertThat(usage.fileTranscriptionCount).isEqualTo(0)
    }

    @Test
    fun `should store date correctly`() {
        val date = LocalDate.of(2026, 1, 15)
        val usage = DailyUsage(date = date)
        assertThat(usage.date).isEqualTo(date)
    }

    @Test
    fun `copy should increment counts correctly`() {
        val usage = DailyUsage(date = LocalDate.now(), voiceInputCount = 5)
        val updated = usage.copy(voiceInputCount = usage.voiceInputCount + 1)
        assertThat(updated.voiceInputCount).isEqualTo(6)
    }

    @Test
    fun `copy should increment transcription count`() {
        val usage = DailyUsage(date = LocalDate.now(), fileTranscriptionCount = 1)
        val updated = usage.copy(fileTranscriptionCount = usage.fileTranscriptionCount + 1)
        assertThat(updated.fileTranscriptionCount).isEqualTo(2)
    }
}
