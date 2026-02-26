package com.voxpen.app.billing

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProStatusResolverTest {
    private val billingFlow = MutableStateFlow<ProStatus>(ProStatus.Free)
    private val licenseFlow = MutableStateFlow<ProStatus>(ProStatus.Free)
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var resolver: ProStatusResolver

    @BeforeEach
    fun setUp() {
        resolver = ProStatusResolver(billingFlow, licenseFlow, testScope)
    }

    @Test
    fun `should be Free when both sources are Free`() = runTest {
        resolver.proStatus.test {
            assertThat(awaitItem()).isEqualTo(ProStatus.Free)
        }
    }

    @Test
    fun `should be Pro GOOGLE_PLAY when billing is Pro`() = runTest {
        billingFlow.value = ProStatus.Pro(ProSource.GOOGLE_PLAY)
        resolver.proStatus.test {
            assertThat(awaitItem()).isEqualTo(ProStatus.Pro(ProSource.GOOGLE_PLAY))
        }
    }

    @Test
    fun `should be Pro LICENSE_KEY when license is Pro`() = runTest {
        licenseFlow.value = ProStatus.Pro(ProSource.LICENSE_KEY)
        resolver.proStatus.test {
            assertThat(awaitItem()).isEqualTo(ProStatus.Pro(ProSource.LICENSE_KEY))
        }
    }

    @Test
    fun `should prefer GOOGLE_PLAY when both are Pro`() = runTest {
        billingFlow.value = ProStatus.Pro(ProSource.GOOGLE_PLAY)
        licenseFlow.value = ProStatus.Pro(ProSource.LICENSE_KEY)
        resolver.proStatus.test {
            assertThat(awaitItem()).isEqualTo(ProStatus.Pro(ProSource.GOOGLE_PLAY))
        }
    }

    @Test
    fun `should revert to Free when Pro source reverts`() = runTest {
        resolver.proStatus.test {
            assertThat(awaitItem()).isEqualTo(ProStatus.Free)
            licenseFlow.value = ProStatus.Pro(ProSource.LICENSE_KEY)
            assertThat(awaitItem()).isEqualTo(ProStatus.Pro(ProSource.LICENSE_KEY))
            licenseFlow.value = ProStatus.Free
            assertThat(awaitItem()).isEqualTo(ProStatus.Free)
        }
    }
}
