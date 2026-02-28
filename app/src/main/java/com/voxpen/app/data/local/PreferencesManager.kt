package com.voxpen.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.voxpen.app.data.model.LlmProvider
import com.voxpen.app.data.model.RecordingMode
import com.voxpen.app.data.model.SttLanguage
import com.voxpen.app.data.model.ToneStyle
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

        val llmProviderFlow: Flow<LlmProvider> =
            context.dataStore.data.map { prefs ->
                LlmProvider.fromKey(prefs[LLM_PROVIDER_KEY] ?: LlmProvider.DEFAULT.key)
            }

        val customLlmModelFlow: Flow<String> =
            context.dataStore.data.map { prefs ->
                prefs[CUSTOM_LLM_MODEL_KEY] ?: ""
            }

        val customSttBaseUrlFlow: Flow<String> =
            context.dataStore.data.map { prefs ->
                prefs[CUSTOM_STT_BASE_URL_KEY] ?: ""
            }

        val toneStyleFlow: Flow<ToneStyle> =
            context.dataStore.data.map { prefs ->
                ToneStyle.fromKey(prefs[TONE_STYLE_KEY] ?: ToneStyle.DEFAULT.key)
            }

        val onboardingCompletedFlow: Flow<Boolean> =
            context.dataStore.data.map { prefs ->
                prefs[ONBOARDING_COMPLETED_KEY] ?: false
            }

        val keyboardTooltipsShownFlow: Flow<Boolean> =
            context.dataStore.data.map { prefs ->
                prefs[KEYBOARD_TOOLTIPS_SHOWN_KEY] ?: false
            }

        val translationEnabledFlow: Flow<Boolean> =
            context.dataStore.data.map { prefs ->
                prefs[TRANSLATION_ENABLED_KEY] ?: DEFAULT_TRANSLATION_ENABLED
            }

        val translationTargetLanguageFlow: Flow<SttLanguage> =
            context.dataStore.data.map { prefs ->
                languageFromKey(prefs[TRANSLATION_TARGET_LANGUAGE_KEY] ?: "en")
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

        suspend fun setLlmProvider(provider: LlmProvider) {
            context.dataStore.edit { prefs ->
                prefs[LLM_PROVIDER_KEY] = provider.key
            }
        }

        suspend fun setCustomLlmModel(model: String) {
            context.dataStore.edit { prefs ->
                prefs[CUSTOM_LLM_MODEL_KEY] = model
            }
        }

        suspend fun setCustomSttBaseUrl(url: String) {
            context.dataStore.edit { prefs ->
                prefs[CUSTOM_STT_BASE_URL_KEY] = url
            }
        }

        suspend fun setToneStyle(tone: ToneStyle) {
            context.dataStore.edit { prefs ->
                prefs[TONE_STYLE_KEY] = tone.key
            }
        }

        suspend fun setOnboardingCompleted(completed: Boolean) {
            context.dataStore.edit { prefs ->
                prefs[ONBOARDING_COMPLETED_KEY] = completed
            }
        }

        suspend fun setKeyboardTooltipsShown(shown: Boolean) {
            context.dataStore.edit { prefs ->
                prefs[KEYBOARD_TOOLTIPS_SHOWN_KEY] = shown
            }
        }

        suspend fun setTranslationEnabled(enabled: Boolean) {
            context.dataStore.edit { prefs ->
                prefs[TRANSLATION_ENABLED_KEY] = enabled
            }
        }

        suspend fun setTranslationTargetLanguage(language: SttLanguage) {
            context.dataStore.edit { prefs ->
                prefs[TRANSLATION_TARGET_LANGUAGE_KEY] = languageToKey(language)
            }
        }

        fun customPromptFlow(languageKey: String): Flow<String?> =
            context.dataStore.data.map { prefs ->
                prefs[stringPreferencesKey("custom_prompt_$languageKey")]
            }

        suspend fun setCustomPrompt(
            languageKey: String,
            prompt: String?,
        ) {
            context.dataStore.edit { prefs ->
                val key = stringPreferencesKey("custom_prompt_$languageKey")
                if (prompt.isNullOrBlank()) {
                    prefs.remove(key)
                } else {
                    prefs[key] = prompt
                }
            }
        }

        companion object {
            val DEFAULT_LANGUAGE: SttLanguage = SttLanguage.Auto
            val DEFAULT_RECORDING_MODE: RecordingMode = RecordingMode.TAP_TO_TOGGLE
            const val DEFAULT_REFINEMENT_ENABLED: Boolean = true
            const val DEFAULT_STT_MODEL: String = "whisper-large-v3-turbo"
            const val DEFAULT_LLM_MODEL: String = "llama-3.3-70b-versatile"
            const val DEFAULT_TRANSLATION_ENABLED: Boolean = false
            val DEFAULT_TRANSLATION_TARGET_LANGUAGE: SttLanguage = SttLanguage.English

            private val LANGUAGE_KEY = stringPreferencesKey("stt_language")
            private val RECORDING_MODE_KEY = stringPreferencesKey("recording_mode")
            private val REFINEMENT_ENABLED_KEY = booleanPreferencesKey("refinement_enabled")
            private val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")
            private val KEYBOARD_TOOLTIPS_SHOWN_KEY = booleanPreferencesKey("keyboard_tooltips_shown")
            private val STT_MODEL_KEY = stringPreferencesKey("stt_model")
            private val LLM_MODEL_KEY = stringPreferencesKey("llm_model")
            private val TONE_STYLE_KEY = stringPreferencesKey("tone_style")
            private val LLM_PROVIDER_KEY = stringPreferencesKey("llm_provider")
            private val CUSTOM_LLM_MODEL_KEY = stringPreferencesKey("custom_llm_model")
            private val CUSTOM_STT_BASE_URL_KEY = stringPreferencesKey("custom_stt_base_url_pref")
            private val TRANSLATION_ENABLED_KEY = booleanPreferencesKey("translation_enabled")
            private val TRANSLATION_TARGET_LANGUAGE_KEY = stringPreferencesKey("translation_target_language")

            fun languageFromKey(key: String): SttLanguage =
                when (key) {
                    "zh" -> SttLanguage.Chinese
                    "en" -> SttLanguage.English
                    "ja" -> SttLanguage.Japanese
                    "ko" -> SttLanguage.Korean
                    "fr" -> SttLanguage.French
                    "de" -> SttLanguage.German
                    "es" -> SttLanguage.Spanish
                    "vi" -> SttLanguage.Vietnamese
                    "id" -> SttLanguage.Indonesian
                    "th" -> SttLanguage.Thai
                    else -> SttLanguage.Auto
                }

            fun languageToKey(language: SttLanguage): String =
                when (language) {
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
        }
    }
