package com.voxpen.app.data.remote

import com.google.common.truth.Truth.assertThat
import com.voxpen.app.data.model.LlmProvider
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test

class ChatCompletionApiFactoryTest {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val factory = ChatCompletionApiFactory(client, json)

    @Test
    fun `should create API for Groq provider`() {
        val api = factory.create(LlmProvider.Groq)
        assertThat(api).isNotNull()
    }

    @Test
    fun `should create API for OpenAI provider`() {
        val api = factory.create(LlmProvider.OpenAI)
        assertThat(api).isNotNull()
    }

    @Test
    fun `should create API for OpenRouter provider`() {
        val api = factory.create(LlmProvider.OpenRouter)
        assertThat(api).isNotNull()
    }

    @Test
    fun `should create API for Custom provider with base URL`() {
        val api = factory.createForCustom("https://my-server.com/")
        assertThat(api).isNotNull()
    }

    @Test
    fun `should cache API instances for same provider`() {
        val api1 = factory.create(LlmProvider.Groq)
        val api2 = factory.create(LlmProvider.Groq)
        assertThat(api1).isSameInstanceAs(api2)
    }

    @Test
    fun `should return different instances for different providers`() {
        val groq = factory.create(LlmProvider.Groq)
        val openai = factory.create(LlmProvider.OpenAI)
        assertThat(groq).isNotSameInstanceAs(openai)
    }
}
