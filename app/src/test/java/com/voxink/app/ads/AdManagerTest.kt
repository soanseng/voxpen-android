package com.voxink.app.ads

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class AdManagerTest {
    @Test
    fun `banner ad unit ID should be test ID`() {
        assertThat(AdManager.BANNER_AD_UNIT_ID).startsWith("ca-app-pub-3940256099942544")
    }

    @Test
    fun `interstitial ad unit ID should be test ID`() {
        assertThat(AdManager.INTERSTITIAL_AD_UNIT_ID).startsWith("ca-app-pub-3940256099942544")
    }

    @Test
    fun `rewarded ad unit ID should be test ID`() {
        assertThat(AdManager.REWARDED_AD_UNIT_ID).startsWith("ca-app-pub-3940256099942544")
    }
}
