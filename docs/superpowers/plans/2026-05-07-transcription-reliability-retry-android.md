# Android Transcription Reliability Retry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the desktop transcription reliability fixes to Android: first-class OpenAI Audio API STT, visible short/silent recording failures, transient STT retry, live recording chunking, readable provider errors, and manual resend from transcription history.

**Architecture:** Keep IME orchestration in `RecordingController` and API calls inside repositories/use cases. Add a dedicated `SttProvider` model instead of reusing `LlmProvider`, route Groq/OpenAI/custom STT through a neutral `SttApi`, normalize provider errors in `SttRepository`, chunk live PCM in `TranscribeAudioUseCase`, and persist failed live recordings through Room + private app storage so the history screen can retry them.

**Tech Stack:** Kotlin, Android IME, Hilt, Coroutines/Flow, Retrofit + OkHttp, Room, DataStore, Jetpack Compose, MockK, Turbine, JUnit 5.

---

## Problem Mapping

The desktop fix addressed these user-visible failures:

- OpenAI STT was advertised but not fully treated as a first-class Audio API provider.
- Provider failures were truncated or too generic to diagnose.
- Short or silent recordings could disappear without a readable error.
- Long live recordings were sent as one STT call, making transient failures lose the whole utterance.
- Failed live recordings were not saved for manual resend.

Android has the same risk surfaces:

- `SttRepository` always uses the injected Groq API unless `customSttBaseUrl` is set.
- `GroqApi.transcribe()` uses `@POST("openai/v1/audio/transcriptions")`, which is correct with Groq base URL `https://api.groq.com/` but wrong for OpenAI base URL `https://api.openai.com/`.
- `RecordingController` currently returns `ImeUiState.Idle` for silent or too-short audio.
- `TranscribeAudioUseCase` encodes and sends the full live PCM buffer as one WAV.
- `TranscriptionEntity` has no `status`, `errorMessage`, `audioPath`, or `provider`.
- `TranscriptionScreen` can display history rows but has no failed-row retry action.

## File Structure

- Create `app/src/main/java/com/voxpen/app/data/model/SttProvider.kt`
  - Dedicated STT provider enum with base URLs, default models, and model list.

- Create `app/src/main/java/com/voxpen/app/data/remote/SttApi.kt`
  - Neutral OpenAI-compatible audio transcription Retrofit interface using `@POST("v1/audio/transcriptions")`.

- Modify `app/src/main/java/com/voxpen/app/data/remote/SttApiFactory.kt`
  - Create cached STT APIs for Groq, OpenAI, and custom base URLs.

- Modify `app/src/main/java/com/voxpen/app/data/repository/SttRepository.kt`
  - Accept `SttProvider`, choose response format per provider/model, retry transient failures once, normalize errors.

- Create `app/src/main/java/com/voxpen/app/util/LiveAudioChunker.kt`
  - Split live PCM into 60-second frame-aligned chunks.

- Create `app/src/main/java/com/voxpen/app/util/RecordingValidator.kt`
  - Distinguish too-short, silent, and usable recordings.

- Modify `app/src/main/java/com/voxpen/app/domain/usecase/TranscribeAudioUseCase.kt`
  - Chunk live PCM, encode each chunk, transcribe sequentially, and join non-empty texts.

- Create `app/src/main/java/com/voxpen/app/data/local/RecordingStore.kt`
  - Save/read/delete failed live WAV files under private app storage.

- Modify `app/src/main/java/com/voxpen/app/data/local/TranscriptionEntity.kt`
  - Add `status`, `errorMessage`, `audioPath`, and `provider`.

- Modify `app/src/main/java/com/voxpen/app/data/local/AppDatabase.kt`
  - Bump Room version to 4 and add `MIGRATION_3_4`.

- Modify `app/src/main/java/com/voxpen/app/data/repository/TranscriptionRepository.kt`
  - Add failed insert and update-after-retry helpers.

- Create `app/src/main/java/com/voxpen/app/domain/usecase/RetryTranscriptionUseCase.kt`
  - Load failed row, read saved WAV, resend with current STT provider, update the same row.

- Modify `app/src/main/java/com/voxpen/app/data/local/PreferencesManager.kt`
  - Persist `stt_provider`.

- Modify `app/src/main/java/com/voxpen/app/data/local/ApiKeyManager.kt`
  - Add STT-provider-specific key helpers.

- Modify `app/src/main/java/com/voxpen/app/ime/RecordingController.kt`
  - Use `RecordingValidator`, selected STT provider/key, failed-row persistence, and readable errors.

- Modify `app/src/main/java/com/voxpen/app/ime/VoxPenIMEEntryPoint.kt`
  - Expose `TranscriptionRepository` and `RecordingStore` to IME controller construction.

- Modify `app/src/main/java/com/voxpen/app/ui/settings/SettingsUiState.kt`
- Modify `app/src/main/java/com/voxpen/app/ui/settings/SettingsViewModel.kt`
- Modify `app/src/main/java/com/voxpen/app/ui/settings/SettingsScreen.kt`
  - Add STT provider selector, OpenAI STT models, and provider-specific API keys.

- Modify `app/src/main/java/com/voxpen/app/ui/transcription/TranscriptionViewModel.kt`
- Modify `app/src/main/java/com/voxpen/app/ui/transcription/TranscriptionScreen.kt`
- Modify `app/src/main/java/com/voxpen/app/ui/transcription/TranscriptionEntryPoint.kt`
  - Display failed rows with full error text and retry button.

- Modify `app/src/main/res/values/strings.xml`
- Modify `app/src/main/res/values-zh-rTW/strings.xml`
  - Add strings for STT provider, failed status, and retry.

---

### Task 1: Add STT Provider Model And Settings Persistence

**Files:**
- Test: `app/src/test/java/com/voxpen/app/data/model/SttProviderTest.kt`
- Modify: `app/src/main/java/com/voxpen/app/data/model/SttProvider.kt`
- Modify: `app/src/main/java/com/voxpen/app/data/local/PreferencesManager.kt`
- Test: `app/src/test/java/com/voxpen/app/data/local/PreferencesManagerTest.kt`

- [ ] **Step 1: Write the failing provider model test**

