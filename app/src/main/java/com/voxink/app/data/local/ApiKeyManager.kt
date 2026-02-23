package com.voxink.app.data.local

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiKeyManager
    @Inject
    constructor(
        private val encryptedPrefs: SharedPreferences,
    ) {
        fun getGroqApiKey(): String? = encryptedPrefs.getString(KEY_GROQ, null)

        fun setGroqApiKey(key: String?) {
            encryptedPrefs.edit().apply {
                if (key != null) putString(KEY_GROQ, key) else remove(KEY_GROQ)
                apply()
            }
        }

        fun isGroqKeyConfigured(): Boolean = !getGroqApiKey().isNullOrBlank()

        companion object {
            private const val KEY_GROQ = "groq_api_key"
        }
    }
