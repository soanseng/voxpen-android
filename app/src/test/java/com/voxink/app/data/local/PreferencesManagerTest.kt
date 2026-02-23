package com.voxink.app.data.local

import com.google.common.truth.Truth.assertThat
import com.voxink.app.data.model.RecordingMode
import com.voxink.app.data.model.SttLanguage
import org.junit.jupiter.api.Test

class PreferencesManagerTest {
    @Test
    fun `should have correct default language`() {
        assertThat(PreferencesManager.DEFAULT_LANGUAGE).isEqualTo(SttLanguage.Auto)
    }

    @Test
    fun `should have correct default recording mode`() {
        assertThat(PreferencesManager.DEFAULT_RECORDING_MODE).isEqualTo(RecordingMode.TAP_TO_TOGGLE)
    }

    @Test
    fun `should map language key strings to SttLanguage`() {
        assertThat(PreferencesManager.languageFromKey("auto")).isEqualTo(SttLanguage.Auto)
        assertThat(PreferencesManager.languageFromKey("zh")).isEqualTo(SttLanguage.Chinese)
        assertThat(PreferencesManager.languageFromKey("en")).isEqualTo(SttLanguage.English)
        assertThat(PreferencesManager.languageFromKey("ja")).isEqualTo(SttLanguage.Japanese)
        assertThat(PreferencesManager.languageFromKey("unknown")).isEqualTo(SttLanguage.Auto)
    }

    @Test
    fun `should map SttLanguage to key strings`() {
        assertThat(PreferencesManager.languageToKey(SttLanguage.Auto)).isEqualTo("auto")
        assertThat(PreferencesManager.languageToKey(SttLanguage.Chinese)).isEqualTo("zh")
        assertThat(PreferencesManager.languageToKey(SttLanguage.English)).isEqualTo("en")
        assertThat(PreferencesManager.languageToKey(SttLanguage.Japanese)).isEqualTo("ja")
    }
}