```kotlin
package com.voxpen.app.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SttProviderTest {
    @Test
    fun `providers expose correct keys and defaults`() {
        assertThat(SttProvider.Groq.key).isEqualTo("groq")
        assertThat(SttProvider.Groq.baseUrl).isEqualTo("https://api.groq.com/openai/")
        assertThat(SttProvider.Groq.defaultModelId).isEqualTo("whisper-large-v3-turbo")

        assertThat(SttProvider.OpenAI.key).isEqualTo("openai")
        assertThat(SttProvider.OpenAI.baseUrl).isEqualTo("https://api.openai.com/")
        assertThat(SttProvider.OpenAI.defaultModelId).isEqualTo("whisper-1")

        assertThat(SttProvider.Custom.key).isEqualTo("custom")
        assertThat(SttProvider.DEFAULT).isEqualTo(SttProvider.Groq)
    }

    @Test
    fun `openai models include current audio transcription models`() {
        assertThat(SttProvider.OpenAI.models.map { it.id }).containsExactly(
            "whisper-1",
            "gpt-4o-transcribe",
            "gpt-4o-mini-transcribe",
        ).inOrder()
    }

    @Test
    fun `fromKey defaults to groq`() {
        assertThat(SttProvider.fromKey("openai")).isEqualTo(SttProvider.OpenAI)
        assertThat(SttProvider.fromKey("custom")).isEqualTo(SttProvider.Custom)
        assertThat(SttProvider.fromKey("unknown")).isEqualTo(SttProvider.Groq)
    }
}
```

- [ ] **Step 2: Run the model test and verify it fails**

```bash
./gradlew testDebugUnitTest --tests com.voxpen.app.data.model.SttProviderTest
```

Expected: FAIL because `SttProvider` does not exist.

- [ ] **Step 3: Add `SttProvider.kt`**

```kotlin
package com.voxpen.app.data.model

sealed class SttProvider(
    val key: String,
    val displayName: String,
    val baseUrl: String?,
    val defaultModelId: String,
    val models: List<SttModel>,
) {
    data object Groq : SttProvider(
        key = "groq",
        displayName = "Groq",
        baseUrl = "https://api.groq.com/openai/",
        defaultModelId = "whisper-large-v3-turbo",
        models = listOf(
            SttModel("whisper-large-v3-turbo", "whisper-large-v3-turbo"),
            SttModel("whisper-large-v3", "whisper-large-v3"),
        ),
    )

    data object OpenAI : SttProvider(
        key = "openai",
        displayName = "OpenAI",
        baseUrl = "https://api.openai.com/",
        defaultModelId = "whisper-1",
        models = listOf(
            SttModel("whisper-1", "whisper-1"),
            SttModel("gpt-4o-transcribe", "gpt-4o-transcribe"),
            SttModel("gpt-4o-mini-transcribe", "gpt-4o-mini-transcribe"),
        ),
    )

    data object Custom : SttProvider(
        key = "custom",
        displayName = "Custom",
        baseUrl = null,
        defaultModelId = "whisper-large-v3-turbo",
        models = emptyList(),
    )

    companion object {
        val DEFAULT: SttProvider get() = Groq
        val all: List<SttProvider> get() = listOf(Groq, OpenAI, Custom)

        fun fromKey(key: String): SttProvider =
            when (key) {
                Groq.key -> Groq
                OpenAI.key -> OpenAI
                Custom.key -> Custom
                else -> DEFAULT
            }
    }
}

data class SttModel(
    val id: String,
    val label: String,
)
```

- [ ] **Step 4: Add failing preference tests**

Add to `PreferencesManagerTest`:

```kotlin
@Test
fun `default STT provider should be Groq`() {
    assertThat(PreferencesManager.DEFAULT_STT_PROVIDER).isEqualTo(SttProvider.Groq)
}

@Test
fun `should map STT provider keys`() {
    assertThat(PreferencesManager.sttProviderFromKey("groq")).isEqualTo(SttProvider.Groq)
    assertThat(PreferencesManager.sttProviderFromKey("openai")).isEqualTo(SttProvider.OpenAI)
    assertThat(PreferencesManager.sttProviderFromKey("custom")).isEqualTo(SttProvider.Custom)
    assertThat(PreferencesManager.sttProviderFromKey("bad")).isEqualTo(SttProvider.Groq)
    assertThat(PreferencesManager.sttProviderToKey(SttProvider.OpenAI)).isEqualTo("openai")
}
```

- [ ] **Step 5: Persist `stt_provider` in `PreferencesManager`**

Add imports:

```kotlin
import com.voxpen.app.data.model.SttProvider
```

Add flow and setter:

```kotlin
val sttProviderFlow: Flow<SttProvider> =
    dataStore.data.map { prefs ->
        sttProviderFromKey(prefs[STT_PROVIDER_KEY] ?: DEFAULT_STT_PROVIDER.key)
    }

suspend fun setSttProvider(provider: SttProvider) {
    dataStore.edit { prefs ->
        prefs[STT_PROVIDER_KEY] = sttProviderToKey(provider)
    }
}
```

Add companion values:

```kotlin
val DEFAULT_STT_PROVIDER: SttProvider = SttProvider.DEFAULT
private val STT_PROVIDER_KEY = stringPreferencesKey("stt_provider")

fun sttProviderFromKey(key: String): SttProvider = SttProvider.fromKey(key)

fun sttProviderToKey(provider: SttProvider): String = provider.key
```

- [ ] **Step 6: Run tests**

```bash
./gradlew testDebugUnitTest --tests com.voxpen.app.data.model.SttProviderTest --tests com.voxpen.app.data.local.PreferencesManagerTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/voxpen/app/data/model/SttProvider.kt app/src/main/java/com/voxpen/app/data/local/PreferencesManager.kt app/src/test/java/com/voxpen/app/data/model/SttProviderTest.kt app/src/test/java/com/voxpen/app/data/local/PreferencesManagerTest.kt
git commit -m "feat: add STT provider settings"
```

---

### Task 2: Route Groq, OpenAI, And Custom STT Through A Neutral API

**Files:**
- Create: `app/src/main/java/com/voxpen/app/data/remote/SttApi.kt`
- Modify: `app/src/main/java/com/voxpen/app/data/remote/SttApiFactory.kt`
- Modify: `app/src/main/java/com/voxpen/app/data/repository/SttRepository.kt`
- Test: `app/src/test/java/com/voxpen/app/data/remote/SttApiFactoryTest.kt`
- Test: `app/src/test/java/com/voxpen/app/data/repository/SttRepositoryTest.kt`

