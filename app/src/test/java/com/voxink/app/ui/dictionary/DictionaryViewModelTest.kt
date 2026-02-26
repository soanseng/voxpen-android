package com.voxink.app.ui.dictionary

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.voxink.app.billing.BillingManager
import com.voxink.app.billing.ProSource
import com.voxink.app.billing.ProStatus
import com.voxink.app.data.local.DictionaryEntry
import com.voxink.app.data.repository.DictionaryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DictionaryViewModelTest {
    private val repository: DictionaryRepository = mockk()
    private val billingManager: BillingManager = mockk()
    private val testDispatcher = UnconfinedTestDispatcher()

    private val entriesFlow = MutableStateFlow<List<DictionaryEntry>>(emptyList())
    private val countFlow = MutableStateFlow(0)
    private val proStatusFlow = MutableStateFlow<ProStatus>(ProStatus.Free)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { repository.getAll() } returns entriesFlow
        every { repository.count() } returns countFlow
        every { billingManager.proStatus } returns proStatusFlow
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = DictionaryViewModel(repository, billingManager)

    @Test
    fun `should expose entries from repository`() =
        runTest {
            val vm = createViewModel()
            val entry = DictionaryEntry(id = 1, word = "語墨", createdAt = 1000L)
            entriesFlow.value = listOf(entry)

            vm.entries.test {
                assertThat(awaitItem()).containsExactly(entry)
            }
        }

    @Test
    fun `should expose count from repository`() =
        runTest {
            val vm = createViewModel()
            countFlow.value = 3

            vm.count.test {
                assertThat(awaitItem()).isEqualTo(3)
            }
        }

    @Test
    fun `should expose isPro from billing manager`() =
        runTest {
            val vm = createViewModel()
            proStatusFlow.value = ProStatus.Pro(ProSource.GOOGLE_PLAY)

            vm.isPro.test {
                assertThat(awaitItem()).isTrue()
            }
        }

    @Test
    fun `should add word via repository`() =
        runTest {
            coEvery { repository.add(any()) } returns 1L
            val vm = createViewModel()

            vm.addWord("語墨")

            coVerify { repository.add("語墨") }
        }

    @Test
    fun `should not add blank word`() =
        runTest {
            val vm = createViewModel()

            vm.addWord("   ")

            coVerify(exactly = 0) { repository.add(any()) }
        }

    @Test
    fun `should detect duplicate when insert returns negative one`() =
        runTest {
            coEvery { repository.add(any()) } returns -1L
            val vm = createViewModel()

            vm.addWord("語墨")

            vm.showDuplicateToast.test {
                assertThat(awaitItem()).isTrue()
            }
        }

    @Test
    fun `should remove entry via repository`() =
        runTest {
            val entry = DictionaryEntry(id = 1, word = "test", createdAt = 1000L)
            coEvery { repository.remove(entry) } returns Unit
            val vm = createViewModel()

            vm.removeWord(entry)

            coVerify { repository.remove(entry) }
        }

    @Test
    fun `should report limit reached for Free user at 10 entries`() =
        runTest {
            val vm = createViewModel()
            countFlow.value = 10
            proStatusFlow.value = ProStatus.Free

            vm.isLimitReached.test {
                assertThat(awaitItem()).isTrue()
            }
        }

    @Test
    fun `should not report limit reached for Pro user`() =
        runTest {
            val vm = createViewModel()
            countFlow.value = 100
            proStatusFlow.value = ProStatus.Pro(ProSource.GOOGLE_PLAY)

            vm.isLimitReached.test {
                assertThat(awaitItem()).isFalse()
            }
        }

    @Test
    fun `should not add word when limit is reached for Free user`() =
        runTest {
            val vm = createViewModel()
            countFlow.value = 10
            proStatusFlow.value = ProStatus.Free

            vm.isLimitReached.test {
                assertThat(awaitItem()).isTrue()
            }

            vm.addWord("語墨")

            coVerify(exactly = 0) { repository.add(any()) }
        }
}
