package com.voxink.app.data.repository

import com.google.common.truth.Truth.assertThat
import com.voxink.app.data.local.DictionaryDao
import com.voxink.app.data.local.DictionaryEntry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DictionaryRepositoryTest {
    private val dao: DictionaryDao = mockk()
    private lateinit var repository: DictionaryRepository

    @BeforeEach
    fun setUp() {
        repository = DictionaryRepository(dao)
    }

    @Test
    fun `should return all entries from dao`() =
        runTest {
            val entries =
                listOf(
                    DictionaryEntry(id = 1, word = "語墨", createdAt = 2000L),
                    DictionaryEntry(id = 2, word = "Anthropic", createdAt = 1000L),
                )
            every { dao.getAll() } returns flowOf(entries)

            val result = repository.getAll().first()

            assertThat(result).hasSize(2)
            assertThat(result[0].word).isEqualTo("語墨")
        }

    @Test
    fun `should return count from dao`() =
        runTest {
            every { dao.count() } returns flowOf(5)

            val result = repository.count().first()

            assertThat(result).isEqualTo(5)
        }

    @Test
    fun `should trim word before inserting`() =
        runTest {
            val slot = slot<DictionaryEntry>()
            coEvery { dao.insert(capture(slot)) } returns 1L

            repository.add("  語墨  ")

            assertThat(slot.captured.word).isEqualTo("語墨")
            assertThat(slot.captured.createdAt).isGreaterThan(0L)
        }

    @Test
    fun `should delegate delete to dao`() =
        runTest {
            val entry = DictionaryEntry(id = 1, word = "test", createdAt = 1000L)
            coEvery { dao.delete(entry) } returns Unit

            repository.remove(entry)

            coVerify { dao.delete(entry) }
        }

    @Test
    fun `should return words with limit from dao`() =
        runTest {
            coEvery { dao.getWords(80) } returns listOf("語墨", "Claude")

            val result = repository.getWords(80)

            assertThat(result).containsExactly("語墨", "Claude")
        }
}