- [ ] **Step 1: Add failing factory tests**

Replace `SttApiFactoryTest` expectations with provider-aware tests:

```kotlin
@Test
fun `should create cached API for OpenAI provider`() {
    val api1 = factory.create(SttProvider.OpenAI)
    val api2 = factory.create(SttProvider.OpenAI)

    assertThat(api1).isSameInstanceAs(api2)
}

@Test
fun `should create cached API for Groq provider`() {
    val api1 = factory.create(SttProvider.Groq)
    val api2 = factory.create(SttProvider.Groq)

    assertThat(api1).isSameInstanceAs(api2)
}

@Test
fun `should create custom API with normalized slash`() {
    val api1 = factory.createForCustom("https://my-whisper.example.com")
    val api2 = factory.createForCustom("https://my-whisper.example.com/")

    assertThat(api1).isSameInstanceAs(api2)
}
```

- [ ] **Step 2: Add failing repository routing test**

Add to `SttRepositoryTest`:

```kotlin
@Test
fun `should call OpenAI STT API when provider is OpenAI`() =
    runTest {
        val openAiApi: SttApi = mockk()
        every { sttApiFactory.create(SttProvider.OpenAI) } returns openAiApi
        coEvery {
            openAiApi.transcribe(any(), any(), any(), any(), any(), any())
        } returns WhisperResponse(text = "openai result")

        val result = repository.transcribe(
            wavBytes = ByteArray(10),
            language = SttLanguage.English,
            apiKey = "sk-test",
            provider = SttProvider.OpenAI,
            model = "whisper-1",
        )

        assertThat(result.getOrNull()?.text).isEqualTo("openai result")
        coVerify(exactly = 0) { groqApi.transcribe(any(), any(), any(), any(), any(), any()) }
    }
```

- [ ] **Step 3: Run tests and verify failure**

```bash
./gradlew testDebugUnitTest --tests com.voxpen.app.data.remote.SttApiFactoryTest --tests com.voxpen.app.data.repository.SttRepositoryTest
```

Expected: FAIL because `SttApi`, `SttApiFactory.create(provider)`, and `provider` argument do not exist.

- [ ] **Step 4: Create `SttApi.kt`**

```kotlin
package com.voxpen.app.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface SttApi {
    @Multipart
    @POST("v1/audio/transcriptions")
    suspend fun transcribe(
        @Header("Authorization") authorization: String,
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody,
        @Part("response_format") responseFormat: RequestBody,
        @Part("language") language: RequestBody? = null,
        @Part("prompt") prompt: RequestBody? = null,
    ): WhisperResponse
}
```

- [ ] **Step 5: Update `SttApiFactory`**

```kotlin
@Singleton
class SttApiFactory
    @Inject
    constructor(
        private val client: OkHttpClient,
        private val json: Json,
    ) {
        private val cache = ConcurrentHashMap<String, SttApi>()

        fun create(provider: SttProvider): SttApi {
            val baseUrl = provider.baseUrl
                ?: throw IllegalArgumentException("Custom STT requires base URL")
            return createForBaseUrl(provider.key, baseUrl)
        }

        fun createForCustom(baseUrl: String): SttApi =
            createForBaseUrl("custom_stt", baseUrl)

        private fun createForBaseUrl(cachePrefix: String, rawBaseUrl: String): SttApi {
            val normalized = rawBaseUrl.trim().let { if (it.endsWith("/")) it else "$it/" }
            return cache.getOrPut("$cachePrefix:$normalized") {
                Retrofit.Builder()
                    .baseUrl(normalized)
                    .client(client)
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()
                    .create(SttApi::class.java)
            }
        }
    }
```

- [ ] **Step 6: Update `SttRepository` signature and routing**

Change constructor to accept `SttApiFactory`; keep `GroqApi` only for backward-compatible tests until callers are updated:

```kotlin
suspend fun transcribe(
    wavBytes: ByteArray,
    language: SttLanguage,
    apiKey: String,
    provider: SttProvider = SttProvider.Groq,
    model: String = provider.defaultModelId,
    vocabularyHint: String? = null,
    customSttBaseUrl: String? = null,
): Result<TranscriptionResult> {
    if (apiKey.isBlank()) {
        return Result.failure(IllegalStateException("API key not configured for ${provider.key}"))
    }

    val api = if (provider == SttProvider.Custom) {
        val baseUrl = customSttBaseUrl?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("Custom STT base URL not configured"))
        sttApiFactory.createForCustom(baseUrl)
    } else {
        sttApiFactory.create(provider)
    }

    return transcribeOnce(api, wavBytes, language, apiKey, provider, model, vocabularyHint)
}
```

Move the existing multipart body construction into a private `transcribeOnce(...)`.

- [ ] **Step 7: Update tests to mock `SttApi` instead of `GroqApi` for new cases**

Keep old tests passing by routing Groq through `sttApiFactory.create(SttProvider.Groq)`. In `setUp()`:

```kotlin
private val groqSttApi: SttApi = mockk()

every { sttApiFactory.create(SttProvider.Groq) } returns groqSttApi
```

Replace `groqApi.transcribe(...)` expectations in `SttRepositoryTest` with `groqSttApi.transcribe(...)`.

- [ ] **Step 8: Run tests**

```bash
./gradlew testDebugUnitTest --tests com.voxpen.app.data.remote.SttApiFactoryTest --tests com.voxpen.app.data.repository.SttRepositoryTest
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/voxpen/app/data/remote/SttApi.kt app/src/main/java/com/voxpen/app/data/remote/SttApiFactory.kt app/src/main/java/com/voxpen/app/data/repository/SttRepository.kt app/src/test/java/com/voxpen/app/data/remote/SttApiFactoryTest.kt app/src/test/java/com/voxpen/app/data/repository/SttRepositoryTest.kt
git commit -m "feat: route STT providers through neutral API"
```

---

