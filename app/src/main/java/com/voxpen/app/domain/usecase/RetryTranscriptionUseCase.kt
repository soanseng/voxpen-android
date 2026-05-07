package com.voxpen.app.domain.usecase

import com.voxpen.app.data.local.PreferencesManager
import com.voxpen.app.data.local.RecordingStore
import com.voxpen.app.data.local.TranscriptionEntity
import com.voxpen.app.data.model.SttProvider
import com.voxpen.app.data.repository.SttRepository
import com.voxpen.app.data.repository.TranscriptionRepository
import com.voxpen.app.util.AudioChunker
import com.voxpen.app.util.LiveAudioChunker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class RetryTranscriptionUseCase(
    private val transcriptionRepository: TranscriptionRepository,
    private val recordingStore: RecordingStore,
    private val sttRepository: SttRepository,
    private val ioDispatcher: CoroutineDispatcher,
) {
    @Inject
    constructor(
        transcriptionRepository: TranscriptionRepository,
        recordingStore: RecordingStore,
        sttRepository: SttRepository,
    ) : this(
        transcriptionRepository = transcriptionRepository,
        recordingStore = recordingStore,
        sttRepository = sttRepository,
        ioDispatcher = Dispatchers.IO,
    )

    suspend operator fun invoke(
        id: Long,
        apiKey: String,
        provider: SttProvider,
        model: String,
        customSttBaseUrl: String? = null,
    ): Result<TranscriptionEntity> {
        val entity =
            transcriptionRepository.getById(id)
                ?: return Result.failure(IllegalArgumentException("Transcription not found"))
        val audioPath = entity.audioPath
        if (audioPath.isNullOrBlank() || !recordingStore.exists(audioPath)) {
            return Result.failure(IllegalStateException("Saved audio is not available"))
        }

        val language = PreferencesManager.languageFromKey(entity.language)
        val chunks =
            withContext(ioDispatcher) {
                val wavBytes = recordingStore.read(audioPath)
                AudioChunker.chunkWav(wavBytes, LiveAudioChunker.CHUNK_BYTES + WAV_HEADER_BYTES)
            }
        val transcriptions = mutableListOf<String>()
        for (chunk in chunks) {
            val result =
                sttRepository.transcribe(
                    wavBytes = chunk,
                    language = language,
                    apiKey = apiKey,
                    model = model,
                    provider = provider,
                    customSttBaseUrl = customSttBaseUrl,
                )
            result.fold(
                onSuccess = { transcriptions.add(it.text) },
                onFailure = { return Result.failure(it) },
            )
        }
        val text = transcriptions.filter { it.isNotBlank() }.joinToString(" ")
        val completed =
            transcriptionRepository.markCompletedAfterRetry(id, text)
                ?: return Result.failure(IllegalArgumentException("Transcription not found"))
        return Result.success(completed)
    }

    companion object {
        private const val WAV_HEADER_BYTES = 44
    }
}
