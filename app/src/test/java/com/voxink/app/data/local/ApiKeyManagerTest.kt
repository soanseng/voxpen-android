package com.voxink.app.data.local

import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import com.voxink.app.data.model.LlmProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ApiKeyManagerTest {
    private val sharedPreferences: SharedPreferences = mockk(relaxed = true)
    private val editor: SharedPreferences.Editor = mockk(relaxed = true)
    private lateinit var manager: ApiKeyManager

    @BeforeEach
    fun setUp() {
        every { sharedPreferences.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        manager = ApiKeyManager(sharedPreferences)
    }

    // --- Legacy Groq-specific tests ---

    @Test
    fun `should return null when no Groq key is stored`() {
        every { sharedPreferences.getString("groq_api_key", null) } returns null
        assertThat(manager.getGroqApiKey()).isNull()
    }

    @Test
    fun `should return stored Groq API key`() {
        every { sharedPreferences.getString("groq_api_key", null) } returns "gsk_test123"
        assertThat(manager.getGroqApiKey()).isEqualTo("gsk_test123")
    }

    @Test
    fun `should save Groq API key`() {
        manager.setGroqApiKey("gsk_new_key")
        verify { editor.putString("groq_api_key", "gsk_new_key") }
        verify { editor.apply() }
    }

    @Test
    fun `should clear Groq API key when set to null`() {
        every { editor.remove(any()) } returns editor
        manager.setGroqApiKey(null)
        verify { editor.remove("groq_api_key") }
        verify { editor.apply() }
    }

    @Test
    fun `should report key configured when key exists`() {
        every { sharedPreferences.getString("groq_api_key", null) } returns "gsk_key"
        assertThat(manager.isGroqKeyConfigured()).isTrue()
    }

    @Test
    fun `should report key not configured when key is null`() {
        every { sharedPreferences.getString("groq_api_key", null) } returns null
        assertThat(manager.isGroqKeyConfigured()).isFalse()
    }

    @Test
    fun `should report key not configured when key is blank`() {
        every { sharedPreferences.getString("groq_api_key", null) } returns "  "
        assertThat(manager.isGroqKeyConfigured()).isFalse()
    }

    // --- Per-provider API key tests ---

    @Test
    fun `getApiKey for Groq delegates to getGroqApiKey`() {
        every { sharedPreferences.getString("groq_api_key", null) } returns "gsk_test"
        assertThat(manager.getApiKey(LlmProvider.Groq)).isEqualTo("gsk_test")
    }

    @Test
    fun `getApiKey for OpenAI uses provider-specific key`() {
        every { sharedPreferences.getString("api_key_openai", null) } returns "sk_test"
        assertThat(manager.getApiKey(LlmProvider.OpenAI)).isEqualTo("sk_test")
    }

    @Test
    fun `getApiKey for OpenRouter uses provider-specific key`() {
        every { sharedPreferences.getString("api_key_openrouter", null) } returns "or_test"
        assertThat(manager.getApiKey(LlmProvider.OpenRouter)).isEqualTo("or_test")
    }

    @Test
    fun `getApiKey for Custom uses provider-specific key`() {
        every { sharedPreferences.getString("api_key_custom", null) } returns "custom_test"
        assertThat(manager.getApiKey(LlmProvider.Custom)).isEqualTo("custom_test")
    }

    @Test
    fun `setApiKey for Groq delegates to setGroqApiKey`() {
        manager.setApiKey(LlmProvider.Groq, "gsk_new")
        verify { editor.putString("groq_api_key", "gsk_new") }
    }

    @Test
    fun `setApiKey for OpenAI uses provider-specific key`() {
        manager.setApiKey(LlmProvider.OpenAI, "sk_new")
        verify { editor.putString("api_key_openai", "sk_new") }
    }

    @Test
    fun `setApiKey for OpenRouter uses provider-specific key`() {
        manager.setApiKey(LlmProvider.OpenRouter, "or_key")
        verify { editor.putString("api_key_openrouter", "or_key") }
    }

    @Test
    fun `setApiKey with null removes provider key`() {
        every { editor.remove(any()) } returns editor
        manager.setApiKey(LlmProvider.OpenAI, null)
        verify { editor.remove("api_key_openai") }
        verify { editor.apply() }
    }

    @Test
    fun `isKeyConfigured returns true when key exists`() {
        every { sharedPreferences.getString("api_key_openai", null) } returns "sk_test"
        assertThat(manager.isKeyConfigured(LlmProvider.OpenAI)).isTrue()
    }

    @Test
    fun `isKeyConfigured returns false when key is null`() {
        every { sharedPreferences.getString("api_key_openai", null) } returns null
        assertThat(manager.isKeyConfigured(LlmProvider.OpenAI)).isFalse()
    }

    @Test
    fun `isKeyConfigured returns false when key is blank`() {
        every { sharedPreferences.getString("api_key_openai", null) } returns "   "
        assertThat(manager.isKeyConfigured(LlmProvider.OpenAI)).isFalse()
    }

    @Test
    fun `isKeyConfigured for Groq delegates to legacy key`() {
        every { sharedPreferences.getString("groq_api_key", null) } returns "gsk_key"
        assertThat(manager.isKeyConfigured(LlmProvider.Groq)).isTrue()
    }

    // --- Custom base URL tests ---

    @Test
    fun `getCustomBaseUrl returns stored URL`() {
        every { sharedPreferences.getString("custom_llm_base_url", null) } returns "https://my-server.com/"
        assertThat(manager.getCustomBaseUrl()).isEqualTo("https://my-server.com/")
    }

    @Test
    fun `getCustomBaseUrl returns null when not set`() {
        every { sharedPreferences.getString("custom_llm_base_url", null) } returns null
        assertThat(manager.getCustomBaseUrl()).isNull()
    }

    @Test
    fun `setCustomBaseUrl saves URL`() {
        manager.setCustomBaseUrl("https://my-server.com/")
        verify { editor.putString("custom_llm_base_url", "https://my-server.com/") }
        verify { editor.apply() }
    }

    @Test
    fun `setCustomBaseUrl with null removes URL`() {
        every { editor.remove(any()) } returns editor
        manager.setCustomBaseUrl(null)
        verify { editor.remove("custom_llm_base_url") }
        verify { editor.apply() }
    }
}
