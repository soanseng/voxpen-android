package com.voxink.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
class PreferencesManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        val languageFlow: Flow<SttLanguage> =
            context.dataStore.data.map { prefs ->
                languageFromKey(prefs[LANGUAGE_KEY] ?: "auto")
            }

        val recordingModeFlow: Flow<RecordingMode> =
            context.dataStore.data.map { prefs ->
                val modeStr = prefs[RECORDING_MODE_KEY] ?: DEFAULT_RECORDING_MODE.name
                RecordingMode.valueOf(modeStr)
            }

        val refinementEnabledFlow: Flow<Boolean> =
            context.dataStore.data.map { prefs ->
                prefs[REFINEMENT_ENABLED_KEY] ?: DEFAULT_REFINEMENT_ENABLED
            }

        val sttModelFlow: Flow<String> =
            context.dataStore.data.map { prefs ->
                prefs[STT_MODEL_KEY] ?: DEFAULT_STT_MODEL
            }

        val llmModelFlow: Flow<String> =
            context.dataStore.data.map { prefs ->
                prefs[LLM_MODEL_KEY] ?: DEFAULT_LLM_MODEL
            }

        val onboardingCompletedFlow: Flow<Boolean> =
            context.dataStore.data.map { prefs ->
                prefs[ONBOARDING_COMPLETED_KEY] ?: false
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

        suspend fun setRefinementEnabled(enabled: Boolean) {
            context.dataStore.edit { prefs ->
                prefs[REFINEMENT_ENABLED_KEY] = enabled
            }
        }

        suspend fun setSttModel(model: String) {
            context.dataStore.edit { prefs ->
                prefs[STT_MODEL_KEY] = model
            }
        }

        suspend fun setLlmModel(model: String) {
            context.dataStore.edit { prefs ->
                prefs[LLM_MODEL_KEY] = model
            }
        }

        suspend fun setOnboardingCompleted(completed: Boolean) {
            context.dataStore.edit { prefs ->
                prefs[ONBOARDING_COMPLETED_KEY] = completed
            }
        }

        companion object {
            val DEFAULT_LANGUAGE: SttLanguage = SttLanguage.Auto
            val DEFAULT_RECORDING_MODE: RecordingMode = RecordingMode.TAP_TO_TOGGLE
            const val DEFAULT_REFINEMENT_ENABLED: Boolean = true
            const val DEFAULT_STT_MODEL: String = "whisper-large-v3-turbo"
            const val DEFAULT_LLM_MODEL: String = "llama-3.3-70b-versatile"

            private val LANGUAGE_KEY = stringPreferencesKey("stt_language")
            private val RECORDING_MODE_KEY = stringPreferencesKey("recording_mode")
            private val REFINEMENT_ENABLED_KEY = booleanPreferencesKey("refinement_enabled")
            private val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")
            private val STT_MODEL_KEY = stringPreferencesKey("stt_model")
            private val LLM_MODEL_KEY = stringPreferencesKey("llm_model")

            fun languageFromKey(key: String): SttLanguage =
                when (key) {
                    "zh" -> SttLanguage.Chinese
                    "en" -> SttLanguage.English
                    "ja" -> SttLanguage.Japanese
                    else -> SttLanguage.Auto
                }

            fun languageToKey(language: SttLanguage): String =
                when (language) {
                    SttLanguage.Auto -> "auto"
                    SttLanguage.Chinese -> "zh"
                    SttLanguage.English -> "en"
                    SttLanguage.Japanese -> "ja"
                }
        }
    }
