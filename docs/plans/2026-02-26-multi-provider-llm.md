# Multi-Provider LLM Model Selection Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Support 4 LLM providers (Groq, OpenAI, OpenRouter, Custom) with per-provider API keys, curated model lists with recommendation tags, and custom model name input.

**Architecture:** A new `LlmProvider` sealed class models the 4 providers. Each has a base URL, curated `LlmModelOption` list, and string key for persistence. A `ChatCompletionApi` Retrofit interface (extracted from the LLM half of `GroqApi`) is instantiated dynamically per provider. `ApiKeyManager` gains per-provider key storage. STT remains Groq-only (unchanged). The `LlmRepository` accepts a provider+apiKey pair resolved at call time by `RecordingController`.

**Tech Stack:** Kotlin, Retrofit + OkHttp, Hilt DI, DataStore Preferences, Jetpack Compose, JUnit 5 + MockK + Truth

---

## Task 1: Create LlmProvider and LlmModelOption models

**Files:**
- Create: `app/src/main/java/com/voxink/app/data/model/LlmProvider.kt`
- Create: `app/src/test/java/com/voxink/app/data/model/LlmProviderTest.kt`

**Step 1: Write the failing test**

Create `app/src/test/java/com/voxink/app/data/model/LlmProviderTest.kt`:

```kotlin
package com.voxink.app.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LlmProviderTest {
    @Test
    fun `all providers should have unique keys`() {
        val keys = LlmProvider.all.map { it.key }
        assertThat(keys).containsNoDuplicates()
    }

    @Test
    fun `fromKey should return correct provider`() {
        assertThat(LlmProvider.fromKey("groq")).isEqualTo(LlmProvider.Groq)
        assertThat(LlmProvider.fromKey("openai")).isEqualTo(LlmProvider.OpenAI)
        assertThat(LlmProvider.fromKey("openrouter")).isEqualTo(LlmProvider.OpenRouter)
        assertThat(LlmProvider.fromKey("custom")).isEqualTo(LlmProvider.Custom)
    }

    @Test
    fun `fromKey should default to Groq for unknown key`() {
        assertThat(LlmProvider.fromKey("unknown")).isEqualTo(LlmProvider.Groq)
    }

    @Test
    fun `default provider should be Groq`() {
        assertThat(LlmProvider.DEFAULT).isEqualTo(LlmProvider.Groq)
    }

    @Test
    fun `Groq should have correct base URL`() {
        assertThat(LlmProvider.Groq.baseUrl).isEqualTo("https://api.groq.com/")
    }

    @Test
    fun `OpenAI should have correct base URL`() {
        assertThat(LlmProvider.OpenAI.baseUrl).isEqualTo("https://api.openai.com/")
    }

    @Test
    fun `OpenRouter should have correct base URL`() {
        assertThat(LlmProvider.OpenRouter.baseUrl).isEqualTo("https://openrouter.ai/api/")
    }

    @Test
    fun `Custom should have empty base URL`() {
        assertThat(LlmProvider.Custom.baseUrl).isEmpty()
    }

    @Test
    fun `Groq models should contain default model`() {
        assertThat(LlmProvider.Groq.models.map { it.id })
            .contains("openai/gpt-oss-120b")
    }

    @Test
    fun `each provider should have a recommended model`() {
        LlmProvider.all.filter { it != LlmProvider.Custom }.forEach { provider ->
            assertThat(provider.models.any { it.isDefault }).isTrue()
        }
    }

    @Test
    fun `model option tags should not be empty for tagged models`() {
        LlmProvider.all.flatMap { it.models }.filter { it.tag != null }.forEach { model ->
            assertThat(model.tag).isNotEmpty()
        }
    }

    @Test
    fun `default model for each provider should return correct id`() {
        assertThat(LlmProvider.Groq.defaultModelId).isEqualTo("openai/gpt-oss-120b")
        assertThat(LlmProvider.OpenAI.defaultModelId).isEqualTo("gpt-4o-mini")
        assertThat(LlmProvider.OpenRouter.defaultModelId).isEqualTo("google/gemini-2.0-flash-001")
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.voxink.app.data.model.LlmProviderTest" --no-daemon`
Expected: FAIL — `LlmProvider` doesn't exist.

**Step 3: Create LlmProvider.kt**

Create `app/src/main/java/com/voxink/app/data/model/LlmProvider.kt`:

