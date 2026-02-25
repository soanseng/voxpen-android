package com.voxink.app.domain.usecase

import com.voxink.app.data.local.PreferencesManager
import com.voxink.app.data.local.TranscriptionEntity
import com.voxink.app.data.model.SttLanguage
import com.voxink.app.data.repository.SttRepository
import com.voxink.app.data.repository.TranscriptionRepository
import com.voxink.app.util.AudioChunker
import javax.inject.Inject

class TranscribeFileUseCase
    @Inject
    constructor(
        private val sttRepository: SttRepository,
        private val transcriptionRepository: TranscriptionRepository,
    ) {
        suspend operator fun invoke(
            fileBytes: ByteArray,
            fileName: String,
            language: SttLanguage,
            apiKey: String,
            maxChunkBytes: Int = DEFAULT_MAX_CHUNK_BYTES,
        ): Result<TranscriptionEntity> {
            val chunks =
                if (AudioChunker.isWav(fileBytes)) {
                    AudioChunker.chunkWav(fileBytes, maxChunkBytes)
                } else {
                    AudioChunker.chunk(fileBytes, maxChunkBytes)
                }

            val transcriptions = mutableListOf<String>()
            for (chunk in chunks) {
                val result = sttRepository.transcribe(chunk, language, apiKey)
                result.fold(
                    onSuccess = { transcriptions.add(it) },
                    onFailure = { return Result.failure(it) },
                )
            }

            val mergedText = transcriptions.joinToString(" ")
            val languageKey = PreferencesManager.languageToKey(language)
            val entity =
                TranscriptionEntity(
                    fileName = fileName,
                    originalText = mergedText,
                    language = languageKey,
                    fileSizeBytes = fileBytes.size.toLong(),
                    createdAt = System.currentTimeMillis(),
                )
            val id = transcriptionRepository.insert(entity)
            return Result.success(entity.copy(id = id))
        }

        companion object {
            private const val DEFAULT_MAX_CHUNK_BYTES = 25 * 1024 * 1024
        }
    }
