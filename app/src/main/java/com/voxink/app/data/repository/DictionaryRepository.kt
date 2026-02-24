package com.voxink.app.data.repository

import com.voxink.app.data.local.DictionaryDao
import com.voxink.app.data.local.DictionaryEntry
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryRepository
    @Inject
    constructor(
        private val dao: DictionaryDao,
    ) {
        fun getAll(): Flow<List<DictionaryEntry>> = dao.getAll()

        fun count(): Flow<Int> = dao.count()

        suspend fun add(word: String): Long =
            dao.insert(
                DictionaryEntry(
                    word = word.trim(),
                    createdAt = System.currentTimeMillis(),
                ),
            )

        suspend fun remove(entry: DictionaryEntry) = dao.delete(entry)

        suspend fun getWords(limit: Int = 500): List<String> = dao.getWords(limit)
    }