```kotlin
package com.voxink.app.data.model

data class LlmModelOption(
    val id: String,
    val label: String,
    val tag: String? = null,
    val isDefault: Boolean = false,
)

sealed class LlmProvider(
    val key: String,
    val baseUrl: String,
    val models: List<LlmModelOption>,
) {
    val defaultModelId: String
        get() = models.firstOrNull { it.isDefault }?.id ?: models.firstOrNull()?.id ?: ""

    data object Groq : LlmProvider(
        key = "groq",
        baseUrl = "https://api.groq.com/",
        models = listOf(
            LlmModelOption("openai/gpt-oss-120b", "GPT-OSS 120B", tag = "recommended", isDefault = true),
            LlmModelOption("openai/gpt-oss-20b", "GPT-OSS 20B", tag = "fast"),
            LlmModelOption("qwen/qwen3-32b", "Qwen3 32B", tag = "best_chinese"),
            LlmModelOption("llama-3.3-70b-versatile", "LLaMA 3.3 70B"),
        ),
    )

    data object OpenAI : LlmProvider(
        key = "openai",
        baseUrl = "https://api.openai.com/",
        models = listOf(
            LlmModelOption("gpt-4o-mini", "GPT-4o Mini", tag = "recommended", isDefault = true),
            LlmModelOption("gpt-4.1-nano", "GPT-4.1 Nano", tag = "cheapest"),
            LlmModelOption("gpt-4.1-mini", "GPT-4.1 Mini"),
        ),
    )

    data object OpenRouter : LlmProvider(
        key = "openrouter",
        baseUrl = "https://openrouter.ai/api/",
        models = listOf(
            LlmModelOption("google/gemini-2.0-flash-001", "Gemini 2.0 Flash", tag = "recommended", isDefault = true),
            LlmModelOption("anthropic/claude-3.5-haiku", "Claude 3.5 Haiku", tag = "quality"),
            LlmModelOption("deepseek/deepseek-chat", "DeepSeek Chat", tag = "cheapest"),
        ),
    )

    data object Custom : LlmProvider(
        key = "custom",
        baseUrl = "",
        models = emptyList(),
    )

    companion object {
        val DEFAULT: LlmProvider get() = Groq
        val all: List<LlmProvider> get() = listOf(Groq, OpenAI, OpenRouter, Custom)

        fun fromKey(key: String): LlmProvider =
            when (key) {
                "groq" -> Groq
                "openai" -> OpenAI
                "openrouter" -> OpenRouter
                "custom" -> Custom
                else -> DEFAULT
            }
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.voxink.app.data.model.LlmProviderTest" --no-daemon`
Expected: All tests PASS.

**Step 5: Commit**

```bash
git add app/src/main/java/com/voxink/app/data/model/LlmProvider.kt \
       app/src/test/java/com/voxink/app/data/model/LlmProviderTest.kt
git commit -m "feat: add LlmProvider and LlmModelOption models

4 providers: Groq (default), OpenAI, OpenRouter, Custom.
Curated model lists with recommendation tags per provider."
```

---

## Task 2: Extract ChatCompletionApi from GroqApi

Currently `GroqApi` has both STT (`transcribe`) and LLM (`chatCompletion`). We need a separate `ChatCompletionApi` interface for LLM that can be instantiated with any provider's base URL.

