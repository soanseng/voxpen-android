package com.voxpen.app.data.remote

import com.voxpen.app.data.model.SttProvider
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SttApiFactory
    @Inject
    constructor(
        private val client: OkHttpClient,
        private val json: Json,
    ) {
        private val cache = ConcurrentHashMap<String, SttApi>()

        fun createForProvider(provider: SttProvider): SttApi {
            val baseUrl = requireNotNull(provider.baseUrl) {
                "Custom STT requires a base URL"
            }
            return create(baseUrl, "provider_stt:${provider.key}")
        }

        fun createForCustom(baseUrl: String): SttApi = create(baseUrl, "custom_stt:$baseUrl")

        private fun create(
            baseUrl: String,
            cacheKey: String,
        ): SttApi =
            cache.getOrPut(cacheKey) {
                Retrofit.Builder()
                    .baseUrl(normalizeBaseUrl(baseUrl))
                    .client(client)
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()
                    .create(SttApi::class.java)
            }

        private fun normalizeBaseUrl(baseUrl: String): String =
            if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    }
