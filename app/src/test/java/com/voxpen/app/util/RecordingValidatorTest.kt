package com.voxpen.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class RecordingValidatorTest {
    @Test
    fun `short recording is too short`() {
        assertThat(RecordingValidator.validate(ByteArray(1_000)))
            .isEqualTo(RecordingValidator.Result.TooShort)
    }

    @Test
    fun `long zero recording is silent`() {
        assertThat(RecordingValidator.validate(ByteArray(32_000)))
            .isEqualTo(RecordingValidator.Result.Silent)
    }

    @Test
    fun `audible recording is valid`() {
        val pcm = AudioSilenceDetectorTest.generateSineWave(durationMs = 500)

        assertThat(RecordingValidator.validate(pcm))
            .isEqualTo(RecordingValidator.Result.Valid)
    }
}
