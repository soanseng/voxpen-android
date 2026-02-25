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
    fun `should return Korean prompt for Korean language`() {
        val prompt = RefinementPrompt.forLanguage(SttLanguage.Korean)
        assertThat(prompt).contains("군더더기")
        assertThat(prompt).contains("음성 텍스트 변환")
    }

    @Test
    fun `should return French prompt for French language`() {
        val prompt = RefinementPrompt.forLanguage(SttLanguage.French)
        assertThat(prompt).contains("mots de remplissage")
    }

    @Test
    fun `should return German prompt for German language`() {
        val prompt = RefinementPrompt.forLanguage(SttLanguage.German)
        assertThat(prompt).contains("Füllwörter")
    }

    @Test
    fun `should return Spanish prompt for Spanish language`() {
        val prompt = RefinementPrompt.forLanguage(SttLanguage.Spanish)
        assertThat(prompt).contains("muletillas")
    }

    @Test
    fun `should return Vietnamese prompt for Vietnamese language`() {
        val prompt = RefinementPrompt.forLanguage(SttLanguage.Vietnamese)
        assertThat(prompt).contains("từ đệm")
    }

    @Test
    fun `should return Indonesian prompt for Indonesian language`() {
        val prompt = RefinementPrompt.forLanguage(SttLanguage.Indonesian)
        assertThat(prompt).contains("kata pengisi")
    }

    @Test
    fun `should return Thai prompt for Thai language`() {
        val prompt = RefinementPrompt.forLanguage(SttLanguage.Thai)
        assertThat(prompt).contains("คำเติม")
    }

    @Test
    fun `all prompts should be non-empty`() {
        listOf(
            SttLanguage.Auto,
            SttLanguage.Chinese,
            SttLanguage.English,
            SttLanguage.Japanese,
            SttLanguage.Korean,
            SttLanguage.French,
            SttLanguage.German,
            SttLanguage.Spanish,
            SttLanguage.Vietnamese,
            SttLanguage.Indonesian,
            SttLanguage.Thai,
        ).forEach { lang ->
            assertThat(RefinementPrompt.forLanguage(lang)).isNotEmpty()
        }
    }

    @Test
    fun `should append vocabulary suffix when provided`() {
        val prompt =
            RefinementPrompt.forLanguage(
                SttLanguage.Chinese,
                listOf("語墨", "Claude"),
            )
        assertThat(prompt).contains("自定義詞典")
        assertThat(prompt).contains("語墨")
        assertThat(prompt).contains("Claude")
    }

    @Test
    fun `should not append suffix when vocabulary is empty`() {
        val withVocab = RefinementPrompt.forLanguage(SttLanguage.Chinese, emptyList())
        val without = RefinementPrompt.forLanguage(SttLanguage.Chinese)
        assertThat(withVocab).isEqualTo(without)
    }

    @Test
    fun `should use custom prompt when provided`() {
        val custom = "My custom prompt for testing"
        val prompt = RefinementPrompt.forLanguage(SttLanguage.Chinese, customPrompt = custom)
        assertThat(prompt).isEqualTo(custom)
        assertThat(prompt).doesNotContain("繁體中文")
    }

    @Test
    fun `should use default prompt when custom is blank`() {
        val prompt = RefinementPrompt.forLanguage(SttLanguage.Chinese, customPrompt = "  ")
        assertThat(prompt).contains("繁體中文")
    }

    @Test
    fun `should use default prompt when custom is null`() {
        val prompt = RefinementPrompt.forLanguage(SttLanguage.Chinese, customPrompt = null)
        assertThat(prompt).contains("繁體中文")
    }

    @Test
    fun `should append vocabulary suffix to custom prompt`() {
        val custom = "My custom prompt"
        val prompt =
            RefinementPrompt.forLanguage(
                SttLanguage.Chinese,
                listOf("語墨"),
                custom,
            )
        assertThat(prompt).startsWith(custom)
        assertThat(prompt).contains("自定義詞典")
        assertThat(prompt).contains("語墨")
    }
}