**Files:**
- Create: `app/src/main/java/com/voxink/app/data/remote/ChatCompletionApi.kt`
- Modify: `app/src/main/java/com/voxink/app/data/remote/GroqApi.kt` — keep as-is (STT still uses it; don't break existing code)

**Step 1: Create ChatCompletionApi**

Create `app/src/main/java/com/voxink/app/data/remote/ChatCompletionApi.kt`:

```kotlin
package com.voxink.app.data.remote

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface ChatCompletionApi {
    @POST("v1/chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest,
    ): ChatCompletionResponse
}
```

Note: The path is `v1/chat/completions` (not `openai/v1/chat/completions`). Groq's base URL `https://api.groq.com/` uses the path `openai/v1/chat/completions`, so we set Groq's base URL to `https://api.groq.com/openai/` in the factory. OpenAI uses `https://api.openai.com/v1/chat/completions`. OpenRouter uses `https://openrouter.ai/api/v1/chat/completions`. All share the same `v1/chat/completions` suffix.

**IMPORTANT**: Update `LlmProvider.Groq.baseUrl` to `"https://api.groq.com/openai/"` so the path `v1/chat/completions` resolves correctly. Also update the test expectation.

**Step 2: Update LlmProvider Groq baseUrl**

In `app/src/main/java/com/voxink/app/data/model/LlmProvider.kt`, change Groq's `baseUrl`:

```kotlin
data object Groq : LlmProvider(
    key = "groq",
    baseUrl = "https://api.groq.com/openai/",
    // ... models unchanged
)
```

Update test in `LlmProviderTest.kt`:
```kotlin
@Test
fun `Groq should have correct base URL`() {
    assertThat(LlmProvider.Groq.baseUrl).isEqualTo("https://api.groq.com/openai/")
}
```

**Step 3: Build to verify**

Run: `./gradlew assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL.

**Step 4: Commit**

```bash
git add app/src/main/java/com/voxink/app/data/remote/ChatCompletionApi.kt \
       app/src/main/java/com/voxink/app/data/model/LlmProvider.kt \
       app/src/test/java/com/voxink/app/data/model/LlmProviderTest.kt
git commit -m "feat: extract ChatCompletionApi interface for multi-provider LLM

Shared v1/chat/completions endpoint works across Groq, OpenAI,
and OpenRouter. Groq base URL adjusted to include /openai/ prefix."
```

---

## Task 3: Create ChatCompletionApiFactory

A factory that creates `ChatCompletionApi` Retrofit instances on demand based on `LlmProvider`. Caches instances to avoid rebuilding Retrofit for every call.

**Files:**
- Create: `app/src/main/java/com/voxink/app/data/remote/ChatCompletionApiFactory.kt`
- Create: `app/src/test/java/com/voxink/app/data/remote/ChatCompletionApiFactoryTest.kt`

**Step 1: Write the failing test**

Create `app/src/test/java/com/voxink/app/data/remote/ChatCompletionApiFactoryTest.kt`:

```kotlin
package com.voxink.app.data.remote

import com.google.common.truth.Truth.assertThat
import com.voxink.app.data.model.LlmProvider
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test

class ChatCompletionApiFactoryTest {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val factory = ChatCompletionApiFactory(client, json)

    @Test
    fun `should create API for Groq provider`() {
        val api = factory.create(LlmProvider.Groq)
        assertThat(api).isNotNull()
    }

    @Test
    fun `should create API for OpenAI provider`() {
        val api = factory.create(LlmProvider.OpenAI)
        assertThat(api).isNotNull()
    }

    @Test
    fun `should create API for OpenRouter provider`() {
        val api = factory.create(LlmProvider.OpenRouter)
        assertThat(api).isNotNull()
    }

    @Test
    fun `should create API for Custom provider with base URL`() {
        val api = factory.createForCustom("https://my-server.com/")
        assertThat(api).isNotNull()
    }

    @Test
    fun `should cache API instances for same provider`() {
        val api1 = factory.create(LlmProvider.Groq)
        val api2 = factory.create(LlmProvider.Groq)
        assertThat(api1).isSameInstanceAs(api2)
    }

    @Test
    fun `should return different instances for different providers`() {
        val groq = factory.create(LlmProvider.Groq)
        val openai = factory.create(LlmProvider.OpenAI)
        assertThat(groq).isNotSameInstanceAs(openai)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.voxink.app.data.remote.ChatCompletionApiFactoryTest" --no-daemon`
Expected: FAIL — `ChatCompletionApiFactory` doesn't exist.

**Step 3: Create ChatCompletionApiFactory**

Create `app/src/main/java/com/voxink/app/data/remote/ChatCompletionApiFactory.kt`:

```kotlin
package com.voxink.app.data.remote

import com.voxink.app.data.model.LlmProvider
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatCompletionApiFactory
    @Inject
    constructor(
        private val client: OkHttpClient,
        private val json: Json,
    ) {
        private val cache = ConcurrentHashMap<String, ChatCompletionApi>()

        fun create(provider: LlmProvider): ChatCompletionApi {
            require(provider.baseUrl.isNotBlank()) { "Use createForCustom() for Custom provider" }
            return cache.getOrPut(provider.key) {
                buildApi(provider.baseUrl)
            }
        }

        fun createForCustom(baseUrl: String): ChatCompletionApi {
            return cache.getOrPut("custom:$baseUrl") {
                buildApi(baseUrl)
            }
        }

        private fun buildApi(baseUrl: String): ChatCompletionApi {
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(ChatCompletionApi::class.java)
        }
    }
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.voxink.app.data.remote.ChatCompletionApiFactoryTest" --no-daemon`
Expected: All 6 tests PASS.

**Step 5: Commit**

```bash
git add app/src/main/java/com/voxink/app/data/remote/ChatCompletionApiFactory.kt \
       app/src/test/java/com/voxink/app/data/remote/ChatCompletionApiFactoryTest.kt
git commit -m "feat: add ChatCompletionApiFactory with provider caching

Creates and caches Retrofit ChatCompletionApi instances per provider.
Supports Groq, OpenAI, OpenRouter, and custom base URLs."
```

---

## Task 4: Add per-provider API key storage

**Files:**
- Modify: `app/src/main/java/com/voxink/app/data/local/ApiKeyManager.kt`
- Modify: `app/src/test/java/com/voxink/app/data/local/ApiKeyManagerTest.kt` (if exists, else create)

**Step 1: Update ApiKeyManager**

The current `ApiKeyManager` only stores Groq keys. Add generic per-provider key storage while keeping the existing `getGroqApiKey()`/`setGroqApiKey()` methods for backward compatibility (STT still uses them).

In `app/src/main/java/com/voxink/app/data/local/ApiKeyManager.kt`:

```kotlin
package com.voxink.app.data.local

import android.content.SharedPreferences
import com.voxink.app.data.model.LlmProvider
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

        companion object {
            private const val KEY_GROQ = "groq_api_key"
            private const val KEY_PREFIX = "api_key_"
            private const val KEY_CUSTOM_BASE_URL = "custom_llm_base_url"
        }
    }
```

**Step 2: Write tests**

Create or update `app/src/test/java/com/voxink/app/data/local/ApiKeyManagerTest.kt`:

```kotlin
package com.voxink.app.data.local

import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import com.voxink.app.data.model.LlmProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ApiKeyManagerTest {
    private val prefs: SharedPreferences = mockk(relaxed = true)
    private val editor: SharedPreferences.Editor = mockk(relaxed = true)
    private lateinit var manager: ApiKeyManager

    @BeforeEach
    fun setUp() {
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        manager = ApiKeyManager(prefs)
    }

    @Test
    fun `getApiKey for Groq delegates to getGroqApiKey`() {
        every { prefs.getString("groq_api_key", null) } returns "gsk_test"
        assertThat(manager.getApiKey(LlmProvider.Groq)).isEqualTo("gsk_test")
    }

    @Test
    fun `getApiKey for OpenAI uses provider-specific key`() {
        every { prefs.getString("api_key_openai", null) } returns "sk_test"
        assertThat(manager.getApiKey(LlmProvider.OpenAI)).isEqualTo("sk_test")
    }

    @Test
    fun `setApiKey for Groq delegates to setGroqApiKey`() {
        manager.setApiKey(LlmProvider.Groq, "gsk_new")
        verify { editor.putString("groq_api_key", "gsk_new") }
    }

    @Test
    fun `setApiKey for OpenRouter uses provider-specific key`() {
        manager.setApiKey(LlmProvider.OpenRouter, "or_key")
        verify { editor.putString("api_key_openrouter", "or_key") }
    }

    @Test
    fun `isKeyConfigured returns true when key exists`() {
        every { prefs.getString("api_key_openai", null) } returns "sk_test"
        assertThat(manager.isKeyConfigured(LlmProvider.OpenAI)).isTrue()
    }

    @Test
    fun `isKeyConfigured returns false when key is null`() {
        every { prefs.getString("api_key_openai", null) } returns null
        assertThat(manager.isKeyConfigured(LlmProvider.OpenAI)).isFalse()
    }

    @Test
    fun `custom base URL storage works`() {
        manager.setCustomBaseUrl("https://my-server.com/")
        verify { editor.putString("custom_llm_base_url", "https://my-server.com/") }
    }
}
```

**Step 3: Run tests**

Run: `./gradlew testDebugUnitTest --tests "com.voxink.app.data.local.ApiKeyManagerTest" --no-daemon`
Expected: All tests PASS.

**Step 4: Commit**

```bash
git add app/src/main/java/com/voxink/app/data/local/ApiKeyManager.kt \
       app/src/test/java/com/voxink/app/data/local/ApiKeyManagerTest.kt
git commit -m "feat: add per-provider API key storage in ApiKeyManager

Generic getApiKey/setApiKey for all providers. Groq delegates to
existing key for backward compatibility. Custom base URL storage."
```

---

## Task 5: Add provider/model persistence to PreferencesManager

**Files:**
- Modify: `app/src/main/java/com/voxink/app/data/local/PreferencesManager.kt`

**Step 1: Add provider flow and setter**

In `app/src/main/java/com/voxink/app/data/local/PreferencesManager.kt`:

Add import: `import com.voxink.app.data.model.LlmProvider`

Add key in companion:
```kotlin
private val LLM_PROVIDER_KEY = stringPreferencesKey("llm_provider")
private val CUSTOM_LLM_MODEL_KEY = stringPreferencesKey("custom_llm_model")
```

Add flows (after `llmModelFlow`):
```kotlin
val llmProviderFlow: Flow<LlmProvider> =
    context.dataStore.data.map { prefs ->
        LlmProvider.fromKey(prefs[LLM_PROVIDER_KEY] ?: LlmProvider.DEFAULT.key)
    }

val customLlmModelFlow: Flow<String> =
    context.dataStore.data.map { prefs ->
        prefs[CUSTOM_LLM_MODEL_KEY] ?: ""
    }
```

Add setters (after `setLlmModel`):
```kotlin
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
```

**Step 2: Build to verify**

Run: `./gradlew assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL.

**Step 3: Commit**

```bash
git add app/src/main/java/com/voxink/app/data/local/PreferencesManager.kt
git commit -m "feat: add LLM provider and custom model persistence"
```

---

## Task 6: Refactor LlmRepository to use ChatCompletionApiFactory

**Files:**
- Modify: `app/src/main/java/com/voxink/app/data/repository/LlmRepository.kt`
- Modify: `app/src/test/java/com/voxink/app/data/repository/LlmRepositoryTest.kt`

**Step 1: Update LlmRepository**

Replace the `GroqApi` dependency with `ChatCompletionApiFactory`. Add `provider` and `customBaseUrl` parameters to `refine()`.

```kotlin
package com.voxink.app.data.repository

import com.voxink.app.data.model.LlmProvider
import com.voxink.app.data.model.RefinementPrompt
import com.voxink.app.data.model.SttLanguage
import com.voxink.app.data.model.ToneStyle
import com.voxink.app.data.remote.ChatCompletionApiFactory
import com.voxink.app.data.remote.ChatCompletionRequest
import com.voxink.app.data.remote.ChatMessage
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmRepository
    @Inject
    constructor(
        private val apiFactory: ChatCompletionApiFactory,
    ) {
        suspend fun refine(
            text: String,
            language: SttLanguage,
            apiKey: String,
            model: String = LLM_MODEL,
            vocabulary: List<String> = emptyList(),
            customPrompt: String? = null,
            tone: ToneStyle = ToneStyle.Casual,
            provider: LlmProvider = LlmProvider.Groq,
            customBaseUrl: String? = null,
        ): Result<String> {
            if (apiKey.isBlank()) {
                return Result.failure(IllegalStateException("API key not configured"))
            }
            if (text.isBlank()) {
                return Result.failure(IllegalArgumentException("Text is empty"))
            }

            return try {
                val api = if (provider == LlmProvider.Custom && !customBaseUrl.isNullOrBlank()) {
                    apiFactory.createForCustom(customBaseUrl)
                } else {
                    apiFactory.create(provider)
                }
                val systemPrompt = RefinementPrompt.forLanguage(language, vocabulary, customPrompt, tone)
                val request =
                    ChatCompletionRequest(
                        model = model,
                        messages =
                            listOf(
                                ChatMessage(role = "system", content = systemPrompt),
                                ChatMessage(role = "user", content = text),
                            ),
                        temperature = TEMPERATURE,
                        maxTokens = MAX_TOKENS,
                    )
                val response = api.chatCompletion("Bearer $apiKey", request)
                val content =
                    response.choices.firstOrNull()?.message?.content
                        ?: return Result.failure(IllegalStateException("No response content"))
                Result.success(content)
            } catch (e: IOException) {
                Result.failure(e)
            } catch (e: retrofit2.HttpException) {
                Result.failure(e)
            }
        }

        companion object {
            private const val LLM_MODEL = "llama-3.3-70b-versatile"
            private const val TEMPERATURE = 0.3
            private const val MAX_TOKENS = 2048
        }
    }
```

**Step 2: Update LlmRepositoryTest**

Replace the `GroqApi` mock setup with `ChatCompletionApiFactory` using MockWebServer. The test creates a factory pointed at MockWebServer, so all provider calls hit the mock.

```kotlin
package com.voxink.app.data.repository

import com.google.common.truth.Truth.assertThat
import com.voxink.app.data.model.LlmProvider
import com.voxink.app.data.model.SttLanguage
import com.voxink.app.data.remote.ChatCompletionApiFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LlmRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: LlmRepository

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true }
        val client = OkHttpClient()
        val factory = ChatCompletionApiFactory(client, json)
        repository = LlmRepository(factory)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueueSuccess(content: String = "Polished text") {
        server.enqueue(
            MockResponse()
                .setBody(
                    """{"id":"c1","choices":[{"index":0,""" +
                        """"message":{"role":"assistant","content":"$content"}}]}""",
                )
                .setHeader("Content-Type", "application/json"),
        )
    }

    @Test
    fun `should return refined text on success`() =
        runTest {
            enqueueSuccess()
            val result = repository.refine(
                "raw text", SttLanguage.English, "test-key",
                provider = LlmProvider.Custom,
                customBaseUrl = server.url("/").toString(),
            )
            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isEqualTo("Polished text")
        }

    @Test
    fun `should send Bearer authorization header`() =
        runTest {
            enqueueSuccess("ok")
            repository.refine(
                "text", SttLanguage.Auto, "my-api-key",
                provider = LlmProvider.Custom,
                customBaseUrl = server.url("/").toString(),
            )
            val request = server.takeRequest()
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer my-api-key")
        }

    @Test
    fun `should include system prompt and user text in request body`() =
        runTest {
            enqueueSuccess("ok")
            repository.refine(
                "test input", SttLanguage.Chinese, "key",
                provider = LlmProvider.Custom,
                customBaseUrl = server.url("/").toString(),
            )
            val request = server.takeRequest()
            val body = request.body.readUtf8()
            assertThat(body).contains("\"role\":\"system\"")
            assertThat(body).contains("\"role\":\"user\"")
            assertThat(body).contains("test input")
        }

    @Test
    fun `should return failure on empty API key`() =
        runTest {
            val result = repository.refine("text", SttLanguage.Auto, "")
            assertThat(result.isFailure).isTrue()
        }

    @Test
    fun `should return failure on empty text`() =
        runTest {
            val result = repository.refine("", SttLanguage.Auto, "key")
            assertThat(result.isFailure).isTrue()
        }

    @Test
    fun `should return failure on server error`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(500))
            val result = repository.refine(
                "text", SttLanguage.English, "key",
                provider = LlmProvider.Custom,
                customBaseUrl = server.url("/").toString(),
            )
            assertThat(result.isFailure).isTrue()
        }

    @Test
    fun `should use provided model name in request body`() =
        runTest {
            enqueueSuccess("ok")
            repository.refine(
                "text", SttLanguage.English, "key",
                model = "gpt-4o-mini",
                provider = LlmProvider.Custom,
                customBaseUrl = server.url("/").toString(),
            )
            val request = server.takeRequest()
            val body = request.body.readUtf8()
            assertThat(body).contains("\"model\":\"gpt-4o-mini\"")
        }

    @Test
    fun `should include vocabulary in system prompt when provided`() =
        runTest {
            enqueueSuccess("ok")
            repository.refine(
                "text", SttLanguage.Chinese, "key",
                vocabulary = listOf("語墨", "Claude"),
                provider = LlmProvider.Custom,
                customBaseUrl = server.url("/").toString(),
            )
            val request = server.takeRequest()
            val body = request.body.readUtf8()
            assertThat(body).contains("語墨")
        }
}
```

**Step 3: Run tests**

Run: `./gradlew testDebugUnitTest --tests "com.voxink.app.data.repository.LlmRepositoryTest" --no-daemon`
Expected: All 8 tests PASS.

**Step 4: Commit**

```bash
git add app/src/main/java/com/voxink/app/data/repository/LlmRepository.kt \
       app/src/test/java/com/voxink/app/data/repository/LlmRepositoryTest.kt
