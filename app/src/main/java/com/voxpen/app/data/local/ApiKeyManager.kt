package com.voxpen.app.data.local

import android.content.SharedPreferences
import com.voxpen.app.data.model.LlmProvider
import com.voxpen.app.data.model.SttProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiKeyManager
    @Inject
    constructor(
        private val encryptedPrefs: SharedPreferences,
    ) {
        // --- Legacy Groq-specific (used by STT) ---
        fun getGroqApiKey(): String? = encryptedPrefs.getString(KEY_GROQ, null)

        fun setGroqApiKey(key: String?) {
            encryptedPrefs.edit().apply {
                if (key != null) putString(KEY_GROQ, key) else remove(KEY_GROQ)
                apply()
            }
        }

        fun isGroqKeyConfigured(): Boolean = !getGroqApiKey().isNullOrBlank()

        // --- Per-provider API keys ---
        fun getApiKey(provider: LlmProvider): String? {
            if (provider == LlmProvider.Groq) return getGroqApiKey()
            return encryptedPrefs.getString(keyFor(provider), null)
        }

        fun setApiKey(provider: LlmProvider, key: String?) {
            if (provider == LlmProvider.Groq) {
                setGroqApiKey(key)
                return
            }
            encryptedPrefs.edit().apply {
                val prefKey = keyFor(provider)
                if (key != null) putString(prefKey, key) else remove(prefKey)
                apply()
            }
        }

        fun isKeyConfigured(provider: LlmProvider): Boolean =
            !getApiKey(provider).isNullOrBlank()

        fun getSttApiKey(provider: SttProvider): String? =
            when (provider) {
                SttProvider.Groq -> getGroqApiKey()
                SttProvider.OpenAI -> encryptedPrefs.getString("${STT_KEY_PREFIX}${provider.key}", null)
                    ?: encryptedPrefs.getString(keyFor(LlmProvider.OpenAI), null)
                SttProvider.Custom -> encryptedPrefs.getString("${STT_KEY_PREFIX}${provider.key}", null)
                    ?: encryptedPrefs.getString(keyFor(LlmProvider.Custom), null)
            }

        fun setSttApiKey(
            provider: SttProvider,
            key: String?,
        ) {
            if (provider == SttProvider.Groq) {
                setGroqApiKey(key)
                return
            }
            encryptedPrefs.edit().apply {
                val prefKey = "${STT_KEY_PREFIX}${provider.key}"
                if (key != null) putString(prefKey, key) else remove(prefKey)
                apply()
            }
        }

        fun isSttKeyConfigured(provider: SttProvider): Boolean =
            !getSttApiKey(provider).isNullOrBlank()

        private fun keyFor(provider: LlmProvider): String =
            "${KEY_PREFIX}${provider.key}"

        // --- Custom provider base URL ---
        fun getCustomBaseUrl(): String? =
            encryptedPrefs.getString(KEY_CUSTOM_BASE_URL, null)

        fun setCustomBaseUrl(url: String?) {
            encryptedPrefs.edit().apply {
                if (url != null) putString(KEY_CUSTOM_BASE_URL, url) else remove(KEY_CUSTOM_BASE_URL)
                apply()
            }
        }

        // --- Custom STT base URL ---
        fun getCustomSttBaseUrl(): String? =
            encryptedPrefs.getString(KEY_CUSTOM_STT_BASE_URL, null)

        fun setCustomSttBaseUrl(url: String?) {
            encryptedPrefs.edit().apply {
                if (url != null) putString(KEY_CUSTOM_STT_BASE_URL, url) else remove(KEY_CUSTOM_STT_BASE_URL)
                apply()
            }
        }

        companion object {
            private const val KEY_GROQ = "groq_api_key"
            private const val KEY_PREFIX = "api_key_"
            private const val STT_KEY_PREFIX = "stt_api_key_"
            private const val KEY_CUSTOM_BASE_URL = "custom_llm_base_url"
            private const val KEY_CUSTOM_STT_BASE_URL = "custom_stt_base_url"
        }
    }
