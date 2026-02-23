package com.voxink.app.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SttLanguageTest {

    @Test
    fun `should define auto-detect with no language code`() {
        assertThat(SttLanguage.Auto.code).isNull()
        assertThat(SttLanguage.Auto.prompt).isEqualTo("繁體中文，可能夾雜英文。")
    }

    @Test
    fun `should define Chinese with zh code and Traditional Chinese prompt`() {
        assertThat(SttLanguage.Chinese.code).isEqualTo("zh")
        assertThat(SttLanguage.Chinese.prompt).isEqualTo("繁體中文轉錄。")
    }

    @Test
    fun `should define English with en code`() {
        assertThat(SttLanguage.English.code).isEqualTo("en")
        assertThat(SttLanguage.English.prompt).isNotEmpty()
    }

    @Test
    fun `should define Japanese with ja code`() {
        assertThat(SttLanguage.Japanese.code).isEqualTo("ja")
        assertThat(SttLanguage.Japanese.prompt).isNotEmpty()
    }

    @Test
    fun `should be exhaustive in when expression`() {
        val languages = listOf(
            SttLanguage.Auto,
            SttLanguage.Chinese,
            SttLanguage.English,
            SttLanguage.Japanese,
        )
        languages.forEach { lang ->
            val label = when (lang) {
                SttLanguage.Auto -> "auto"
                SttLanguage.Chinese -> "zh"
                SttLanguage.English -> "en"
                SttLanguage.Japanese -> "ja"
            }
            assertThat(label).isNotEmpty()
        }
    }
}
