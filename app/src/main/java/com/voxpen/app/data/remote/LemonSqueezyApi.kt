package com.voxpen.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

interface LemonSqueezyApi {
    @POST("v1/licenses/activate")
    suspend fun activateLicense(
        @Body request: ActivateLicenseRequest,
    ): LicenseResponse

    @POST("v1/licenses/validate")
    suspend fun validateLicense(
        @Body request: ValidateLicenseRequest,
    ): LicenseResponse

    @POST("v1/licenses/deactivate")
    suspend fun deactivateLicense(
        @Body request: DeactivateLicenseRequest,
    ): LicenseResponse
}

@Serializable
data class ActivateLicenseRequest(
    @SerialName("license_key") val licenseKey: String,
    @SerialName("instance_name") val instanceName: String,
)

@Serializable
data class ValidateLicenseRequest(
    @SerialName("license_key") val licenseKey: String,
    @SerialName("instance_id") val instanceId: String,
)

@Serializable
data class DeactivateLicenseRequest(
    @SerialName("license_key") val licenseKey: String,
    @SerialName("instance_id") val instanceId: String,
)

@Serializable
data class LicenseResponse(
    val valid: Boolean,
    @SerialName("license_key") val licenseKey: LicenseKeyInfo? = null,
    val instance: InstanceInfo? = null,
    val meta: LicenseMeta? = null,
)

@Serializable
data class LicenseKeyInfo(
    val id: Long,
    val key: String,
    val status: String,
)

@Serializable
data class InstanceInfo(
    val id: String,
)

@Serializable
data class LicenseMeta(
    @SerialName("product_name") val productName: String? = null,
)
