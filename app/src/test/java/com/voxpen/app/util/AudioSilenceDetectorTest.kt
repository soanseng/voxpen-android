package com.voxpen.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin

class AudioSilenceDetectorTest {

    @Test
    fun `empty byte array is silent`() {
        assertThat(AudioSilenceDetector.isSilent(ByteArray(0))).isTrue()
    }

    @Test
    fun `very short audio below minimum duration is silent`() {
        // 0.1s at 16kHz mono 16-bit = 3200 bytes — too short for speech
        val shortPcm = ByteArray(3200)
        assertThat(AudioSilenceDetector.isSilent(shortPcm)).isTrue()
    }

    @Test
    fun `all-zero PCM data is silent`() {
        // 1 second of silence at 16kHz mono 16-bit = 32000 bytes
        val silentPcm = ByteArray(32000)
        assertThat(AudioSilenceDetector.isSilent(silentPcm)).isTrue()
    }

    @Test
    fun `low-amplitude noise is silent`() {
        // 1 second with very low amplitude random noise (< threshold)
        val pcm = ByteArray(32000)
        for (i in pcm.indices step 2) {
            // Tiny values: amplitude ~10 out of 32768
            val sample: Short = (i % 20 - 10).toShort()
            pcm[i] = (sample.toInt() and 0xFF).toByte()
            pcm[i + 1] = (sample.toInt() shr 8).toByte()
        }
        assertThat(AudioSilenceDetector.isSilent(pcm)).isTrue()
    }

    @Test
    fun `speech-level audio is not silent`() {
        // 1 second with a sine wave at speech amplitude (~5000)
        val pcm = generateSineWave(durationMs = 1000, amplitude = 5000)
        assertThat(AudioSilenceDetector.isSilent(pcm)).isFalse()
    }

    @Test
    fun `moderate amplitude audio is not silent`() {
        // 0.5 seconds at moderate amplitude (~1000)
        val pcm = generateSineWave(durationMs = 500, amplitude = 1000)
        assertThat(AudioSilenceDetector.isSilent(pcm)).isFalse()
    }

    @Test
    fun `audio just above minimum duration with speech is not silent`() {
        // 0.4 seconds of speech — above minimum duration
        val pcm = generateSineWave(durationMs = 400, amplitude = 3000)
        assertThat(AudioSilenceDetector.isSilent(pcm)).isFalse()
    }

    companion object {
        private const val SAMPLE_RATE = 16000

        fun generateSineWave(
            durationMs: Int = 500,
            amplitude: Int = 5000,
            frequencyHz: Int = 440,
            sampleRate: Int = SAMPLE_RATE,
        ): ByteArray {
            val numSamples = sampleRate * durationMs / 1000
            val bytes = ByteArray(numSamples * 2)
            for (i in 0 until numSamples) {
                val sample = (amplitude * sin(2 * PI * frequencyHz * i / sampleRate)).toInt().toShort()
                bytes[i * 2] = (sample.toInt() and 0xFF).toByte()
                bytes[i * 2 + 1] = (sample.toInt() shr 8).toByte()
            }
            return bytes
        }
    }
}