git commit -m "refactor: LlmRepository uses ChatCompletionApiFactory

Accepts provider + customBaseUrl parameters. No longer depends
on GroqApi directly. Tests use MockWebServer via Custom provider."
```

---

## Task 7: Thread provider through the call chain

**Files:**
- Modify: `app/src/main/java/com/voxink/app/domain/usecase/RefineTextUseCase.kt`
- Modify: `app/src/main/java/com/voxink/app/ime/RecordingController.kt`
- Modify: `app/src/test/java/com/voxink/app/ime/RecordingControllerTest.kt`

**Step 1: Update RefineTextUseCase**

Add `provider`, `customBaseUrl` parameters:

```kotlin
package com.voxink.app.domain.usecase

import com.voxink.app.data.model.LlmProvider
import com.voxink.app.data.model.SttLanguage
import com.voxink.app.data.model.ToneStyle
import com.voxink.app.data.repository.LlmRepository
import javax.inject.Inject

class RefineTextUseCase
    @Inject
    constructor(
        private val llmRepository: LlmRepository,
    ) {
        suspend operator fun invoke(
            text: String,
            language: SttLanguage,
            apiKey: String,
            model: String = "llama-3.3-70b-versatile",
            vocabulary: List<String> = emptyList(),
            customPrompt: String? = null,
            tone: ToneStyle = ToneStyle.Casual,
            provider: LlmProvider = LlmProvider.Groq,
            customBaseUrl: String? = null,
        ): Result<String> = llmRepository.refine(text, language, apiKey, model, vocabulary, customPrompt, tone, provider, customBaseUrl)
    }
