package com.voxpen.app.util

import com.google.common.truth.Truth.assertThat
import com.voxpen.app.data.model.SttLanguage
import org.junit.jupiter.api.Test

class VocabularyPromptBuilderTest {
    @Test
    fun `should return base prompt when vocabulary is empty`() {
        val result = VocabularyPromptBuilder.buildWhisperPrompt(SttLanguage.Chinese, emptyList())
        assertThat(result).isEqualTo("繁體中文轉錄。")
    }

    @Test
    fun `should append vocabulary to base prompt`() {
        val result =
            VocabularyPromptBuilder.buildWhisperPrompt(
                SttLanguage.Chinese,
                listOf("語墨", "Anthropic"),
            )
        assertThat(result).isEqualTo("繁體中文轉錄。 語墨, Anthropic")
    }

    @Test
    fun `should truncate vocabulary when exceeding token budget`() {
        val longWords = (1..120).map { "詞彙$it" }
        val result = VocabularyPromptBuilder.buildWhisperPrompt(SttLanguage.Chinese, longWords)
        assertThat(result.length).isLessThan(
            "繁體中文轉錄。".length + longWords.joinToString(", ").length,
        )
        assertThat(result).startsWith("繁體中文轉錄。")
    }

    @Test
    fun `should return empty string for LLM suffix when vocabulary is empty`() {
        val result = VocabularyPromptBuilder.buildLlmSuffix(SttLanguage.Chinese, emptyList())
        assertThat(result).isEmpty()
    }

    @Test
    fun `should build Chinese LLM suffix`() {
        val result =
            VocabularyPromptBuilder.buildLlmSuffix(
                SttLanguage.Chinese,
                listOf("語墨", "Anthropic"),
            )
        assertThat(result).contains("自定義詞典")
        assertThat(result).contains("語墨")
        assertThat(result).contains("Anthropic")
    }

    @Test
    fun `should build English LLM suffix`() {
        val result =
            VocabularyPromptBuilder.buildLlmSuffix(
                SttLanguage.English,
                listOf("VoxInk", "Claude"),
            )
        assertThat(result).contains("Custom dictionary")
        assertThat(result).contains("VoxInk")
        assertThat(result).contains("Claude")
    }

    @Test
    fun `should build Japanese LLM suffix`() {
        val result =
            VocabularyPromptBuilder.buildLlmSuffix(
                SttLanguage.Japanese,
                listOf("語墨"),
            )
        assertThat(result).contains("カスタム辞書")
        assertThat(result).contains("語墨")
    }

    @Test
    fun `should estimate CJK tokens as roughly 2 per char`() {
        val tokens = VocabularyPromptBuilder.estimateTokens("語墨")
        assertThat(tokens).isEqualTo(4)
    }

    @Test
    fun `should estimate Latin tokens as roughly 0_25 per char`() {
        val tokens = VocabularyPromptBuilder.estimateTokens("Anthropic")
        assertThat(tokens).isEqualTo(3)
    }
}
