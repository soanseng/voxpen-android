package com.voxink.app.data.local

import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
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
}
