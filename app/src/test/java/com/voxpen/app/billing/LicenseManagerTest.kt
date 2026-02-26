package com.voxpen.app.billing

import android.content.SharedPreferences
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.voxpen.app.data.remote.InstanceInfo
import com.voxpen.app.data.remote.LemonSqueezyApi
import com.voxpen.app.data.remote.LicenseKeyInfo
import com.voxpen.app.data.remote.LicenseMeta
import com.voxpen.app.data.remote.LicenseResponse
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LicenseManagerTest {
    private val api: LemonSqueezyApi = mockk()
    private val prefs: SharedPreferences = mockk(relaxed = true)
    private val editor: SharedPreferences.Editor = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var manager: LicenseManager

    private val validResponse = LicenseResponse(
        valid = true,
        licenseKey = LicenseKeyInfo(id = 1, key = "test-key", status = "active"),
        instance = InstanceInfo(id = "inst-123"),
        meta = LicenseMeta(productName = "VoxInk Bundle"),
    )

    private val invalidResponse = LicenseResponse(
        valid = false,
        licenseKey = LicenseKeyInfo(id = 1, key = "test-key", status = "disabled"),
    )

    @BeforeEach
    fun setUp() {
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.putLong(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        every { editor.apply() } returns Unit
        every { prefs.getString("license_key", null) } returns null
        every { prefs.getString("license_instance_id", null) } returns null
        every { prefs.getBoolean("license_valid", false) } returns false
        every { prefs.getLong("license_validated_at", 0L) } returns 0L
        every { prefs.getString("license_product_name", null) } returns null

        manager = LicenseManager(api, prefs, "android-test-device", testDispatcher)
    }

    @Test
    fun `initial proStatus should be Free when no cached license`() = runTest {
        manager.proStatus.test {
            assertThat(awaitItem()).isEqualTo(ProStatus.Free)
        }
    }

    @Test
    fun `initial proStatus should be Pro when valid cached license exists`() = runTest {
        every { prefs.getString("license_key", null) } returns "cached-key"
        every { prefs.getBoolean("license_valid", false) } returns true
        val cachedManager = LicenseManager(api, prefs, "android-test-device", testDispatcher)

        cachedManager.proStatus.test {
            assertThat(awaitItem()).isEqualTo(ProStatus.Pro(ProSource.LICENSE_KEY))
        }
    }

    @Test
    fun `activateLicense should set Pro on valid response`() = runTest {
        coEvery { api.activateLicense(any()) } returns validResponse

        manager.proStatus.test {
            assertThat(awaitItem()).isEqualTo(ProStatus.Free)
            val result = manager.activateLicense("test-key")
            assertThat(result.isSuccess).isTrue()
            assertThat(awaitItem()).isEqualTo(ProStatus.Pro(ProSource.LICENSE_KEY))
        }
    }

    @Test
    fun `activateLicense should return failure on invalid response`() = runTest {
        coEvery { api.activateLicense(any()) } returns invalidResponse

        val result = manager.activateLicense("bad-key")
        assertThat(result.isFailure).isTrue()

        manager.proStatus.test {
            assertThat(awaitItem()).isEqualTo(ProStatus.Free)
        }
    }

    @Test
    fun `activateLicense should return failure on network error`() = runTest {
        coEvery { api.activateLicense(any()) } throws IOException("No internet")

        val result = manager.activateLicense("test-key")
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("No internet")
    }

    @Test
    fun `activateLicense should cache license data on success`() = runTest {
        coEvery { api.activateLicense(any()) } returns validResponse

        manager.activateLicense("test-key")

        verify { editor.putString("license_key", "test-key") }
        verify { editor.putString("license_instance_id", "inst-123") }
        verify { editor.putBoolean("license_valid", true) }
        verify { editor.putString("license_product_name", "VoxInk Bundle") }
    }

    @Test
    fun `validateCachedLicense should keep Pro on valid response`() = runTest {
        every { prefs.getString("license_key", null) } returns "cached-key"
        every { prefs.getString("license_instance_id", null) } returns "inst-123"
        every { prefs.getBoolean("license_valid", false) } returns true
        val cachedManager = LicenseManager(api, prefs, "android-test-device", testDispatcher)

        coEvery { api.validateLicense(any()) } returns validResponse

        cachedManager.validateCachedLicense()

        cachedManager.proStatus.test {
            assertThat(awaitItem()).isEqualTo(ProStatus.Pro(ProSource.LICENSE_KEY))
        }
    }

    @Test
    fun `validateCachedLicense should revoke on invalid response`() = runTest {
        every { prefs.getString("license_key", null) } returns "cached-key"
        every { prefs.getString("license_instance_id", null) } returns "inst-123"
        every { prefs.getBoolean("license_valid", false) } returns true
        val cachedManager = LicenseManager(api, prefs, "android-test-device", testDispatcher)

        coEvery { api.validateLicense(any()) } returns invalidResponse

        cachedManager.proStatus.test {
            assertThat(awaitItem()).isEqualTo(ProStatus.Pro(ProSource.LICENSE_KEY))
            cachedManager.validateCachedLicense()
            assertThat(awaitItem()).isEqualTo(ProStatus.Free)
        }

        verify { editor.remove("license_key") }
    }

    @Test
    fun `validateCachedLicense should keep Pro on network error`() = runTest {
        every { prefs.getString("license_key", null) } returns "cached-key"
        every { prefs.getString("license_instance_id", null) } returns "inst-123"
        every { prefs.getBoolean("license_valid", false) } returns true
        val cachedManager = LicenseManager(api, prefs, "android-test-device", testDispatcher)

        coEvery { api.validateLicense(any()) } throws IOException("No internet")

        cachedManager.validateCachedLicense()

        cachedManager.proStatus.test {
            assertThat(awaitItem()).isEqualTo(ProStatus.Pro(ProSource.LICENSE_KEY))
        }
    }

    @Test
    fun `deactivateLicense should clear Pro status`() = runTest {
        coEvery { api.activateLicense(any()) } returns validResponse
        coEvery { api.deactivateLicense(any()) } returns validResponse

        manager.activateLicense("test-key")

        // After activation, prefs now has cached license data
        every { prefs.getString("license_key", null) } returns "test-key"
        every { prefs.getString("license_instance_id", null) } returns "inst-123"

        manager.proStatus.test {
            assertThat(awaitItem()).isEqualTo(ProStatus.Pro(ProSource.LICENSE_KEY))
            manager.deactivateLicense()
            assertThat(awaitItem()).isEqualTo(ProStatus.Free)
        }
    }
}
