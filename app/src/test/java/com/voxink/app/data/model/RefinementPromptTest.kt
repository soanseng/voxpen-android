package com.voxink.app.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class RefinementPromptTest {
    @Test
    fun `should return Chinese prompt for Chinese language`() {
        val prompt = RefinementPrompt.forLanguage(SttLanguage.Chinese)
        assertThat(prompt).contains("繁體中文")
        assertThat(prompt).contains("移除贅字")
    }

    @Test
    fun `should return English prompt for English language`() {
        val prompt = RefinementPrompt.forLanguage(SttLanguage.English)
        assertThat(prompt).contains("voice-to-text editor")
        assertThat(prompt).contains("filler words")
    }

    @Test
    fun `should return Japanese prompt for Japanese language`() {
        val prompt = RefinementPrompt.forLanguage(SttLanguage.Japanese)
        assertThat(prompt).contains("フィラー")
        assertThat(prompt).contains("音声テキスト変換")
    }

    @Test
    fun `should return mixed-language prompt for Auto language`() {
        val prompt = RefinementPrompt.forLanguage(SttLanguage.Auto)
        assertThat(prompt).contains("多種語言混合")
        assertThat(prompt).contains("不要把外語強制翻譯")
    }

    @Test
    fun `all prompts should be non-empty`() {
        listOf(SttLanguage.Auto, SttLanguage.Chinese, SttLanguage.English, SttLanguage.Japanese)
            .forEach { lang ->
                assertThat(RefinementPrompt.forLanguage(lang)).isNotEmpty()
            }
    }
}
