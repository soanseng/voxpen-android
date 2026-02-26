package com.voxpen.app.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LlmProviderTest {
    @Test
    fun `all providers should have unique keys`() {
        val keys = LlmProvider.all.map { it.key }
        assertThat(keys).containsNoDuplicates()
    }

    @Test
    fun `fromKey should return correct provider`() {
        assertThat(LlmProvider.fromKey("groq")).isEqualTo(LlmProvider.Groq)
        assertThat(LlmProvider.fromKey("openai")).isEqualTo(LlmProvider.OpenAI)
        assertThat(LlmProvider.fromKey("openrouter")).isEqualTo(LlmProvider.OpenRouter)
        assertThat(LlmProvider.fromKey("custom")).isEqualTo(LlmProvider.Custom)
    }

    @Test
    fun `fromKey should default to Groq for unknown key`() {
        assertThat(LlmProvider.fromKey("unknown")).isEqualTo(LlmProvider.Groq)
    }

    @Test
    fun `default provider should be Groq`() {
        assertThat(LlmProvider.DEFAULT).isEqualTo(LlmProvider.Groq)
    }

    @Test
    fun `Groq should have correct base URL`() {
        assertThat(LlmProvider.Groq.baseUrl).isEqualTo("https://api.groq.com/openai/")
    }

    @Test
    fun `OpenAI should have correct base URL`() {
        assertThat(LlmProvider.OpenAI.baseUrl).isEqualTo("https://api.openai.com/")
    }

    @Test
    fun `OpenRouter should have correct base URL`() {
        assertThat(LlmProvider.OpenRouter.baseUrl).isEqualTo("https://openrouter.ai/api/")
    }

    @Test
    fun `Custom should have empty base URL`() {
        assertThat(LlmProvider.Custom.baseUrl).isEmpty()
    }

    @Test
    fun `Groq models should contain default model`() {
        assertThat(LlmProvider.Groq.models.map { it.id })
            .contains("openai/gpt-oss-120b")
    }

    @Test
    fun `each provider should have a recommended model`() {
        LlmProvider.all.filter { it != LlmProvider.Custom }.forEach { provider ->
            assertThat(provider.models.any { it.isDefault }).isTrue()
        }
    }

    @Test
    fun `model option tags should not be empty for tagged models`() {
        LlmProvider.all.flatMap { it.models }.filter { it.tag != null }.forEach { model ->
            assertThat(model.tag).isNotEmpty()
        }
    }

    @Test
    fun `default model for each provider should return correct id`() {
        assertThat(LlmProvider.Groq.defaultModelId).isEqualTo("openai/gpt-oss-120b")
        assertThat(LlmProvider.OpenAI.defaultModelId).isEqualTo("gpt-4o-mini")
        assertThat(LlmProvider.OpenRouter.defaultModelId).isEqualTo("google/gemini-2.0-flash-001")
    }
}
