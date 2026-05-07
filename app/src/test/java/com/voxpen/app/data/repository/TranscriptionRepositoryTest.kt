package com.voxpen.app.data.repository

import com.google.common.truth.Truth.assertThat
import com.voxpen.app.data.local.TranscriptionDao
import com.voxpen.app.data.local.TranscriptionEntity
import com.voxpen.app.data.local.RecordingStore
import com.voxpen.app.data.model.SttLanguage
import com.voxpen.app.data.model.SttProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TranscriptionRepositoryTest {
    private lateinit var dao: TranscriptionDao
    private lateinit var recordingStore: RecordingStore
    private lateinit var repository: TranscriptionRepository

    @BeforeEach
    fun setUp() {
        dao = mockk(relaxed = true)
        recordingStore = mockk(relaxed = true)
        repository = TranscriptionRepository(dao, recordingStore)
    }

    @Test
    fun `should insert transcription and return id`() =
        runTest {
            coEvery { dao.insert(any()) } returns 42L

            val entity =
                TranscriptionEntity(
                    fileName = "test.wav",
                    originalText = "Hello",
                    language = "en",
                    createdAt = 1000L,
                )
            val id = repository.insert(entity)

            assertThat(id).isEqualTo(42L)
            coVerify { dao.insert(entity) }
        }

    @Test
    fun `should return all transcriptions as flow`() =
        runTest {
            val entities =
                listOf(
                    TranscriptionEntity(
                        id = 1,
                        fileName = "a.wav",
                        originalText = "A",
                        language = "en",
                        createdAt = 2000L,
                    ),
                    TranscriptionEntity(
                        id = 2,
                        fileName = "b.wav",
                        originalText = "B",
                        language = "zh",
                        createdAt = 1000L,
                    ),
                )
            every { dao.getAll() } returns flowOf(entities)

            val result = repository.getAll().first()

            assertThat(result).hasSize(2)
            assertThat(result[0].fileName).isEqualTo("a.wav")
        }

    @Test
    fun `should get transcription by id`() =
        runTest {
            val entity =
                TranscriptionEntity(id = 1, fileName = "a.wav", originalText = "A", language = "en", createdAt = 1000L)
            coEvery { dao.getById(1L) } returns entity

            val result = repository.getById(1L)

            assertThat(result).isNotNull()
            assertThat(result?.fileName).isEqualTo("a.wav")
        }

    @Test
    fun `should return null for non-existent id`() =
        runTest {
            coEvery { dao.getById(99L) } returns null

            val result = repository.getById(99L)

            assertThat(result).isNull()
        }

    @Test
    fun `should delete transcription by id`() =
        runTest {
            coEvery { dao.getById(1L) } returns null

            repository.deleteById(1L)

            coVerify { dao.deleteById(1L) }
        }

    @Test
    fun `should update transcription`() =
        runTest {
            val entity =
                TranscriptionEntity(
                    id = 1,
                    fileName = "a.wav",
                    originalText = "A",
                    refinedText = "Polished A",
                    language = "en",
                    createdAt = 1000L,
                )

            repository.update(entity)

            coVerify { dao.update(entity) }
        }

    @Test
    fun `insertFailedLive creates retryable failed row`() =
        runTest {
            val slot = io.mockk.slot<TranscriptionEntity>()
            coEvery { dao.insert(capture(slot)) } returns 9L

            val id =
                repository.insertFailedLive(
                    audioPath = "/recordings/live.wav",
                    provider = SttProvider.OpenAI,
                    language = SttLanguage.English,
                    errorMessage = "OpenAI failed",
                    createdAt = 123L,
                )

            assertThat(id).isEqualTo(9L)
            assertThat(slot.captured.status).isEqualTo(TranscriptionEntity.STATUS_FAILED)
            assertThat(slot.captured.audioPath).isEqualTo("/recordings/live.wav")
            assertThat(slot.captured.provider).isEqualTo("openai")
        }

    @Test
    fun `deleteById removes saved audio before deleting row`() =
        runTest {
            val entity =
                TranscriptionEntity(
                    id = 1,
                    fileName = "Failed voice recording",
                    originalText = "",
                    language = "en",
                    status = TranscriptionEntity.STATUS_FAILED,
                    audioPath = "/recordings/live.wav",
                    createdAt = 1000L,
                )
            coEvery { dao.getById(1L) } returns entity

            repository.deleteById(1L)

            verify { recordingStore.delete("/recordings/live.wav") }
            coVerify { dao.deleteById(1L) }
        }

    @Test
    fun `markCompletedAfterRetry updates same row and deletes audio`() =
        runTest {
            val entity =
                TranscriptionEntity(
                    id = 1,
                    fileName = "Failed voice recording",
                    originalText = "",
                    language = "en",
                    status = TranscriptionEntity.STATUS_FAILED,
                    errorMessage = "Network failed",
                    audioPath = "/recordings/live.wav",
                    createdAt = 1000L,
                )
            val slot = io.mockk.slot<TranscriptionEntity>()
            coEvery { dao.getById(1L) } returns entity
            coEvery { dao.update(capture(slot)) } returns Unit

            val completed = repository.markCompletedAfterRetry(1L, "Retried text")

            assertThat(completed?.status).isEqualTo(TranscriptionEntity.STATUS_COMPLETED)
            assertThat(slot.captured.originalText).isEqualTo("Retried text")
            assertThat(slot.captured.audioPath).isNull()
            verify { recordingStore.delete("/recordings/live.wav") }
        }

    @Test
    fun `cleanupOrphanedRecordings removes unreferenced files`() =
        runTest {
            coEvery { dao.getAllOnce() } returns listOf(
                TranscriptionEntity(
                    id = 1,
                    fileName = "Failed voice recording",
                    originalText = "",
                    language = "en",
                    status = TranscriptionEntity.STATUS_FAILED,
                    audioPath = "/recordings/keep.wav",
                    createdAt = 1000L,
                ),
            )
            every { recordingStore.listRecordingPaths() } returns listOf(
                "/recordings/keep.wav",
                "/recordings/delete.wav",
            )

            repository.cleanupOrphanedRecordings()

            verify(exactly = 0) { recordingStore.delete("/recordings/keep.wav") }
            verify { recordingStore.delete("/recordings/delete.wav") }
        }
}