### Task 3: Normalize Provider Errors And Retry Transient STT Failures

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/data/repository/SttRepository.kt`
- Test: `app/src/test/java/com/voxpen/app/data/repository/SttRepositoryTest.kt`

- [ ] **Step 1: Add failing response-format and error tests**

Add to `SttRepositoryTest`:

```kotlin
@Test
fun `should use json response format for OpenAI gpt-4o transcription models`() =
    runTest {
        val openAiApi: SttApi = mockk()
        val formatSlot = slot<RequestBody>()
        every { sttApiFactory.create(SttProvider.OpenAI) } returns openAiApi
        coEvery {
            openAiApi.transcribe(any(), any(), any(), capture(formatSlot), any(), any())
        } returns WhisperResponse(text = "ok")

        repository.transcribe(
            wavBytes = ByteArray(10),
            language = SttLanguage.Auto,
            apiKey = "sk",
            provider = SttProvider.OpenAI,
            model = "gpt-4o-mini-transcribe",
        )

        val buffer = okio.Buffer()
        formatSlot.captured.writeTo(buffer)
        assertThat(buffer.readUtf8()).isEqualTo("json")
    }

@Test
fun `should retry timeout once`() =
    runTest {
        val api: SttApi = mockk()
        every { sttApiFactory.create(SttProvider.Groq) } returns api
        coEvery { api.transcribe(any(), any(), any(), any(), any(), any()) }
            .throws(java.net.SocketTimeoutException("timeout"))
            .andThen(WhisperResponse(text = "after retry"))

        val result = repository.transcribe(ByteArray(10), SttLanguage.Auto, "key")

        assertThat(result.getOrNull()?.text).isEqualTo("after retry")
        coVerify(exactly = 2) { api.transcribe(any(), any(), any(), any(), any(), any()) }
    }

@Test
fun `should return readable provider error`() =
    runTest {
        val api: SttApi = mockk()
        every { sttApiFactory.create(SttProvider.OpenAI) } returns api
        coEvery { api.transcribe(any(), any(), any(), any(), any(), any()) } throws
            IOException("connection refused")

        val result = repository.transcribe(
            wavBytes = ByteArray(10),
            language = SttLanguage.Auto,
            apiKey = "sk",
            provider = SttProvider.OpenAI,
        )

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("openai")
        assertThat(result.exceptionOrNull()?.message).contains("connection refused")
    }
```

- [ ] **Step 2: Run tests and verify failure**

```bash
./gradlew testDebugUnitTest --tests com.voxpen.app.data.repository.SttRepositoryTest
```

Expected: FAIL because response format is always `verbose_json` and retry/error normalization are missing.

- [ ] **Step 3: Add response format and retry helpers**

Add to `SttRepository`:

```kotlin
private fun responseFormat(
    provider: SttProvider,
    model: String,
): String =
    if (provider == SttProvider.OpenAI && model in setOf("gpt-4o-transcribe", "gpt-4o-mini-transcribe")) {
        "json"
    } else {
        "verbose_json"
    }

private suspend fun transcribeWithRetry(
    api: SttApi,
    wavBytes: ByteArray,
    language: SttLanguage,
    apiKey: String,
    provider: SttProvider,
    model: String,
    vocabularyHint: String?,
): Result<TranscriptionResult> {
    var firstFailure: Throwable? = null
    repeat(2) { attempt ->
        val result = runCatching {
            transcribeOnce(api, wavBytes, language, apiKey, provider, model, vocabularyHint)
        }.fold(
            onSuccess = { it },
            onFailure = { Result.failure<TranscriptionResult>(it) },
        )
        if (result.isSuccess) return result
        val error = result.exceptionOrNull()!!
        if (attempt == 0 && isRetryable(error)) {
            firstFailure = error
        } else {
            return Result.failure(IllegalStateException(formatProviderError(provider, error), error))
        }
    }
    return Result.failure(IllegalStateException(formatProviderError(provider, firstFailure!!), firstFailure))
}

private fun isRetryable(error: Throwable): Boolean =
    error is java.net.SocketTimeoutException || error is IOException

private fun formatProviderError(
    provider: SttProvider,
    error: Throwable,
): String = "${provider.key}: ${error.message ?: error::class.java.simpleName}".take(600)
```

In `transcribeOnce`, use:

```kotlin
val format = responseFormat(provider, model).toRequestBody(TEXT_PLAIN)
```

Then have public `transcribe(...)` call `transcribeWithRetry(...)`.

- [ ] **Step 4: Add HTTP status handling**

Catch Retrofit `HttpException` in `transcribeWithRetry` and use status-aware retry:

```kotlin
private fun isRetryable(error: Throwable): Boolean {
    if (error is retrofit2.HttpException) {
        return error.code() == 408 || error.code() == 429 || error.code() in 500..599
    }
    return error is java.net.SocketTimeoutException || error is IOException
}

private fun formatProviderError(
    provider: SttProvider,
    error: Throwable,
): String {
    if (error is retrofit2.HttpException) {
        val body = error.response()?.errorBody()?.string().orEmpty().take(500)
        return "${provider.key} HTTP ${error.code()}: $body".take(650)
    }
    return "${provider.key}: ${error.message ?: error::class.java.simpleName}".take(650)
}
```

- [ ] **Step 5: Run tests**

```bash
./gradlew testDebugUnitTest --tests com.voxpen.app.data.repository.SttRepositoryTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/voxpen/app/data/repository/SttRepository.kt app/src/test/java/com/voxpen/app/data/repository/SttRepositoryTest.kt
git commit -m "fix: normalize and retry STT failures"
```

---

### Task 4: Chunk Live PCM Before STT

**Files:**
- Create: `app/src/main/java/com/voxpen/app/util/LiveAudioChunker.kt`
- Test: `app/src/test/java/com/voxpen/app/util/LiveAudioChunkerTest.kt`
- Modify: `app/src/main/java/com/voxpen/app/domain/usecase/TranscribeAudioUseCase.kt`
- Test: `app/src/test/java/com/voxpen/app/domain/usecase/TranscribeAudioUseCaseTest.kt`

- [ ] **Step 1: Write failing chunker tests**

```kotlin
package com.voxpen.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LiveAudioChunkerTest {
    @Test
    fun `should return one chunk for short audio`() {
        val pcm = ByteArray(32_000) { 1 }
        assertThat(LiveAudioChunker.split(pcm)).containsExactly(pcm)
    }

    @Test
    fun `should split at sixty seconds for 16khz mono 16-bit pcm`() {
        val oneMinuteBytes = 16_000 * 2 * 60
        val pcm = ByteArray(oneMinuteBytes + 42) { 1 }

        val chunks = LiveAudioChunker.split(pcm)

        assertThat(chunks).hasSize(2)
        assertThat(chunks[0].size).isEqualTo(oneMinuteBytes)
        assertThat(chunks[1].size).isEqualTo(42)
    }

    @Test
    fun `should align chunk size to two byte samples`() {
        val chunks = LiveAudioChunker.split(ByteArray(1_920_001) { 1 })

        assertThat(chunks[0].size % 2).isEqualTo(0)
    }
}
```

- [ ] **Step 2: Implement `LiveAudioChunker`**

```kotlin
package com.voxpen.app.util

