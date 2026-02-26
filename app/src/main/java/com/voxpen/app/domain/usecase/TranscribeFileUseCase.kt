package com.voxpen.app.domain.usecase

import com.voxpen.app.data.local.PreferencesManager
import com.voxpen.app.data.local.TranscriptionEntity
import com.voxpen.app.data.model.LlmProvider
import com.voxpen.app.data.model.SttLanguage
import com.voxpen.app.data.model.ToneStyle
import com.voxpen.app.data.repository.SttRepository
import com.voxpen.app.data.repository.TranscriptionRepository
import com.voxpen.app.data.repository.TranscriptionSegment
import com.voxpen.app.util.AudioChunker
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
            val allSegments = mutableListOf<TranscriptionSegment>()
            var segmentOffsetMs = 0L

            for (chunk in chunks) {
                val result = sttRepository.transcribe(chunk, language, apiKey)
                result.fold(
                    onSuccess = { tr ->
                        transcriptions.add(tr.text)
                        tr.segments.forEach { seg ->
                            allSegments.add(
                                TranscriptionSegment(
                                    startMs = seg.startMs + segmentOffsetMs,
                                    endMs = seg.endMs + segmentOffsetMs,
                                    text = seg.text,
                                ),
                            )
                        }
                        tr.segments.lastOrNull()?.let { segmentOffsetMs += it.endMs }
                    },
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

            val segmentsJson = if (allSegments.isNotEmpty()) {
                Json.encodeToString(allSegments.map { StoredSegment(it.startMs, it.endMs, it.text) })
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
                    segmentsJson = segmentsJson,
                    createdAt = System.currentTimeMillis(),
                )
            val id = transcriptionRepository.insert(entity)
            return Result.success(entity.copy(id = id))
        }

        companion object {
            private const val DEFAULT_MAX_CHUNK_BYTES = 25 * 1024 * 1024
        }
    }

@Serializable
private data class StoredSegment(val s: Long, val e: Long, val t: String)
