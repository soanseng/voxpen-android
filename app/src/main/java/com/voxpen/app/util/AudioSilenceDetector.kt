package com.voxpen.app.util

import kotlin.math.sqrt

object AudioSilenceDetector {

    private const val SAMPLE_RATE = 16000
    private const val BYTES_PER_SAMPLE = 2 // 16-bit PCM
    private const val MIN_DURATION_SECONDS = 0.3
    private const val SILENCE_RMS_THRESHOLD = 200

    /**
     * Checks whether [pcmData] (16-bit mono little-endian PCM at 16 kHz) represents
     * silence or is too short to contain meaningful speech.
     *
     * Returns `true` when the recording should be discarded instead of sent to STT.
     */
    fun isSilent(pcmData: ByteArray): Boolean {
        val minBytes = (SAMPLE_RATE * BYTES_PER_SAMPLE * MIN_DURATION_SECONDS).toInt()
        if (pcmData.size < minBytes) return true

        val numSamples = pcmData.size / BYTES_PER_SAMPLE
        if (numSamples == 0) return true

        var sumSquared = 0.0
        for (i in 0 until numSamples) {
            val offset = i * BYTES_PER_SAMPLE
            val low = pcmData[offset].toInt() and 0xFF
            val high = pcmData[offset + 1].toInt() // sign-extends
            val sample = (high shl 8) or low
            sumSquared += sample.toDouble() * sample.toDouble()
        }

        val rms = sqrt(sumSquared / numSamples)
        return rms < SILENCE_RMS_THRESHOLD
    }
}
