package com.voxpen.app.data.repository

import com.voxpen.app.data.local.TranscriptionDao
import com.voxpen.app.data.local.TranscriptionEntity
import com.voxpen.app.data.local.RecordingStore
import com.voxpen.app.data.model.SttLanguage
import com.voxpen.app.data.model.SttProvider
import com.voxpen.app.data.local.PreferencesManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranscriptionRepository
    @Inject
    constructor(
        private val dao: TranscriptionDao,
        private val recordingStore: RecordingStore,
    ) {
        suspend fun insert(entity: TranscriptionEntity): Long = dao.insert(entity)

        fun getAll(): Flow<List<TranscriptionEntity>> = dao.getAll()

        suspend fun getById(id: Long): TranscriptionEntity? = dao.getById(id)

        suspend fun deleteById(id: Long) {
            val entity = dao.getById(id)
            recordingStore.delete(entity?.audioPath)
            dao.deleteById(id)
        }

        suspend fun update(entity: TranscriptionEntity) = dao.update(entity)

        suspend fun insertFailedLive(
            audioPath: String,
            provider: SttProvider,
            language: SttLanguage,
            errorMessage: String,
            createdAt: Long = System.currentTimeMillis(),
        ): Long =
            dao.insert(
                TranscriptionEntity(
                    fileName = "Failed voice recording",
                    originalText = "",
                    language = PreferencesManager.languageToKey(language),
                    status = TranscriptionEntity.STATUS_FAILED,
                    errorMessage = errorMessage,
                    audioPath = audioPath,
                    provider = provider.key,
                    createdAt = createdAt,
                ),
            )

        suspend fun markCompletedAfterRetry(
            id: Long,
            text: String,
        ): TranscriptionEntity? {
            val existing = dao.getById(id) ?: return null
            val completed =
                existing.copy(
                    originalText = text,
                    refinedText = null,
                    status = TranscriptionEntity.STATUS_COMPLETED,
                    errorMessage = null,
                    audioPath = null,
                )
            dao.update(completed)
            recordingStore.delete(existing.audioPath)
            return completed
        }

        suspend fun cleanupOrphanedRecordings() {
            val referenced = dao.getAllOnce().mapNotNull { it.audioPath }.toSet()
            recordingStore.listRecordingPaths()
                .filterNot { it in referenced }
                .forEach { recordingStore.delete(it) }
        }
    }
