package com.voxpen.app.domain.usecase

import com.voxpen.app.data.model.SttLanguage
import com.voxpen.app.data.repository.SttRepository
import com.voxpen.app.util.AudioEncoder
import javax.inject.Inject

class TranscribeAudioUseCase
    @Inject
    constructor(
        private val sttRepository: SttRepository,
    ) {
        suspend operator fun invoke(
            pcmData: ByteArray,
            language: SttLanguage,
            apiKey: String,
            model: String = "whisper-large-v3-turbo",
            vocabularyHint: String? = null,
            sampleRate: Int = SAMPLE_RATE,
            channels: Int = CHANNELS,
            bitsPerSample: Int = BITS_PER_SAMPLE,
        ): Result<String> {
            val wavBytes = AudioEncoder.pcmToWav(pcmData, sampleRate, channels, bitsPerSample)
            return sttRepository.transcribe(wavBytes, language, apiKey, model, vocabularyHint)
        }

        companion object {
            const val SAMPLE_RATE = 16000
            const val CHANNELS = 1
            const val BITS_PER_SAMPLE = 16
        }
    }
