package com.voxpen.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TranscriptionEntity::class, DictionaryEntry::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transcriptionDao(): TranscriptionDao

    abstract fun dictionaryDao(): DictionaryDao

    companion object {
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """CREATE TABLE IF NOT EXISTS dictionary_entries (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            word TEXT NOT NULL,
                            createdAt INTEGER NOT NULL
                        )""",
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS index_dictionary_entries_word ON dictionary_entries (word)",
                    )
                }
            }

        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE transcriptions ADD COLUMN segmentsJson TEXT DEFAULT NULL")
                }
            }

        val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE transcriptions ADD COLUMN status TEXT NOT NULL DEFAULT 'completed'")
                    db.execSQL("ALTER TABLE transcriptions ADD COLUMN errorMessage TEXT DEFAULT NULL")
                    db.execSQL("ALTER TABLE transcriptions ADD COLUMN audioPath TEXT DEFAULT NULL")
                    db.execSQL("ALTER TABLE transcriptions ADD COLUMN provider TEXT DEFAULT NULL")
                }
            }
    }
}
