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
    fun `should define Korean with ko code`() {
        assertThat(SttLanguage.Korean.code).isEqualTo("ko")
        assertThat(SttLanguage.Korean.prompt).isNotEmpty()
    }

    @Test
    fun `should define French with fr code`() {
        assertThat(SttLanguage.French.code).isEqualTo("fr")
        assertThat(SttLanguage.French.prompt).isNotEmpty()
    }

    @Test
    fun `should define German with de code`() {
        assertThat(SttLanguage.German.code).isEqualTo("de")
        assertThat(SttLanguage.German.prompt).isNotEmpty()
    }

    @Test
    fun `should define Spanish with es code`() {
        assertThat(SttLanguage.Spanish.code).isEqualTo("es")
        assertThat(SttLanguage.Spanish.prompt).isNotEmpty()
    }

    @Test
    fun `should define Vietnamese with vi code`() {
        assertThat(SttLanguage.Vietnamese.code).isEqualTo("vi")
        assertThat(SttLanguage.Vietnamese.prompt).isNotEmpty()
    }

    @Test
    fun `should define Indonesian with id code`() {
        assertThat(SttLanguage.Indonesian.code).isEqualTo("id")
        assertThat(SttLanguage.Indonesian.prompt).isNotEmpty()
    }

    @Test
    fun `should define Thai with th code`() {
        assertThat(SttLanguage.Thai.code).isEqualTo("th")
        assertThat(SttLanguage.Thai.prompt).isNotEmpty()
    }

    @Test
    fun `should be exhaustive in when expression`() {
        val languages =
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
            )
        languages.forEach { lang ->
            val label =
                when (lang) {
                    SttLanguage.Auto -> "auto"
                    SttLanguage.Chinese -> "zh"
                    SttLanguage.English -> "en"
                    SttLanguage.Japanese -> "ja"
                    SttLanguage.Korean -> "ko"
                    SttLanguage.French -> "fr"
                    SttLanguage.German -> "de"
                    SttLanguage.Spanish -> "es"
                    SttLanguage.Vietnamese -> "vi"
                    SttLanguage.Indonesian -> "id"
                    SttLanguage.Thai -> "th"
                }
            assertThat(label).isNotEmpty()
        }
    }
}