object LiveAudioChunker {
    const val SAMPLE_RATE = 16_000
    const val BYTES_PER_SAMPLE = 2
    const val CHUNK_SECONDS = 60
    const val CHUNK_BYTES = SAMPLE_RATE * BYTES_PER_SAMPLE * CHUNK_SECONDS

    fun split(pcmData: ByteArray): List<ByteArray> {
        if (pcmData.isEmpty()) return emptyList()
        val frameAlignedChunkBytes = CHUNK_BYTES - (CHUNK_BYTES % BYTES_PER_SAMPLE)
        return pcmData.asList()
            .chunked(frameAlignedChunkBytes)
            .map { it.toByteArray() }
    }
}
```

- [ ] **Step 3: Add failing use case test**

Add to `TranscribeAudioUseCaseTest`:

```kotlin
@Test
fun `should transcribe live chunks sequentially and join text`() =
    runTest {
        unmockkObject(AudioEncoder)
        val pcm = ByteArray(LiveAudioChunker.CHUNK_BYTES + 32_000) { 1 }
        val api: SttApi = mockk()
        every { sttApiFactory.create(SttProvider.Groq) } returns api
        coEvery { api.transcribe(any(), any(), any(), any(), any(), any()) }
            .returnsMany(WhisperResponse(text = "first"), WhisperResponse(text = "second"))

        val result = useCase(pcm, SttLanguage.English, "key")

        assertThat(result.getOrNull()).isEqualTo("first second")
        coVerify(exactly = 2) { api.transcribe(any(), any(), any(), any(), any(), any()) }
    }
```

- [ ] **Step 4: Update `TranscribeAudioUseCase`**

```kotlin
val texts = mutableListOf<String>()
for (chunk in LiveAudioChunker.split(pcmData)) {
    val wavBytes = AudioEncoder.pcmToWav(chunk, sampleRate, channels, bitsPerSample)
    val result = sttRepository.transcribe(
        wavBytes = wavBytes,
        language = language,
        apiKey = apiKey,
        provider = provider,
        model = model,
        vocabularyHint = vocabularyHint,
        customSttBaseUrl = customSttBaseUrl,
    )
    result.fold(
        onSuccess = { texts.add(it.text.trim()) },
        onFailure = { return Result.failure(it) },
    )
}
return Result.success(texts.filter { it.isNotBlank() }.joinToString(" "))
```

Add `provider: SttProvider = SttProvider.Groq` to the use case signature.

- [ ] **Step 5: Run tests**

```bash
./gradlew testDebugUnitTest --tests com.voxpen.app.util.LiveAudioChunkerTest --tests com.voxpen.app.domain.usecase.TranscribeAudioUseCaseTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/voxpen/app/util/LiveAudioChunker.kt app/src/main/java/com/voxpen/app/domain/usecase/TranscribeAudioUseCase.kt app/src/test/java/com/voxpen/app/util/LiveAudioChunkerTest.kt app/src/test/java/com/voxpen/app/domain/usecase/TranscribeAudioUseCaseTest.kt
git commit -m "fix: chunk live recordings before STT"
```

---

### Task 5: Show Visible Errors For Short And Silent Recordings

**Files:**
- Create: `app/src/main/java/com/voxpen/app/util/RecordingValidator.kt`
- Test: `app/src/test/java/com/voxpen/app/util/RecordingValidatorTest.kt`
- Modify: `app/src/main/java/com/voxpen/app/ime/RecordingController.kt`
- Test: `app/src/test/java/com/voxpen/app/ime/RecordingControllerTest.kt`

- [ ] **Step 1: Add validator tests**

```kotlin
package com.voxpen.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class RecordingValidatorTest {
    @Test
    fun `should reject too short recording`() {
        val result = RecordingValidator.validate(ByteArray(1000))
        assertThat(result).isEqualTo(RecordingValidation.TooShort)
    }

    @Test
    fun `should reject silent recording`() {
        val result = RecordingValidator.validate(ByteArray(32_000))
        assertThat(result).isEqualTo(RecordingValidation.Silent)
    }

    @Test
    fun `should accept voiced recording`() {
        val pcm = AudioSilenceDetectorTest.generateSineWave(durationMs = 500)
        val result = RecordingValidator.validate(pcm)
        assertThat(result).isEqualTo(RecordingValidation.Valid)
    }
}
```

- [ ] **Step 2: Implement validator**

```kotlin
package com.voxpen.app.util

sealed interface RecordingValidation {
    data object Valid : RecordingValidation
    data object TooShort : RecordingValidation
    data object Silent : RecordingValidation
}

object RecordingValidator {
    private const val MIN_BYTES = (16_000 * 2 * 0.3).toInt()

