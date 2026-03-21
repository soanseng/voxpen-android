package com.voxpen.app.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TranslationPromptTest {
    @Test
    fun `should contain translation instruction for zh to en`() {
        val prompt = TranslationPrompt.build(SttLanguage.Chinese, SttLanguage.English)
        assertThat(prompt).contains("translat")
        assertThat(prompt).contains("English")
    }

    @Test
    fun `should contain translation instruction for en to zh`() {
        val prompt = TranslationPrompt.build(SttLanguage.English, SttLanguage.Chinese)
        assertThat(prompt).contains("繁體中文")
    }

    @Test
    fun `should contain translation instruction for auto to en`() {
        val prompt = TranslationPrompt.build(SttLanguage.Auto, SttLanguage.English)
        assertThat(prompt).contains("English")
    }

    @Test
    fun `should not output explanations instruction`() {
        val prompt = TranslationPrompt.build(SttLanguage.Chinese, SttLanguage.English)
        assertThat(prompt).contains("no explanation")
    }

    @Test
    fun `should handle zh to ja translation`() {
        val prompt = TranslationPrompt.build(SttLanguage.Chinese, SttLanguage.Japanese)
        assertThat(prompt).contains("日本語")
    }

    @Test
    fun `should mention mixed language handling in toEnglish prompt`() {
        val prompt = TranslationPrompt.build(SttLanguage.Chinese, SttLanguage.English)
        assertThat(prompt).contains("multiple languages mixed")
        assertThat(prompt).contains("ALL content into English")
    }

    @Test
    fun `should mention mixed language handling in toChinese prompt`() {
        val prompt = TranslationPrompt.build(SttLanguage.English, SttLanguage.Chinese)
        assertThat(prompt).contains("多種語言混合")
        assertThat(prompt).contains("統一翻譯成繁體中文")
    }

    @Test
    fun `should mention mixed language handling in toJapanese prompt`() {
        val prompt = TranslationPrompt.build(SttLanguage.Chinese, SttLanguage.Japanese)
        assertThat(prompt).contains("複数の言語が混在")
    }

    @Test
    fun `toChinese prompt should instruct full-width punctuation`() {
        val prompt = TranslationPrompt.build(SttLanguage.English, SttLanguage.Chinese)
        assertThat(prompt).contains("全形標點")
    }
}