```

**Step 2: Update RecordingController**

Add `llmProvider` and `customLlmModel` fields, collect their flows, resolve the correct API key and model at call time.

In `RecordingController.kt`:

Add fields after `toneStyle`:
```kotlin
private var llmProvider: LlmProvider = LlmProvider.DEFAULT
private var customLlmModel: String = ""
```

Add to `init` block:
```kotlin
scope.launch { preferencesManager.llmProviderFlow.collect { llmProvider = it } }
scope.launch { preferencesManager.customLlmModelFlow.collect { customLlmModel = it } }
```

In `onStopRecording`, change the API key and model resolution:

Replace:
```kotlin
val apiKey = apiKeyManager.getGroqApiKey()
```
With:
```kotlin
val apiKey = apiKeyManager.getApiKey(llmProvider)
    ?: apiKeyManager.getGroqApiKey()  // fallback to Groq key for backward compat
```

Replace the `refineTextUseCase` call:
```kotlin
val resolvedModel = if (llmProvider == LlmProvider.Custom) {
    customLlmModel.ifBlank { llmModel }
} else {
    llmModel
}
val customBaseUrl = if (llmProvider == LlmProvider.Custom) {
    apiKeyManager.getCustomBaseUrl()
} else {
    null
}
val refinedResult = refineTextUseCase(
    originalText, language, apiKey, resolvedModel, allVocabulary,
    customPrompt, toneStyle, llmProvider, customBaseUrl,
)
```

**Step 3: Update RecordingControllerTest**

Add mock flows:
```kotlin
private val llmProviderFlow = MutableStateFlow<LlmProvider>(LlmProvider.Groq)
private val customLlmModelFlow = MutableStateFlow("")
```

In `setUp()`:
```kotlin
every { preferencesManager.llmProviderFlow } returns llmProviderFlow
every { preferencesManager.customLlmModelFlow } returns customLlmModelFlow
```

Also add import: `import com.voxink.app.data.model.LlmProvider`

**Step 4: Run all tests**

Run: `./gradlew testDebugUnitTest --no-daemon`
Expected: All tests PASS.

**Step 5: Commit**

```bash
git add app/src/main/java/com/voxink/app/domain/usecase/RefineTextUseCase.kt \
       app/src/main/java/com/voxink/app/ime/RecordingController.kt \
       app/src/test/java/com/voxink/app/ime/RecordingControllerTest.kt
