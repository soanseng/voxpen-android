package com.voxink.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.voxink.app.data.model.RecordingMode
import com.voxink.app.data.model.SttLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val languageFlow: Flow<SttLanguage> = context.dataStore.data.map { prefs ->
        languageFromKey(prefs[LANGUAGE_KEY] ?: "auto")
    }

    val recordingModeFlow: Flow<RecordingMode> = context.dataStore.data.map { prefs ->
        val modeStr = prefs[RECORDING_MODE_KEY] ?: DEFAULT_RECORDING_MODE.name
        RecordingMode.valueOf(modeStr)
    }

    suspend fun setLanguage(language: SttLanguage) {
        context.dataStore.edit { prefs ->
            prefs[LANGUAGE_KEY] = languageToKey(language)
        }
    }

    suspend fun setRecordingMode(mode: RecordingMode) {
        context.dataStore.edit { prefs ->
            prefs[RECORDING_MODE_KEY] = mode.name
        }
    }

    companion object {
        val DEFAULT_LANGUAGE: SttLanguage = SttLanguage.Auto
        val DEFAULT_RECORDING_MODE: RecordingMode = RecordingMode.TAP_TO_TOGGLE

        private val LANGUAGE_KEY = stringPreferencesKey("stt_language")
        private val RECORDING_MODE_KEY = stringPreferencesKey("recording_mode")

        fun languageFromKey(key: String): SttLanguage = when (key) {
            "zh" -> SttLanguage.Chinese
            "en" -> SttLanguage.English
            "ja" -> SttLanguage.Japanese
            else -> SttLanguage.Auto
        }

        fun languageToKey(language: SttLanguage): String = when (language) {
            SttLanguage.Auto -> "auto"
            SttLanguage.Chinese -> "zh"
            SttLanguage.English -> "en"
            SttLanguage.Japanese -> "ja"
        }
    }
}
