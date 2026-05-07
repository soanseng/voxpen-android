package com.voxpen.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migration3To4AddsFailedRetryColumns() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS transcriptions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    fileName TEXT NOT NULL,
                    originalText TEXT NOT NULL,
                    refinedText TEXT,
                    language TEXT NOT NULL,
                    durationMs INTEGER,
                    fileSizeBytes INTEGER,
                    segmentsJson TEXT,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO transcriptions (
                    fileName, originalText, language, createdAt
                ) VALUES ('old.wav', 'hello', 'en', 100)
                """.trimIndent(),
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS dictionary_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    word TEXT NOT NULL,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_dictionary_entries_word ON dictionary_entries (word)")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, AppDatabase.MIGRATION_3_4)
        val cursor =
            db.query("SELECT status, errorMessage, audioPath, provider FROM transcriptions WHERE fileName = 'old.wav'")
        try {
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo(TranscriptionEntity.STATUS_COMPLETED)
            assertThat(cursor.isNull(1)).isTrue()
            assertThat(cursor.isNull(2)).isTrue()
            assertThat(cursor.isNull(3)).isTrue()
        } finally {
            cursor.close()
        }
    }

    private companion object {
        private const val TEST_DB = "migration-test"
    }
}
