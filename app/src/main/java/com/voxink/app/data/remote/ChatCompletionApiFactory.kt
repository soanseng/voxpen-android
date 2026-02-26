package com.voxink.app.data.remote

import com.voxink.app.data.model.LlmProvider
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatCompletionApiFactory
    @Inject
    constructor(
        private val client: OkHttpClient,
        private val json: Json,
    ) {
        private val cache = ConcurrentHashMap<String, ChatCompletionApi>()

        fun create(provider: LlmProvider): ChatCompletionApi {
            require(provider.baseUrl.isNotBlank()) { "Use createForCustom() for Custom provider" }
            return cache.getOrPut(provider.key) {
                buildApi(provider.baseUrl)
            }
        }

        fun createForCustom(baseUrl: String): ChatCompletionApi {
            return cache.getOrPut("custom:$baseUrl") {
                buildApi(baseUrl)
            }
        }

        private fun buildApi(baseUrl: String): ChatCompletionApi {
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(ChatCompletionApi::class.java)
        }
    }
