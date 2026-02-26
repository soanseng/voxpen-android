package com.voxink.app.billing

import android.content.SharedPreferences
import com.voxink.app.data.remote.ActivateLicenseRequest
import com.voxink.app.data.remote.DeactivateLicenseRequest
import com.voxink.app.data.remote.LemonSqueezyApi
import com.voxink.app.data.remote.ValidateLicenseRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class LicenseManager
    @Inject
    constructor(
        private val api: LemonSqueezyApi,
        private val encryptedPrefs: SharedPreferences,
        @Named("licenseInstanceName") private val instanceName: String,
        @Named("ioDispatcher") private val ioDispatcher: CoroutineDispatcher,
    ) {
        private val _proStatus = MutableStateFlow(loadCachedStatus())
        val proStatus: StateFlow<ProStatus> = _proStatus.asStateFlow()

        private fun loadCachedStatus(): ProStatus {
            val key = encryptedPrefs.getString(KEY_LICENSE_KEY, null)
            val valid = encryptedPrefs.getBoolean(KEY_LICENSE_VALID, false)
            return if (key != null && valid) {
                ProStatus.Pro(ProSource.LICENSE_KEY)
            } else {
                ProStatus.Free
            }
        }

        suspend fun activateLicense(licenseKey: String): Result<Unit> =
            withContext(ioDispatcher) {
                try {
                    val response = api.activateLicense(
                        ActivateLicenseRequest(
                            licenseKey = licenseKey,
                            instanceName = instanceName,
                        ),
                    )
                    if (response.valid) {
                        cacheLicense(
                            key = licenseKey,
                            instanceId = response.instance?.id,
                            productName = response.meta?.productName,
                        )
                        _proStatus.value = ProStatus.Pro(ProSource.LICENSE_KEY)
                        Result.success(Unit)
                    } else {
                        Result.failure(LicenseActivationException("Invalid license key"))
                    }
                } catch (e: Exception) {
                    Timber.w(e, "License activation failed")
                    Result.failure(e)
                }
            }

        suspend fun validateCachedLicense() {
            val key = encryptedPrefs.getString(KEY_LICENSE_KEY, null) ?: return
            val instanceId = encryptedPrefs.getString(KEY_INSTANCE_ID, null) ?: return

            withContext(ioDispatcher) {
                try {
                    val response = api.validateLicense(
                        ValidateLicenseRequest(licenseKey = key, instanceId = instanceId),
                    )
                    if (response.valid) {
                        updateValidationTimestamp()
                        Timber.d("License re-validated successfully")
                    } else {
                        Timber.w("License no longer valid, revoking Pro status")
                        clearCachedLicense()
                        _proStatus.value = ProStatus.Free
                    }
                } catch (e: Exception) {
                    Timber.d(e, "License validation failed (network), keeping Pro status")
                }
            }
        }

        suspend fun deactivateLicense() {
            val key = encryptedPrefs.getString(KEY_LICENSE_KEY, null) ?: return
            val instanceId = encryptedPrefs.getString(KEY_INSTANCE_ID, null) ?: return

            withContext(ioDispatcher) {
                try {
                    api.deactivateLicense(
                        DeactivateLicenseRequest(licenseKey = key, instanceId = instanceId),
                    )
                } catch (e: Exception) {
                    Timber.w(e, "License deactivation API call failed")
                }
            }
            clearCachedLicense()
            _proStatus.value = ProStatus.Free
        }

        private fun cacheLicense(key: String, instanceId: String?, productName: String?) {
            encryptedPrefs.edit()
                .putString(KEY_LICENSE_KEY, key)
                .putString(KEY_INSTANCE_ID, instanceId)
                .putBoolean(KEY_LICENSE_VALID, true)
                .putLong(KEY_VALIDATED_AT, System.currentTimeMillis())
                .putString(KEY_PRODUCT_NAME, productName)
                .apply()
        }

        private fun updateValidationTimestamp() {
            encryptedPrefs.edit()
                .putLong(KEY_VALIDATED_AT, System.currentTimeMillis())
                .apply()
        }

        private fun clearCachedLicense() {
            encryptedPrefs.edit()
                .remove(KEY_LICENSE_KEY)
                .remove(KEY_INSTANCE_ID)
                .remove(KEY_LICENSE_VALID)
                .remove(KEY_VALIDATED_AT)
                .remove(KEY_PRODUCT_NAME)
                .apply()
        }

        companion object {
            const val KEY_LICENSE_KEY = "license_key"
            const val KEY_INSTANCE_ID = "license_instance_id"
            const val KEY_LICENSE_VALID = "license_valid"
            const val KEY_VALIDATED_AT = "license_validated_at"
            const val KEY_PRODUCT_NAME = "license_product_name"
        }
    }

class LicenseActivationException(message: String) : Exception(message)