git commit -m "feat: thread LLM provider through refinement call chain

RecordingController resolves provider-specific API key and model,
passes through RefineTextUseCase → LlmRepository."
```

---

## Task 8: Update Settings UI for multi-provider model selection

**Files:**
- Modify: `app/src/main/java/com/voxink/app/ui/settings/SettingsUiState.kt`
- Modify: `app/src/main/java/com/voxink/app/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/voxink/app/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`
- Modify: `app/src/test/java/com/voxink/app/ui/settings/SettingsViewModelTest.kt`

**Step 1: Update SettingsUiState**

Add fields:
```kotlin
val llmProvider: LlmProvider = LlmProvider.DEFAULT,
val customLlmModel: String = "",
val customBaseUrl: String = "",
val providerApiKeys: Map<String, Boolean> = emptyMap(),  // provider.key → isConfigured
```

Add imports: `import com.voxink.app.data.model.LlmProvider`

**Step 2: Update SettingsViewModel**

Add collectors in `init`:
```kotlin
viewModelScope.launch {
    preferencesManager.llmProviderFlow.collect { provider ->
        _uiState.update { it.copy(llmProvider = provider) }
    }
}
viewModelScope.launch {
    preferencesManager.customLlmModelFlow.collect { model ->
        _uiState.update { it.copy(customLlmModel = model) }
    }
}
```

Update `init` to check all provider keys:
```kotlin
_uiState.update {
    it.copy(
        // ... existing fields ...
        providerApiKeys = LlmProvider.all.associate { p ->
            p.key to apiKeyManager.isKeyConfigured(p)
        },
        customBaseUrl = apiKeyManager.getCustomBaseUrl() ?: "",
    )
}
```

Add setters:
```kotlin
fun setLlmProvider(provider: LlmProvider) {
    viewModelScope.launch {
        preferencesManager.setLlmProvider(provider)
        // Auto-select provider's default model
        preferencesManager.setLlmModel(provider.defaultModelId)
    }
}

