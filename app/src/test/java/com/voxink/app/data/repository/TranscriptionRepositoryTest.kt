package com.voxink.app.data.repository

import com.google.common.truth.Truth.assertThat
import com.voxink.app.data.local.TranscriptionDao
import com.voxink.app.data.local.TranscriptionEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TranscriptionRepositoryTest {
    private lateinit var dao: TranscriptionDao
    private lateinit var repository: TranscriptionRepository

    @BeforeEach
    fun setUp() {
        dao = mockk(relaxed = true)
        repository = TranscriptionRepository(dao)
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
}