    fun validate(pcmData: ByteArray): RecordingValidation =
        when {
            pcmData.size < MIN_BYTES -> RecordingValidation.TooShort
            AudioSilenceDetector.isSilent(pcmData) -> RecordingValidation.Silent
            else -> RecordingValidation.Valid
        }
}
```

- [ ] **Step 3: Update existing controller tests**

Change the two tests at the bottom of `RecordingControllerTest`:

```kotlin
assertThat(awaitItem()).isEqualTo(ImeUiState.Error("Recording is too quiet. Please try again."))
```

and:

```kotlin
assertThat(awaitItem()).isEqualTo(ImeUiState.Error("Recording is too short. Please try again."))
```

- [ ] **Step 4: Update `RecordingController`**

Replace the `AudioSilenceDetector.isSilent(pcmData)` branch with:

```kotlin
when (RecordingValidator.validate(pcmData)) {
    RecordingValidation.Valid -> Unit
    RecordingValidation.TooShort -> {
        _uiState.value = ImeUiState.Error("Recording is too short. Please try again.")
        return
    }
    RecordingValidation.Silent -> {
        _uiState.value = ImeUiState.Error("Recording is too quiet. Please try again.")
        return
    }
}
```

- [ ] **Step 5: Run tests**

```bash
./gradlew testDebugUnitTest --tests com.voxpen.app.util.RecordingValidatorTest --tests com.voxpen.app.ime.RecordingControllerTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/voxpen/app/util/RecordingValidator.kt app/src/main/java/com/voxpen/app/ime/RecordingController.kt app/src/test/java/com/voxpen/app/util/RecordingValidatorTest.kt app/src/test/java/com/voxpen/app/ime/RecordingControllerTest.kt
git commit -m "fix: show visible recording validation errors"
```

---

### Task 6: Persist Failed Live Recordings In History

**Files:**
- Create: `app/src/main/java/com/voxpen/app/data/local/RecordingStore.kt`
- Test: `app/src/test/java/com/voxpen/app/data/local/RecordingStoreTest.kt`
- Modify: `app/src/main/java/com/voxpen/app/data/local/TranscriptionEntity.kt`
- Test: `app/src/test/java/com/voxpen/app/data/local/TranscriptionEntityTest.kt`
- Modify: `app/src/main/java/com/voxpen/app/data/local/AppDatabase.kt`
- Modify: `app/src/main/java/com/voxpen/app/di/AppModule.kt`
- Modify: `app/src/main/java/com/voxpen/app/data/repository/TranscriptionRepository.kt`
- Modify: `app/src/main/java/com/voxpen/app/ime/RecordingController.kt`
- Modify: `app/src/main/java/com/voxpen/app/ime/VoxPenIMEEntryPoint.kt`

- [ ] **Step 1: Add entity tests**

Add to `TranscriptionEntityTest`:

```kotlin
@Test
fun `failed entity displays error message`() {
    val entity = TranscriptionEntity(
        fileName = "live-1.wav",
        originalText = "",
        language = "auto",
        createdAt = 1000L,
        status = "failed",
        errorMessage = "openai HTTP 400: bad file",
        audioPath = "/private/recordings/live-1.wav",
        provider = "openai",
    )

    assertThat(entity.displayText).isEqualTo("openai HTTP 400: bad file")
    assertThat(entity.isFailed).isTrue()
}
```

- [ ] **Step 2: Update `TranscriptionEntity`**

```kotlin
val status: String = STATUS_COMPLETED,
val errorMessage: String? = null,
val audioPath: String? = null,
val provider: String? = null,
) {
    val isFailed: Boolean
        get() = status == STATUS_FAILED

    val displayText: String
        get() = refinedText ?: originalText.takeIf { it.isNotBlank() } ?: errorMessage.orEmpty()

    companion object {
        const val STATUS_COMPLETED = "completed"
        const val STATUS_FAILED = "failed"
    }
}
```

- [ ] **Step 3: Add Room migration**

In `AppDatabase`, bump version to 4 and add:

```kotlin
val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE transcriptions ADD COLUMN status TEXT NOT NULL DEFAULT 'completed'")
            db.execSQL("ALTER TABLE transcriptions ADD COLUMN errorMessage TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE transcriptions ADD COLUMN audioPath TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE transcriptions ADD COLUMN provider TEXT DEFAULT NULL")
        }
    }
```

In `AppModule`, add the migration:

```kotlin
.addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
```

- [ ] **Step 4: Create `RecordingStore`**

```kotlin
package com.voxpen.app.data.local

import android.content.Context
import com.voxpen.app.util.AudioEncoder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun saveLiveRecording(
            entryId: Long,
            pcmData: ByteArray,
        ): String {
            val dir = File(context.filesDir, "recordings").apply { mkdirs() }
            val file = File(dir, "live-$entryId.wav")
            file.writeBytes(AudioEncoder.pcmToWav(pcmData, 16_000, 1, 16))
            return file.absolutePath
        }

        fun read(path: String): ByteArray = File(path).readBytes()

        fun delete(path: String) {
            File(path).delete()
        }
    }
```

- [ ] **Step 5: Add repository helpers**

```kotlin
suspend fun insertFailedLive(
    language: SttLanguage,
    provider: SttProvider,
    errorMessage: String,
): Long =
    dao.insert(
        TranscriptionEntity(
            fileName = "Live recording",
            originalText = "",
            language = PreferencesManager.languageToKey(language),
            createdAt = System.currentTimeMillis(),
            status = TranscriptionEntity.STATUS_FAILED,
            errorMessage = errorMessage,
            provider = provider.key,
        ),
    )

suspend fun updateAudioPath(
    id: Long,
    audioPath: String,
) {
    val entity = dao.getById(id) ?: return
    dao.update(entity.copy(audioPath = audioPath))
}

suspend fun markCompletedAfterRetry(
    id: Long,
    originalText: String,
    refinedText: String?,
    provider: SttProvider,
) {
    val entity = dao.getById(id) ?: return
    dao.update(
        entity.copy(
            originalText = originalText,
            refinedText = refinedText,
            status = TranscriptionEntity.STATUS_COMPLETED,
            errorMessage = null,
            provider = provider.key,
        ),
    )
}
```

- [ ] **Step 6: Save failed live recordings in `RecordingController`**

Add constructor dependencies:

```kotlin
private val transcriptionRepository: TranscriptionRepository,
private val recordingStore: RecordingStore,
```

In STT failure branch:

```kotlin
onFailure = { error ->
    val message = error.message ?: "Transcription failed"
    val id = transcriptionRepository.insertFailedLive(language, sttProvider, message)
    val audioPath = recordingStore.saveLiveRecording(id, pcmData)
    transcriptionRepository.updateAudioPath(id, audioPath)
    _uiState.value = ImeUiState.Error(message)
}
```

- [ ] **Step 7: Update IME entry point and controller construction**

Add to `VoxPenIMEEntryPoint`:

```kotlin
fun transcriptionRepository(): TranscriptionRepository

