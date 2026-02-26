package com.voxink.app.billing

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
        assertThat(usage.fileTranscriptionSeconds).isEqualTo(0)
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
    fun `voice input limit should be 15`() {
        assertThat(UsageLimiter.FREE_VOICE_INPUT_LIMIT).isEqualTo(15)
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
    fun `refinement limit should be 3`() {
        assertThat(UsageLimiter.FREE_REFINEMENT_LIMIT).isEqualTo(3)
    }

    @Test
    fun `canTranscribeFile should return true when under duration limit`() {
        assertThat(limiter.canTranscribeFile(60)).isTrue()
    }

    @Test
    fun `canTranscribeFile should return false when file exceeds remaining duration`() {
        limiter.addFileTranscriptionDuration(250)
        assertThat(limiter.canTranscribeFile(60)).isFalse()
    }

    @Test
    fun `canTranscribeFile should return true when file fits exactly`() {
        limiter.addFileTranscriptionDuration(240)
        assertThat(limiter.canTranscribeFile(60)).isTrue()
    }

    @Test
    fun `file transcription duration limit should be 300 seconds`() {
        assertThat(UsageLimiter.FREE_FILE_TRANSCRIPTION_DURATION).isEqualTo(300)
    }

    @Test
    fun `remainingFileTranscriptionSeconds should decrease after adding duration`() {
        assertThat(limiter.remainingFileTranscriptionSeconds()).isEqualTo(300)
        limiter.addFileTranscriptionDuration(120)
        assertThat(limiter.remainingFileTranscriptionSeconds()).isEqualTo(180)
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
    fun `addFileTranscriptionDuration should add seconds`() {
        limiter.addFileTranscriptionDuration(90)
        assertThat(limiter.currentUsage.fileTranscriptionSeconds).isEqualTo(90)
    }

    @Test
    fun `resetIfNewDay should reset counts when date changes`() {
        limiter.incrementVoiceInput()
        limiter.incrementRefinement()
        limiter.addFileTranscriptionDuration(120)
        val tomorrow = LocalDate.now().plusDays(1)
        limiter.resetIfNewDay(tomorrow)
        assertThat(limiter.currentUsage.voiceInputCount).isEqualTo(0)
        assertThat(limiter.currentUsage.refinementCount).isEqualTo(0)
        assertThat(limiter.currentUsage.fileTranscriptionSeconds).isEqualTo(0)
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
