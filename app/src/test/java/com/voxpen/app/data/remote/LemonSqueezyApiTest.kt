package com.voxpen.app.data.remote

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class LemonSqueezyApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: LemonSqueezyApi
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(LemonSqueezyApi::class.java)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `activateLicense should send correct request`() = runTest {
        server.enqueue(MockResponse().setBody("""
            {
                "valid": true,
                "license_key": {
                    "id": 123,
                    "key": "test-key-123",
                    "status": "active"
                },
                "instance": {
                    "id": "inst-abc"
                },
                "meta": {
                    "product_name": "VoxInk Bundle"
                }
            }
        """.trimIndent()))

        val result = api.activateLicense(
            ActivateLicenseRequest(licenseKey = "test-key-123", instanceName = "android-device1"),
        )
        assertThat(result.valid).isTrue()
        assertThat(result.instance?.id).isEqualTo("inst-abc")
        assertThat(result.meta?.productName).isEqualTo("VoxInk Bundle")

        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/v1/licenses/activate")
        assertThat(request.method).isEqualTo("POST")
    }

    @Test
    fun `validateLicense should parse valid response`() = runTest {
        server.enqueue(MockResponse().setBody("""
            {
                "valid": true,
                "license_key": {
                    "id": 123,
                    "key": "test-key-123",
                    "status": "active"
                },
                "instance": null,
                "meta": {
                    "product_name": "VoxInk Bundle"
                }
            }
        """.trimIndent()))

        val result = api.validateLicense(
            ValidateLicenseRequest(licenseKey = "test-key-123", instanceId = "inst-abc"),
        )
        assertThat(result.valid).isTrue()
    }

    @Test
    fun `validateLicense should parse invalid response`() = runTest {
        server.enqueue(MockResponse().setBody("""
            {
                "valid": false,
                "license_key": {
                    "id": 123,
                    "key": "test-key-123",
                    "status": "disabled"
                },
                "instance": null,
                "meta": null
            }
        """.trimIndent()))

        val result = api.validateLicense(
            ValidateLicenseRequest(licenseKey = "test-key-123", instanceId = "inst-abc"),
        )
        assertThat(result.valid).isFalse()
    }
}
