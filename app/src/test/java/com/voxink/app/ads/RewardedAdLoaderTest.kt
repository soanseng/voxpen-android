package com.voxink.app.ads

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class RewardedAdLoaderTest {
    @Test
    fun `isLoaded should be false initially`() {
        val loader = RewardedAdLoader()
        assertThat(loader.isLoaded).isFalse()
    }
}
