package com.voxink.app

import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class SmokeTest {
    @Test
    fun `should verify JUnit 5 and Truth work`() {
        assertThat(1 + 1).isEqualTo(2)
    }

    @Test
    fun `should verify MockK works`() {
        val callback: () -> Unit = mockk(relaxed = true)
        callback()
        verify(exactly = 1) { callback() }
    }
}
