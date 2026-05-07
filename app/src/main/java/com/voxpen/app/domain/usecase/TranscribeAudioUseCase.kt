package com.voxpen.app.domain.usecase

import com.voxpen.app.data.model.SttLanguage
import com.voxpen.app.data.model.SttProvider
import com.voxpen.app.data.repository.SttRepository
import com.voxpen.app.util.AudioEncoder
import com.voxpen.app.util.LiveAudioChunker
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
            provider: SttProvider = SttProvider.DEFAULT,
            customSttBaseUrl: String? = null,
        ): Result<String> {
            val textChunks = mutableListOf<String>()
            val chunks =
                LiveAudioChunker.chunkPcm(
                    pcmData = pcmData,
                    channels = channels,
                    bitsPerSample = bitsPerSample,
                )
            for (chunk in chunks) {
                val wavBytes = AudioEncoder.pcmToWav(chunk, sampleRate, channels, bitsPerSample)
                val result =
                    sttRepository.transcribe(
                        wavBytes = wavBytes,
                        language = language,
                        apiKey = apiKey,
                        model = model,
                        vocabularyHint = vocabularyHint,
                        provider = provider,
                        customSttBaseUrl = customSttBaseUrl,
                    )
                result.fold(
                    onSuccess = { textChunks.add(it.text) },
                    onFailure = { return Result.failure(it) },
                )
            }
            return Result.success(textChunks.filter { it.isNotBlank() }.joinToString(" "))
        }

        companion object {
            const val SAMPLE_RATE = 16000
            const val CHANNELS = 1
            const val BITS_PER_SAMPLE = 16
        }
    }
