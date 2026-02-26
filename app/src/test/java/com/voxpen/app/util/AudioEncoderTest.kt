package com.voxpen.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioEncoderTest {
    @Test
    fun `should produce valid WAV header with RIFF magic bytes`() {
        val pcm = ByteArray(100) { it.toByte() }
        val wav = AudioEncoder.pcmToWav(pcm, sampleRate = 16000, channels = 1, bitsPerSample = 16)
        val header = String(wav.copyOfRange(0, 4), Charsets.US_ASCII)
        assertThat(header).isEqualTo("RIFF")
    }

    @Test
    fun `should have WAVE format marker`() {
        val pcm = ByteArray(100)
        val wav = AudioEncoder.pcmToWav(pcm, sampleRate = 16000, channels = 1, bitsPerSample = 16)
        val format = String(wav.copyOfRange(8, 12), Charsets.US_ASCII)
        assertThat(format).isEqualTo("WAVE")
    }

    @Test
    fun `should have correct total file size in header`() {
        val pcm = ByteArray(200)
        val wav = AudioEncoder.pcmToWav(pcm, sampleRate = 16000, channels = 1, bitsPerSample = 16)
        val fileSize = ByteBuffer.wrap(wav, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
        // RIFF chunk size = total file size - 8 (RIFF header)
        assertThat(fileSize).isEqualTo(wav.size - 8)
    }

    @Test
    fun `should have correct data chunk size`() {
        val pcmSize = 500
        val pcm = ByteArray(pcmSize)
        val wav = AudioEncoder.pcmToWav(pcm, sampleRate = 16000, channels = 1, bitsPerSample = 16)
        // Data chunk size is at offset 40, little-endian
        val dataSize = ByteBuffer.wrap(wav, 40, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertThat(dataSize).isEqualTo(pcmSize)
    }

    @Test
    fun `should have 44-byte header before PCM data`() {
        val pcm = byteArrayOf(1, 2, 3, 4)
        val wav = AudioEncoder.pcmToWav(pcm, sampleRate = 16000, channels = 1, bitsPerSample = 16)
        assertThat(wav.size).isEqualTo(44 + pcm.size)
        // PCM data starts at offset 44
        assertThat(wav[44]).isEqualTo(1.toByte())
        assertThat(wav[45]).isEqualTo(2.toByte())
    }

    @Test
    fun `should encode sample rate in header`() {
        val pcm = ByteArray(10)
        val wav = AudioEncoder.pcmToWav(pcm, sampleRate = 16000, channels = 1, bitsPerSample = 16)
        val sampleRate = ByteBuffer.wrap(wav, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertThat(sampleRate).isEqualTo(16000)
    }

    @Test
    fun `should handle empty PCM data`() {
        val pcm = ByteArray(0)
        val wav = AudioEncoder.pcmToWav(pcm, sampleRate = 16000, channels = 1, bitsPerSample = 16)
        assertThat(wav.size).isEqualTo(44)
    }
}
