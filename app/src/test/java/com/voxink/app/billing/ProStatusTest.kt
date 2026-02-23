package com.voxink.app.billing

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ProStatusTest {
    @Test
    fun `Free status should not be pro`() {
        val status: ProStatus = ProStatus.Free
        assertThat(status.isPro).isFalse()
    }

    @Test
    fun `Pro status should be pro`() {
        val status: ProStatus = ProStatus.Pro
        assertThat(status.isPro).isTrue()
    }

    @Test
    fun `Free should be distinct from Pro`() {
        assertThat(ProStatus.Free).isNotEqualTo(ProStatus.Pro)
    }
}
