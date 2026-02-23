package com.voxink.app.ads

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class InterstitialAdLoaderTest {
    @Test
    fun `frequency constants should be correct`() {
        assertThat(InterstitialAdLoader.MIN_INTERVAL_MS).isEqualTo(5 * 60 * 1000L)
        assertThat(InterstitialAdLoader.MAX_DAILY_SHOWS).isEqualTo(3)
        assertThat(InterstitialAdLoader.GRACE_PERIOD_MS).isEqualTo(3 * 24 * 60 * 60 * 1000L)
    }
}
