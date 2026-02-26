package com.voxpen.app.billing

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class UsageLimiterTest {
    private lateinit var limiter: UsageLimiter

    @BeforeEach
    fun setUp() {
        limiter = UsageLimiter()
    }

    @Test
    fun `initial usage should have zero counts`() {
        val usage = limiter.currentUsage
        assertThat(usage.voiceInputCount).isEqualTo(0)
        assertThat(usage.refinementCount).isEqualTo(0)
        assertThat(usage.fileTranscriptionCount).isEqualTo(0)
    }

    @Test
    fun `canUseVoiceInput should return true when under limit`() {
        assertThat(limiter.canUseVoiceInput()).isTrue()
    }

    @Test
    fun `canUseVoiceInput should return false when at limit`() {
        repeat(UsageLimiter.FREE_VOICE_INPUT_LIMIT) { limiter.incrementVoiceInput() }
        assertThat(limiter.canUseVoiceInput()).isFalse()
    }

    @Test
    fun `voice input limit should be 30`() {
        assertThat(UsageLimiter.FREE_VOICE_INPUT_LIMIT).isEqualTo(30)
    }

    @Test
    fun `canUseRefinement should return true when under limit`() {
        assertThat(limiter.canUseRefinement()).isTrue()
    }

    @Test
    fun `canUseRefinement should return false when at limit`() {
        repeat(UsageLimiter.FREE_REFINEMENT_LIMIT) { limiter.incrementRefinement() }
        assertThat(limiter.canUseRefinement()).isFalse()
    }

    @Test
    fun `refinement limit should be 10`() {
        assertThat(UsageLimiter.FREE_REFINEMENT_LIMIT).isEqualTo(10)
    }

    @Test
    fun `canTranscribeFile should return true when under limit`() {
        assertThat(limiter.canTranscribeFile()).isTrue()
    }

    @Test
    fun `canTranscribeFile should return false when at limit`() {
        repeat(UsageLimiter.FREE_FILE_TRANSCRIPTION_LIMIT) { limiter.incrementFileTranscription() }
        assertThat(limiter.canTranscribeFile()).isFalse()
    }

    @Test
    fun `file transcription limit should be 2`() {
        assertThat(UsageLimiter.FREE_FILE_TRANSCRIPTION_LIMIT).isEqualTo(2)
    }

    @Test
    fun `remainingFileTranscriptions should decrease after incrementing`() {
        assertThat(limiter.remainingFileTranscriptions()).isEqualTo(2)
        limiter.incrementFileTranscription()
        assertThat(limiter.remainingFileTranscriptions()).isEqualTo(1)
    }

    @Test
    fun `incrementVoiceInput should increase count by one`() {
        limiter.incrementVoiceInput()
        assertThat(limiter.currentUsage.voiceInputCount).isEqualTo(1)
    }

    @Test
    fun `incrementRefinement should increase count by one`() {
        limiter.incrementRefinement()
        assertThat(limiter.currentUsage.refinementCount).isEqualTo(1)
    }

    @Test
    fun `incrementFileTranscription should increase count by one`() {
        limiter.incrementFileTranscription()
        assertThat(limiter.currentUsage.fileTranscriptionCount).isEqualTo(1)
    }

    @Test
    fun `resetIfNewDay should reset counts when date changes`() {
        limiter.incrementVoiceInput()
        limiter.incrementRefinement()
        limiter.incrementFileTranscription()
        val tomorrow = LocalDate.now().plusDays(1)
        limiter.resetIfNewDay(tomorrow)
        assertThat(limiter.currentUsage.voiceInputCount).isEqualTo(0)
        assertThat(limiter.currentUsage.refinementCount).isEqualTo(0)
        assertThat(limiter.currentUsage.fileTranscriptionCount).isEqualTo(0)
    }

    @Test
    fun `resetIfNewDay should not reset counts when same day`() {
        limiter.incrementVoiceInput()
        limiter.resetIfNewDay(LocalDate.now())
        assertThat(limiter.currentUsage.voiceInputCount).isEqualTo(1)
    }

    @Test
    fun `remainingVoiceInputs should return correct count`() {
        assertThat(limiter.remainingVoiceInputs()).isEqualTo(UsageLimiter.FREE_VOICE_INPUT_LIMIT)
        limiter.incrementVoiceInput()
        assertThat(limiter.remainingVoiceInputs()).isEqualTo(UsageLimiter.FREE_VOICE_INPUT_LIMIT - 1)
    }

    @Test
    fun `remainingRefinements should return correct count`() {
        assertThat(limiter.remainingRefinements()).isEqualTo(UsageLimiter.FREE_REFINEMENT_LIMIT)
    }
}
