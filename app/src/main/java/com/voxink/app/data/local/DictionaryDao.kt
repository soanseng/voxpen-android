package com.voxink.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DictionaryDao {
    @Query("SELECT * FROM dictionary_entries ORDER BY createdAt DESC")
    fun getAll(): Flow<List<DictionaryEntry>>

    @Query("SELECT COUNT(*) FROM dictionary_entries")
    fun count(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: DictionaryEntry): Long

    @Delete
    suspend fun delete(entry: DictionaryEntry)

    @Query("SELECT word FROM dictionary_entries ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getWords(limit: Int = 500): List<String>
}