fun recordingStore(): RecordingStore
```

Pass those dependencies where `RecordingController` is constructed in `VoxPenIME.kt`.

- [ ] **Step 8: Run tests**

```bash
./gradlew testDebugUnitTest --tests com.voxpen.app.data.local.TranscriptionEntityTest --tests com.voxpen.app.ime.RecordingControllerTest
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/voxpen/app/data/local/RecordingStore.kt app/src/main/java/com/voxpen/app/data/local/TranscriptionEntity.kt app/src/main/java/com/voxpen/app/data/local/AppDatabase.kt app/src/main/java/com/voxpen/app/di/AppModule.kt app/src/main/java/com/voxpen/app/data/repository/TranscriptionRepository.kt app/src/main/java/com/voxpen/app/ime/RecordingController.kt app/src/main/java/com/voxpen/app/ime/VoxPenIMEEntryPoint.kt app/src/test/java/com/voxpen/app/data/local/TranscriptionEntityTest.kt
git commit -m "feat: persist failed live recordings"
```

---

### Task 7: Retry Failed History Rows

**Files:**
- Create: `app/src/main/java/com/voxpen/app/domain/usecase/RetryTranscriptionUseCase.kt`
- Test: `app/src/test/java/com/voxpen/app/domain/usecase/RetryTranscriptionUseCaseTest.kt`
- Modify: `app/src/main/java/com/voxpen/app/ui/transcription/TranscriptionEntryPoint.kt`
- Modify: `app/src/main/java/com/voxpen/app/ui/transcription/TranscriptionViewModel.kt`
- Test: `app/src/test/java/com/voxpen/app/ui/transcription/TranscriptionViewModelTest.kt`
- Modify: `app/src/main/java/com/voxpen/app/ui/transcription/TranscriptionScreen.kt`

- [ ] **Step 1: Add retry use case test**

```kotlin
@Test
fun `should retry failed row and update same entity`() =
    runTest {
        val failed = TranscriptionEntity(
            id = 7,
            fileName = "Live recording",
            originalText = "",
            language = "en",
            createdAt = 1000L,
            status = TranscriptionEntity.STATUS_FAILED,
            errorMessage = "openai HTTP 500",
            audioPath = "/recordings/live-7.wav",
            provider = "openai",
        )
        coEvery { transcriptionRepository.getById(7) } returns failed
        every { recordingStore.read("/recordings/live-7.wav") } returns ByteArray(100)
        coEvery {
            sttRepository.transcribe(any(), SttLanguage.English, "sk", SttProvider.OpenAI, "whisper-1", any(), any())
        } returns Result.success(TranscriptionResult("retried text"))
        coEvery { transcriptionRepository.markCompletedAfterRetry(7, "retried text", null, SttProvider.OpenAI) } returns Unit

        val result = useCase(id = 7, provider = SttProvider.OpenAI, apiKey = "sk", model = "whisper-1")

        assertThat(result.getOrNull()?.originalText).isEqualTo("retried text")
    }
```

- [ ] **Step 2: Implement `RetryTranscriptionUseCase`**

```kotlin
class RetryTranscriptionUseCase
    @Inject
    constructor(
        private val transcriptionRepository: TranscriptionRepository,
        private val recordingStore: RecordingStore,
        private val sttRepository: SttRepository,
    ) {
        suspend operator fun invoke(
            id: Long,
            provider: SttProvider,
            apiKey: String,
            model: String,
            customSttBaseUrl: String? = null,
        ): Result<TranscriptionEntity> {
            val entity = transcriptionRepository.getById(id)
                ?: return Result.failure(IllegalArgumentException("Transcription not found"))
            val audioPath = entity.audioPath
                ?: return Result.failure(IllegalStateException("Saved recording not found"))
            val language = PreferencesManager.languageFromKey(entity.language)
            val wavBytes = recordingStore.read(audioPath)
            val result = sttRepository.transcribe(
                wavBytes = wavBytes,
                language = language,
                apiKey = apiKey,
                provider = provider,
                model = model,
                customSttBaseUrl = customSttBaseUrl,
            )
            return result.map { tr ->
                transcriptionRepository.markCompletedAfterRetry(id, tr.text, null, provider)
                entity.copy(
                    originalText = tr.text,
                    status = TranscriptionEntity.STATUS_COMPLETED,
                    errorMessage = null,
                    provider = provider.key,
                )
            }
        }
    }
