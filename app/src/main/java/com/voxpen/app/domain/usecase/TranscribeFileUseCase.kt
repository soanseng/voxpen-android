package com.voxpen.app.domain.usecase

import com.voxpen.app.data.local.PreferencesManager
import com.voxpen.app.data.local.TranscriptionEntity
import com.voxpen.app.data.model.LlmProvider
import com.voxpen.app.data.model.SttLanguage
import com.voxpen.app.data.model.ToneStyle
import com.voxpen.app.data.repository.SttRepository
import com.voxpen.app.data.repository.TranscriptionRepository
import com.voxpen.app.util.AudioChunker
import javax.inject.Inject

class TranscribeFileUseCase
    @Inject
    constructor(
        private val sttRepository: SttRepository,
        private val transcriptionRepository: TranscriptionRepository,
        private val refineTextUseCase: RefineTextUseCase,
    ) {
        suspend operator fun invoke(
            fileBytes: ByteArray,
            fileName: String,
            language: SttLanguage,
            apiKey: String,
            maxChunkBytes: Int = DEFAULT_MAX_CHUNK_BYTES,
            refinementApiKey: String? = null,
            llmModel: String? = null,
            llmProvider: LlmProvider? = null,
            customLlmBaseUrl: String? = null,
            tone: ToneStyle = ToneStyle.Casual,
            vocabulary: List<String> = emptyList(),
            customPrompt: String? = null,
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

            val refinedText = if (!refinementApiKey.isNullOrBlank() && llmProvider != null && llmModel != null) {
                refineTextUseCase(
                    text = mergedText,
                    language = language,
                    apiKey = refinementApiKey,
                    model = llmModel,
                    vocabulary = vocabulary,
                    customPrompt = customPrompt,
                    tone = tone,
                    provider = llmProvider,
                    customBaseUrl = customLlmBaseUrl,
                ).getOrNull()
            } else {
                null
            }

            val languageKey = PreferencesManager.languageToKey(language)
            val entity =
                TranscriptionEntity(
                    fileName = fileName,
                    originalText = mergedText,
                    refinedText = refinedText,
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