fun saveProviderApiKey(provider: LlmProvider, key: String) {
    apiKeyManager.setApiKey(provider, key)
    _uiState.update {
        it.copy(
            providerApiKeys = it.providerApiKeys + (provider.key to key.isNotBlank()),
            // Keep Groq state in sync
            isApiKeyConfigured = if (provider == LlmProvider.Groq) key.isNotBlank() else it.isApiKeyConfigured,
            apiKeyDisplay = if (provider == LlmProvider.Groq) maskApiKey(key) else it.apiKeyDisplay,
        )
    }
}

fun setCustomLlmModel(model: String) {
    viewModelScope.launch { preferencesManager.setCustomLlmModel(model) }
}

fun setCustomBaseUrl(url: String) {
    apiKeyManager.setCustomBaseUrl(url)
    _uiState.update { it.copy(customBaseUrl = url) }
}
```

**Step 3: Rewrite LlmModelSection in SettingsScreen**

Replace the hardcoded `LlmModelSection` with a new section that:
1. Shows provider tabs (Groq / OpenAI / OpenRouter / Custom) using `FilterChip`
2. Shows provider-specific API key field when that provider doesn't have a key configured
3. Shows the model list for the selected provider using `RadioRow` with tag badges
4. Shows a "Custom model name" text field for the Custom provider (and optionally for other providers)

```kotlin
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LlmProviderSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    SectionHeader(stringResource(R.string.settings_llm_provider_section))

    // Provider tabs
    val providerLabels = mapOf(
        LlmProvider.Groq to "Groq",
        LlmProvider.OpenAI to "OpenAI",
        LlmProvider.OpenRouter to "OpenRouter",
        LlmProvider.Custom to stringResource(R.string.provider_custom),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LlmProvider.all.forEach { provider ->
            FilterChip(
                selected = provider == state.llmProvider,
                onClick = { viewModel.setLlmProvider(provider) },
                label = { Text(providerLabels[provider] ?: provider.key) },
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    // API key for selected provider (if not Groq which has its own section)
    if (state.llmProvider != LlmProvider.Groq) {
        ProviderApiKeyField(state, viewModel)
        Spacer(Modifier.height(8.dp))
    }

    // Custom provider extras
    if (state.llmProvider == LlmProvider.Custom) {
        CustomProviderFields(state, viewModel)
    } else {
        // Model list for built-in providers
        ProviderModelList(state, viewModel)
    }
}

@Composable
private fun ProviderApiKeyField(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    var keyInput by remember { mutableStateOf("") }
    val isConfigured = state.providerApiKeys[state.llmProvider.key] == true
    if (isConfigured) {
        Text(
            stringResource(R.string.provider_key_configured),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    OutlinedTextField(
        value = keyInput,
        onValueChange = { keyInput = it },
        label = { Text(stringResource(R.string.provider_api_key_hint, providerDisplayName(state.llmProvider))) },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = {
            if (keyInput.isNotBlank()) {
                viewModel.saveProviderApiKey(state.llmProvider, keyInput)
                keyInput = ""
            }
        },
        modifier = Modifier.padding(top = 4.dp),
    ) { Text(stringResource(R.string.settings_save)) }
}

@Composable
private fun ProviderModelList(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    val tagLabels = mapOf(
        "recommended" to stringResource(R.string.model_tag_recommended),
        "fast" to stringResource(R.string.model_tag_fast),
        "cheapest" to stringResource(R.string.model_tag_cheapest),
        "quality" to stringResource(R.string.model_tag_quality),
        "best_chinese" to stringResource(R.string.model_tag_best_chinese),
    )
    state.llmProvider.models.forEach { model ->
        val label = buildString {
            append(model.label)
            model.tag?.let { tag ->
                append(" — ")
                append(tagLabels[tag] ?: tag)
            }
        }
        RadioRow(label, state.llmModel == model.id) {
            viewModel.setLlmModel(model.id)
        }
    }
}

@Composable
private fun CustomProviderFields(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    OutlinedTextField(
        value = state.customBaseUrl,
        onValueChange = { viewModel.setCustomBaseUrl(it) },
        label = { Text(stringResource(R.string.provider_custom_base_url)) },
        placeholder = { Text("https://api.example.com/") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = state.customLlmModel,
        onValueChange = { viewModel.setCustomLlmModel(it) },
        label = { Text(stringResource(R.string.provider_custom_model)) },
        placeholder = { Text("llama3.1:8b") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun providerDisplayName(provider: LlmProvider): String =
    when (provider) {
        LlmProvider.Groq -> "Groq"
        LlmProvider.OpenAI -> "OpenAI"
        LlmProvider.OpenRouter -> "OpenRouter"
        LlmProvider.Custom -> "Custom"
    }
```

In the main settings body, replace `LlmModelSection(state, viewModel)` with `LlmProviderSection(state, viewModel)`.

**Step 4: Add string resources**

In `app/src/main/res/values/strings.xml`, add:
```xml
<!-- LLM Provider -->
<string name="settings_llm_provider_section">LLM Provider &amp; Model</string>
<string name="provider_custom">Custom</string>
<string name="provider_key_configured">API key configured ✓</string>
<string name="provider_api_key_hint">%1$s API Key</string>
<string name="provider_custom_base_url">Base URL</string>
<string name="provider_custom_model">Model name</string>
<string name="model_tag_recommended">Recommended</string>
<string name="model_tag_fast">Fast</string>
<string name="model_tag_cheapest">Cheapest</string>
<string name="model_tag_quality">Quality</string>
<string name="model_tag_best_chinese">Best for Chinese</string>
```

In `app/src/main/res/values-zh-rTW/strings.xml`, add:
```xml
<!-- LLM Provider -->
<string name="settings_llm_provider_section">潤稿模型</string>
<string name="provider_custom">自訂</string>
<string name="provider_key_configured">API 金鑰已設定 ✓</string>
<string name="provider_api_key_hint">%1$s API 金鑰</string>
<string name="provider_custom_base_url">伺服器網址</string>
<string name="provider_custom_model">模型名稱</string>
<string name="model_tag_recommended">推薦</string>
<string name="model_tag_fast">極速</string>
<string name="model_tag_cheapest">最省錢</string>
<string name="model_tag_quality">品質最佳</string>
<string name="model_tag_best_chinese">中文最強</string>
```

Also remove the old LLM model strings that are no longer needed:
- `settings_llm_model_section`
- `settings_llm_model_llama`
- `settings_llm_model_gpt_oss_120b`
- `settings_llm_model_gpt_oss_20b`
- `settings_llm_model_description`

**Step 5: Update SettingsViewModelTest**

Add mocks for new flows in `setUp()`:
```kotlin
every { preferencesManager.llmProviderFlow } returns flowOf(LlmProvider.Groq)
every { preferencesManager.customLlmModelFlow } returns flowOf("")
```

**Step 6: Build and run tests**

Run: `./gradlew assembleDebug --no-daemon && ./gradlew testDebugUnitTest --no-daemon`
Expected: BUILD SUCCESSFUL, all tests PASS.

**Step 7: Commit**

```bash
git add app/src/main/java/com/voxink/app/ui/settings/SettingsUiState.kt \
       app/src/main/java/com/voxink/app/ui/settings/SettingsViewModel.kt \
       app/src/main/java/com/voxink/app/ui/settings/SettingsScreen.kt \
       app/src/main/res/values/strings.xml \
       app/src/main/res/values-zh-rTW/strings.xml \
       app/src/test/java/com/voxink/app/ui/settings/SettingsViewModelTest.kt
git commit -m "feat: multi-provider LLM selection UI in Settings

FilterChip provider tabs, per-provider API key input, curated
model lists with tags, custom provider base URL and model fields."
```

---

## Task 9: Final build + test verification

**Step 1: Clean build**

Run: `./gradlew clean assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL.

**Step 2: Run all tests**

Run: `./gradlew testDebugUnitTest --no-daemon`
Expected: All tests PASS.

**Step 3: Verify provider references**

Grep for consistency:
```bash
grep -r "LlmProvider" app/src/main/ --include="*.kt" -l
```
Expected files:
- `data/model/LlmProvider.kt`
- `data/local/ApiKeyManager.kt`
- `data/local/PreferencesManager.kt`
- `data/repository/LlmRepository.kt`
- `data/remote/ChatCompletionApiFactory.kt`
- `domain/usecase/RefineTextUseCase.kt`
- `ime/RecordingController.kt`
- `ui/settings/SettingsUiState.kt`
- `ui/settings/SettingsViewModel.kt`
- `ui/settings/SettingsScreen.kt`

**Step 4: Verify no remaining hardcoded Groq-only LLM patterns**

```bash
grep -r "groqApi\.chatCompletion" app/src/main/ --include="*.kt"
```
Expected: ZERO matches (LlmRepository no longer calls groqApi.chatCompletion).

The `GroqApi` interface still exists for STT (`transcribe`), which is correct — STT remains Groq-only.

**Step 5: Commit if any fixes needed**

---

## Summary

| Task | What | Files |
|------|------|-------|
| 1 | `LlmProvider` + `LlmModelOption` models | model + test |
| 2 | `ChatCompletionApi` interface (extracted from GroqApi) | remote |
| 3 | `ChatCompletionApiFactory` with caching | remote + test |
| 4 | Per-provider API key storage in `ApiKeyManager` | local + test |
| 5 | Provider/model persistence in `PreferencesManager` | local |
| 6 | Refactor `LlmRepository` to use factory | repository + test |
| 7 | Thread provider through call chain | UseCase → Controller + test |
| 8 | Multi-provider Settings UI | UI + strings |
| 9 | Final verification | build + tests |

### Design Notes

- **STT stays Groq-only** — No provider selection for speech-to-text (all providers use Groq Whisper via existing `GroqApi` interface)
- **All LLM providers use OpenAI-compatible format** — Groq, OpenAI, and OpenRouter all implement `/v1/chat/completions`, so one `ChatCompletionApi` Retrofit interface works for all
- **Groq API key shared** — STT and LLM both use the Groq API key when Groq is selected; `ApiKeyManager.getApiKey(Groq)` delegates to `getGroqApiKey()`
- **Custom provider** — User provides base URL + model name + API key; covers Ollama, self-hosted, and any future provider
- **Model lists are hardcoded** — Intentionally kept simple; the custom model name field covers new models. Lists can be updated in app releases
- **Old `LlmModelSection` removed** — Replaced by `LlmProviderSection` with tabs + per-provider model radio buttons
- **Backward compatible** — Existing Groq users keep working with zero migration; `LlmProvider.DEFAULT` is Groq
