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
    fun `Pro with GOOGLE_PLAY source should be pro`() {
        val status: ProStatus = ProStatus.Pro(ProSource.GOOGLE_PLAY)
        assertThat(status.isPro).isTrue()
    }

    @Test
    fun `Pro with LICENSE_KEY source should be pro`() {
        val status: ProStatus = ProStatus.Pro(ProSource.LICENSE_KEY)
        assertThat(status.isPro).isTrue()
    }

    @Test
    fun `Free should be distinct from Pro`() {
        assertThat(ProStatus.Free).isNotEqualTo(ProStatus.Pro(ProSource.GOOGLE_PLAY))
    }

    @Test
    fun `Pro sources should be distinguishable`() {
        val gp = ProStatus.Pro(ProSource.GOOGLE_PLAY)
        val lk = ProStatus.Pro(ProSource.LICENSE_KEY)
        assertThat(gp).isNotEqualTo(lk)
    }
}