```

- [ ] **Step 3: Expose retry use case to history screen**

Add to `TranscriptionEntryPoint`:

```kotlin
fun retryTranscriptionUseCase(): RetryTranscriptionUseCase
```

- [ ] **Step 4: Add view model retry state**

Add to `TranscriptionUiState`:

```kotlin
val retryingId: Long? = null,
```

Add to `TranscriptionViewModel`:

```kotlin
fun setRetrying(id: Long?) {
    _uiState.update { it.copy(retryingId = id) }
}
```

- [ ] **Step 5: Add retry UI callback**

In `TranscriptionDetailScreen`, add parameters:

```kotlin
retrying: Boolean,
onRetry: (Long) -> Unit,
```

Show retry for failed rows:

```kotlin
if (entity.isFailed) {
    Text(entity.errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
    Button(
        enabled = !retrying && entity.audioPath != null,
        onClick = { onRetry(entity.id) },
    ) {
        Text(stringResource(R.string.transcription_retry))
    }
}
```

- [ ] **Step 6: Wire retry from Compose**

Inside `TranscriptionScreenContent`, load provider/key/model from entry point just like file transcription:

```kotlin
val retryUseCase = entryPoint.retryTranscriptionUseCase()
val provider = prefsManager.sttProviderFlow.first()
val apiKey = apiKeyManager.getSttApiKey(provider).orEmpty()
val model = prefsManager.sttModelFlow.first()
val baseUrl = prefsManager.customSttBaseUrlFlow.first().ifBlank { null }
val result = retryUseCase(id, provider, apiKey, model, baseUrl)
```

On success, call `viewModel.selectTranscription(result.getOrThrow())`; on failure, call `viewModel.onTranscriptionError(...)`; always clear `retryingId`.

- [ ] **Step 7: Run tests**

```bash
./gradlew testDebugUnitTest --tests com.voxpen.app.domain.usecase.RetryTranscriptionUseCaseTest --tests com.voxpen.app.ui.transcription.TranscriptionViewModelTest
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/voxpen/app/domain/usecase/RetryTranscriptionUseCase.kt app/src/main/java/com/voxpen/app/ui/transcription/TranscriptionEntryPoint.kt app/src/main/java/com/voxpen/app/ui/transcription/TranscriptionUiState.kt app/src/main/java/com/voxpen/app/ui/transcription/TranscriptionViewModel.kt app/src/main/java/com/voxpen/app/ui/transcription/TranscriptionScreen.kt app/src/test/java/com/voxpen/app/domain/usecase/RetryTranscriptionUseCaseTest.kt app/src/test/java/com/voxpen/app/ui/transcription/TranscriptionViewModelTest.kt
git commit -m "feat: retry failed transcriptions from history"
```

---

### Task 8: Settings UI For STT Provider And OpenAI Models

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/ui/settings/SettingsUiState.kt`
- Modify: `app/src/main/java/com/voxpen/app/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/voxpen/app/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`
- Test: `app/src/test/java/com/voxpen/app/ui/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Add settings state**

```kotlin
val sttProvider: SttProvider = PreferencesManager.DEFAULT_STT_PROVIDER,
val sttProviderApiKeys: Map<String, Boolean> = emptyMap(),
```

- [ ] **Step 2: Collect provider flow and save provider**

In `SettingsViewModel.init`:

```kotlin
viewModelScope.launch {
    preferencesManager.sttProviderFlow.collect { provider ->
        _uiState.update { it.copy(sttProvider = provider) }
    }
}
```

Add:

```kotlin
fun setSttProvider(provider: SttProvider) {
    viewModelScope.launch {
        preferencesManager.setSttProvider(provider)
        preferencesManager.setSttModel(provider.defaultModelId)
    }
}

fun saveSttProviderApiKey(provider: SttProvider, key: String) {
    apiKeyManager.setSttApiKey(provider, key)
    _uiState.update {
        it.copy(sttProviderApiKeys = it.sttProviderApiKeys + (provider.key to key.isNotBlank()))
    }
}
```

- [ ] **Step 3: Add provider key helpers**

In `ApiKeyManager`:

```kotlin
fun getSttApiKey(provider: SttProvider): String? =
    when (provider) {
        SttProvider.Groq -> getGroqApiKey()
        SttProvider.OpenAI -> encryptedPrefs.getString("${KEY_PREFIX}openai", null)
        SttProvider.Custom -> getGroqApiKey()
    }

fun setSttApiKey(provider: SttProvider, key: String?) {
    when (provider) {
        SttProvider.Groq -> setGroqApiKey(key)
        SttProvider.OpenAI -> setApiKey(LlmProvider.OpenAI, key)
        SttProvider.Custom -> setGroqApiKey(key)
    }
}
```

- [ ] **Step 4: Replace STT model section**

In `SettingsScreen.kt`, change `SttModelSection` to show provider chips:

```kotlin
FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    SttProvider.all.forEach { provider ->
        FilterChip(
            selected = state.sttProvider == provider,
            onClick = { viewModel.setSttProvider(provider) },
            label = { Text(provider.displayName) },
        )
    }
}

val models = state.sttProvider.models
models.forEach { model ->
    RadioRow(model.label, state.sttModel == model.id) {
        viewModel.setSttModel(model.id)
    }
}
```

For custom provider, keep the custom STT base URL field and allow free-form model through the existing `sttModel` setter if a model text field is added.

- [ ] **Step 5: Add strings**

`values/strings.xml`:

```xml
<string name="settings_stt_provider_section">Speech-to-text provider</string>
<string name="transcription_retry">Retry</string>
<string name="transcription_failed">Failed</string>
```

`values-zh-rTW/strings.xml`:

```xml
<string name="settings_stt_provider_section">語音辨識服務</string>
<string name="transcription_retry">重送</string>
<string name="transcription_failed">失敗</string>
```

- [ ] **Step 6: Run tests**

```bash
./gradlew testDebugUnitTest --tests com.voxpen.app.ui.settings.SettingsViewModelTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/voxpen/app/ui/settings/SettingsUiState.kt app/src/main/java/com/voxpen/app/ui/settings/SettingsViewModel.kt app/src/main/java/com/voxpen/app/ui/settings/SettingsScreen.kt app/src/main/java/com/voxpen/app/data/local/ApiKeyManager.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh-rTW/strings.xml app/src/test/java/com/voxpen/app/ui/settings/SettingsViewModelTest.kt
git commit -m "feat: add STT provider settings"
```

---

### Task 9: Documentation And Full Verification

**Files:**
- Modify: `CLAUDE.md`
- Modify: `README.md`
- Modify: `README.zh-TW.md`
- Modify: `ROADMAP.md`

- [ ] **Step 1: Update docs**

Document these shipped Android behaviors:

- STT providers: Groq, OpenAI Audio API, custom OpenAI-compatible endpoint.
- OpenAI STT models: `whisper-1`, `gpt-4o-transcribe`, `gpt-4o-mini-transcribe`.
- OpenAI `gpt-4o*` uses `json`; Whisper/Groq uses `verbose_json`.
- Live recordings are chunked into 60-second PCM chunks.
- Transient STT failures retry once.
- Failed live recordings are stored privately and can be retried from history.
- Short/silent recordings show readable IME errors instead of disappearing.

- [ ] **Step 2: Run unit tests**

```bash
./gradlew testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run lint or assemble**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Manual QA**

Use a debug build on a device or emulator:

1. Select Groq STT, record a normal phrase, confirm result appears.
2. Select OpenAI STT with `whisper-1`, record a normal phrase, confirm result appears.
3. Select OpenAI STT with `gpt-4o-mini-transcribe`, record a normal phrase, confirm result appears.
4. Clear the OpenAI key, record with OpenAI selected, confirm the IME shows a complete provider/key error.
5. Record silence, confirm a readable quiet-recording error appears.
6. Record a very short tap, confirm a readable short-recording error appears.
7. Force STT failure with a bad key, open history, confirm a failed row with full error and Retry.
8. Fix the key, tap Retry, confirm the same row becomes completed.

- [ ] **Step 5: Commit docs**

```bash
git add CLAUDE.md README.md README.zh-TW.md ROADMAP.md
git commit -m "docs: document Android transcription retry support"
```

## Self-Review

- Spec coverage: OpenAI first-class STT, response format, retry, chunking, short/silent errors, failed-row persistence, manual retry, settings, and docs each have a task.
- Placeholder scan: This plan contains concrete files, test names, commands, and code snippets for each behavior.
- Type consistency: `SttProvider`, `SttApi`, `RecordingStore`, `RecordingValidator`, and `RetryTranscriptionUseCase` names are used consistently across tasks.
