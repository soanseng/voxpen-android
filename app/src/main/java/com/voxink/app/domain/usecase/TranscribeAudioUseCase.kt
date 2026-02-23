package com.voxink.app.domain.usecase

import com.voxink.app.data.model.SttLanguage
import com.voxink.app.data.repository.SttRepository
import com.voxink.app.util.AudioEncoder
import javax.inject.Inject

class TranscribeAudioUseCase @Inject constructor(
    private val sttRepository: SttRepository,
) {

    suspend operator fun invoke(
        pcmData: ByteArray,
        language: SttLanguage,
        apiKey: String,
        sampleRate: Int = SAMPLE_RATE,
        channels: Int = CHANNELS,
        bitsPerSample: Int = BITS_PER_SAMPLE,
    ): Result<String> {
        val wavBytes = AudioEncoder.pcmToWav(pcmData, sampleRate, channels, bitsPerSample)
        return sttRepository.transcribe(wavBytes, language, apiKey)
    }

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNELS = 1
        const val BITS_PER_SAMPLE = 16
    }
}
