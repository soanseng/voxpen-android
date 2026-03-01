package com.voxpen.app.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.voxpen.app.data.model.RecordingMode
import com.voxpen.app.data.model.SttLanguage
import com.voxpen.app.data.model.ToneStyle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
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

    @Test
    fun `should have correct default refinement enabled`() {
        assertThat(PreferencesManager.DEFAULT_REFINEMENT_ENABLED).isTrue()
    }

    @Test
    fun `default STT model should be whisper-large-v3-turbo`() {
        assertThat(PreferencesManager.DEFAULT_STT_MODEL).isEqualTo("whisper-large-v3-turbo")
    }

    @Test
    fun `default LLM model should be llama-3_3-70b-versatile`() {
        assertThat(PreferencesManager.DEFAULT_LLM_MODEL).isEqualTo("llama-3.3-70b-versatile")
    }

    @Test
    fun `default translation enabled should be false`() {
        assertFalse(PreferencesManager.DEFAULT_TRANSLATION_ENABLED)
    }

    @Test
    fun `default translation target language should be English`() {
        assertThat(PreferencesManager.DEFAULT_TRANSLATION_TARGET_LANGUAGE)
            .isEqualTo(SttLanguage.English)
    }

    // ---- DataStore-backed tests ----

    private fun createPreferencesManager(tempDir: File): PreferencesManager {
        val testDispatcher = UnconfinedTestDispatcher()
        val testScope = TestScope(testDispatcher)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { File(tempDir, "test_prefs.preferences_pb") },
        )
        return PreferencesManager(dataStore)
    }

    @Test
    fun `autoToneEnabled defaults to true`(@TempDir tempDir: File) = runTest {
        val prefs = createPreferencesManager(tempDir)
        prefs.autoToneEnabledFlow.test {
            assertThat(awaitItem()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setAutoToneEnabled persists false`(@TempDir tempDir: File) = runTest {
        val prefs = createPreferencesManager(tempDir)
        prefs.setAutoToneEnabled(false)
        prefs.autoToneEnabledFlow.test {
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `customAppToneRulesFlow defaults to empty map`(@TempDir tempDir: File) = runTest {
        val prefs = createPreferencesManager(tempDir)
        prefs.customAppToneRulesFlow.test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setCustomAppToneRule persists and reads back`(@TempDir tempDir: File) = runTest {
        val prefs = createPreferencesManager(tempDir)
        prefs.setCustomAppToneRule("com.myapp", ToneStyle.Professional)
        prefs.customAppToneRulesFlow.test {
            val rules = awaitItem()
            assertThat(rules["com.myapp"]).isEqualTo(ToneStyle.Professional)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removeCustomAppToneRule removes the entry`(@TempDir tempDir: File) = runTest {
        val prefs = createPreferencesManager(tempDir)
        prefs.setCustomAppToneRule("com.myapp", ToneStyle.Casual)
        prefs.removeCustomAppToneRule("com.myapp")
        prefs.customAppToneRulesFlow.test {
            assertThat(awaitItem()).doesNotContainKey("com.myapp")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
