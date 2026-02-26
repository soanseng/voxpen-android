package com.voxpen.app.di

import com.voxpen.app.BuildConfig
import com.voxpen.app.data.remote.GroqApi
import com.voxpen.app.data.remote.LemonSqueezyApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideJson(): Json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level =
                        if (BuildConfig.DEBUG) {
                            HttpLoggingInterceptor.Level.HEADERS
                        } else {
                            HttpLoggingInterceptor.Level.NONE
                        }
                },
            )
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideGroqApi(
        client: OkHttpClient,
        json: Json,
    ): GroqApi {
        return Retrofit.Builder()
            .baseUrl("https://api.groq.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GroqApi::class.java)
    }

    @Provides
    @Singleton
    fun provideLemonSqueezyApi(
        client: OkHttpClient,
        json: Json,
    ): LemonSqueezyApi {
        return Retrofit.Builder()
            .baseUrl("https://api.lemonsqueezy.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(LemonSqueezyApi::class.java)
    }
}
