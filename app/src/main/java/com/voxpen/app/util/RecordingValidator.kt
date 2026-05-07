package com.voxpen.app.util

object RecordingValidator {
    private const val SAMPLE_RATE = 16_000
    private const val BYTES_PER_SAMPLE = 2
    private const val MIN_DURATION_SECONDS = 0.3

    sealed interface Result {
        data object Valid : Result
        data object TooShort : Result
        data object Silent : Result
    }

    fun validate(pcmData: ByteArray): Result {
        val minBytes = (SAMPLE_RATE * BYTES_PER_SAMPLE * MIN_DURATION_SECONDS).toInt()
        if (pcmData.size < minBytes) return Result.TooShort
        if (AudioSilenceDetector.isSilent(pcmData)) return Result.Silent
        return Result.Valid
    }
}
