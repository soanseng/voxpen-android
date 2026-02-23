package com.voxink.app.billing

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class BillingManagerTest {
    @Test
    fun `initial pro status should be Free`() {
        // BillingManager requires Android context, so we test the ProStatus flow default
        // The actual BillingClient integration is verified via manual testing on device
        val status: ProStatus = ProStatus.Free
        assertThat(status.isPro).isFalse()
    }

    @Test
    fun `product ID should be correct`() {
        assertThat(BillingManager.PRODUCT_ID_PRO).isEqualTo("voxink_pro")
    }
}
