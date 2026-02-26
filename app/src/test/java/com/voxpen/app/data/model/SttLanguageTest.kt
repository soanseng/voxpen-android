package com.voxpen.app.data.model

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
    fun `Auto should have globe emoji`() {
        assertThat(SttLanguage.Auto.emoji).isEqualTo("\uD83C\uDF10")
    }

    @Test
    fun `Chinese should have Taiwan flag emoji`() {
        assertThat(SttLanguage.Chinese.emoji).isEqualTo("\uD83C\uDDF9\uD83C\uDDFC")
    }

    @Test
    fun `English should have US flag emoji`() {
        assertThat(SttLanguage.English.emoji).isEqualTo("\uD83C\uDDFA\uD83C\uDDF8")
    }

    @Test
    fun `Japanese should have Japan flag emoji`() {
        assertThat(SttLanguage.Japanese.emoji).isEqualTo("\uD83C\uDDEF\uD83C\uDDF5")
    }

    @Test
    fun `Korean should have Korea flag emoji`() {
        assertThat(SttLanguage.Korean.emoji).isEqualTo("\uD83C\uDDF0\uD83C\uDDF7")
    }

    @Test
    fun `French should have France flag emoji`() {
        assertThat(SttLanguage.French.emoji).isEqualTo("\uD83C\uDDEB\uD83C\uDDF7")
    }

    @Test
    fun `German should have Germany flag emoji`() {
        assertThat(SttLanguage.German.emoji).isEqualTo("\uD83C\uDDE9\uD83C\uDDEA")
    }

    @Test
    fun `Spanish should have Spain flag emoji`() {
        assertThat(SttLanguage.Spanish.emoji).isEqualTo("\uD83C\uDDEA\uD83C\uDDF8")
    }

    @Test
    fun `Vietnamese should have Vietnam flag emoji`() {
        assertThat(SttLanguage.Vietnamese.emoji).isEqualTo("\uD83C\uDDFB\uD83C\uDDF3")
    }

    @Test
    fun `Indonesian should have Indonesia flag emoji`() {
        assertThat(SttLanguage.Indonesian.emoji).isEqualTo("\uD83C\uDDEE\uD83C\uDDE9")
    }

    @Test
    fun `Thai should have Thailand flag emoji`() {
        assertThat(SttLanguage.Thai.emoji).isEqualTo("\uD83C\uDDF9\uD83C\uDDED")
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
