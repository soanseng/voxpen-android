package com.voxpen.app.data.repository

import com.voxpen.app.data.local.TranscriptionDao
import com.voxpen.app.data.local.TranscriptionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranscriptionRepository
    @Inject
    constructor(
        private val dao: TranscriptionDao,
    ) {
        suspend fun insert(entity: TranscriptionEntity): Long = dao.insert(entity)

        fun getAll(): Flow<List<TranscriptionEntity>> = dao.getAll()

        suspend fun getById(id: Long): TranscriptionEntity? = dao.getById(id)

        suspend fun deleteById(id: Long) = dao.deleteById(id)

        suspend fun update(entity: TranscriptionEntity) = dao.update(entity)
    }
