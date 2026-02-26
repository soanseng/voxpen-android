package com.voxpen.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dictionary_entries",
    indices = [Index(value = ["word"], unique = true)],
)
data class DictionaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val createdAt: Long,
)
