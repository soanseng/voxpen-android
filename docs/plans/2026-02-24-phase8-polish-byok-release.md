# Phase 8: UX Polish, Multi-Provider BYOK & Release Readiness

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Close all remaining gaps from Phases 1-5 and 7 that are not explicitly deferred to v1.1. Three pillars: (A) UX polish quick wins, (B) multi-provider BYOK expansion, (C) release readiness for Play Store submission.

**Architecture:** The multi-provider design introduces an `OpenAiCompatibleApi` Retrofit interface that serves both Groq and OpenAI (they share the same Whisper + ChatCompletion API shape). Custom endpoints reuse this same interface with a user-supplied base URL. `ApiKeyManager` expands from single-key to per-provider key storage. `SttRepository` and `LlmRepository` accept a provider parameter and route to the correct Retrofit instance. A `@Named` qualifier pattern in Hilt distinguishes the Groq vs OpenAI vs Custom Retrofit instances.

**Tech Stack:** Same as existing (Kotlin, Hilt, Retrofit, Compose, DataStore, Room). No new library dependencies for 8A-8D. UMP SDK (already declared) activated in 8E.

---

## Batch 8A: UX Polish — Recording Feedback & Candidate Bar

### Task 1: Mic Button Pulse Animation

**Files:**
- Create: `app/src/main/res/anim/pulse_recording.xml`
- Modify: `app/src/main/java/com/voxink/app/ime/VoxInkIME.kt`

Create an XML `ObjectAnimator` set that loops alpha (1.0→0.6→1.0) and scaleX/scaleY (1.0→1.1→1.0) with 800ms duration and infinite repeat. In `updateUi()`, start the animation when state is `Recording`, cancel and reset when state transitions away.

```xml
<!-- pulse_recording.xml -->
<set xmlns:android="http://schemas.android.com/apk/res/android"
    android:ordering="together">
    <objectAnimator android:propertyName="alpha"
        android:valueFrom="1.0" android:valueTo="0.6"
        android:duration="800" android:repeatMode="reverse"
        android:repeatCount="infinite" />
    <objectAnimator android:propertyName="scaleX"
        android:valueFrom="1.0" android:valueTo="1.1"
        android:duration="800" android:repeatMode="reverse"
        android:repeatCount="infinite" />
    <objectAnimator android:propertyName="scaleY"
        android:valueFrom="1.0" android:valueTo="1.1"
        android:duration="800" android:repeatMode="reverse"
        android:repeatCount="infinite" />
</set>
```

Implementation in `VoxInkIME.updateUi()`:
```kotlin
private var pulseAnimator: AnimatorSet? = null

// In Recording state:
pulseAnimator?.cancel()
pulseAnimator = AnimatorInflater.loadAnimator(this, R.animator.pulse_recording) as AnimatorSet
pulseAnimator?.setTarget(micButton)
pulseAnimator?.start()

// In all other states:
pulseAnimator?.cancel()
micButton?.alpha = 1f
micButton?.scaleX = 1f
micButton?.scaleY = 1f
```

**TDD:** Unit test `RecordingController` state transitions already exist. Animation is visual — verify manually on device. Add a simple test that `updateUi(Recording)` doesn't crash.

### Task 2: Haptic Feedback on Record Start/Stop

**Files:**
- Modify: `app/src/main/java/com/voxink/app/ime/VoxInkIME.kt`

Use `View.performHapticFeedback()` which doesn't require `VIBRATE` permission:

```kotlin
// In startRecording():
micButton?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

// In stopRecording():
micButton?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)  // API 30+, fallback to KEYBOARD_TAP
```

Fallback for API < 30: use `HapticFeedbackConstants.KEYBOARD_TAP`.

**TDD:** Haptic is hardware-dependent. No unit test needed — verify on device.

### Task 3: Audio Cue on Record Start/Stop

**Files:**
- Modify: `app/src/main/java/com/voxink/app/ime/VoxInkIME.kt`

Use `ToneGenerator` for a minimal, non-intrusive audio cue:

```kotlin
private var toneGenerator: ToneGenerator? = null

override fun onCreate() {
    super.onCreate()
    toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 30) // low volume
}

// Record start: short beep
toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 80)

// Record stop: double beep
toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 80)
```

Release in `onDestroy()`. Volume is intentionally low (30/100) so it doesn't interfere with speech being recorded.

**TDD:** No unit test — audio is hardware-dependent. Verify on device.

### Task 4: API Key Validation on Save

**Files:**
- Modify: `app/src/main/java/com/voxink/app/data/local/ApiKeyManager.kt`
- Modify: `app/src/main/java/com/voxink/app/data/remote/GroqApi.kt`
- Modify: `app/src/main/java/com/voxink/app/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/voxink/app/ui/settings/SettingsUiState.kt`
- Create: `app/src/test/java/com/voxink/app/ui/settings/SettingsViewModelApiKeyValidationTest.kt`

Add a lightweight validation endpoint to `GroqApi`:
```kotlin
// GroqApi.kt — add:
@GET("openai/v1/models")
suspend fun listModels(@Header("Authorization") authorization: String): ModelsResponse
```

`ModelsResponse` is a minimal data class (`data class ModelsResponse(val data: List<ModelInfo>)`, `data class ModelInfo(val id: String)`).

In `SettingsViewModel.saveApiKey()`:
1. Show a "Validating..." state (`SettingsUiState.apiKeyValidation: ValidationState`)
2. Call `groqApi.listModels("Bearer $key")` in a coroutine
3. On 200 OK → save key, show success toast/state
4. On 401/403 → show "Invalid API key" error, don't save
5. On network error → save key anyway with warning "Saved but couldn't verify (offline?)"

`ValidationState`: `sealed interface { Idle, Validating, Valid, Invalid(message), SavedOffline }`

**TDD:**
- Test: valid key → calls API → saves key → state = Valid
- Test: invalid key → API returns 401 → doesn't save → state = Invalid
- Test: network error → saves key → state = SavedOffline

### Task 5: Candidate Bar — Scrollable Long Text

**Files:**
- Modify: `app/src/main/res/layout/keyboard_view.xml`

Replace `ellipsize="end" maxLines="2"` with `HorizontalScrollView` wrapping for the three text views (`candidate_text`, `candidate_original`, `candidate_refined`):

```xml
<HorizontalScrollView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:scrollbars="none"
    android:fadingEdge="horizontal"
    android:fadingEdgeLength="16dp">
    <TextView
        android:id="@+id/candidate_original"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:maxLines="2"
        android:padding="6dp"
        android:textColor="#888888"
        android:textSize="14sp"
        android:background="?android:attr/selectableItemBackground" />
</HorizontalScrollView>
```

Keep `maxLines="2"` to limit vertical height, but allow horizontal scrolling. Use `fadingEdge="horizontal"` to hint that content extends beyond view.

**TDD:** Layout change — verify on device with a long transcription string. No unit test.

### Task 6: Add Bilingual Strings for 8A

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`

New strings:
- `api_key_validating` / "Validating API key..." / "正在驗證 API 金鑰..."
- `api_key_valid` / "API key is valid" / "API 金鑰有效"
- `api_key_invalid` / "Invalid API key" / "API 金鑰無效"
- `api_key_saved_offline` / "Saved (could not verify — offline?)" / "已儲存（無法驗證，可能離線）"

### Task 7: Commit Batch 8A

Commit message: `feat: Phase 8A — recording animation, haptic/audio cues, API key validation, scrollable candidate bar`

---

## Batch 8B: Multi-Provider Data Layer

### Task 8: Create SttProvider and LlmProvider Models

**Files:**
- Create: `app/src/main/java/com/voxink/app/data/model/SttProvider.kt`
- Create: `app/src/main/java/com/voxink/app/data/model/LlmProvider.kt`
- Create: `app/src/test/java/com/voxink/app/data/model/SttProviderTest.kt`

```kotlin
// SttProvider.kt
enum class SttProvider(
    val displayName: String,
    val baseUrl: String?,       // null for Custom (user-defined)
    val defaultModel: String,
) {
    GROQ("Groq", "https://api.groq.com/", "whisper-large-v3-turbo"),
    OPENAI("OpenAI", "https://api.openai.com/", "whisper-1"),
    CUSTOM("Custom", null, "whisper-large-v3-turbo"),
}

// LlmProvider.kt
enum class LlmProvider(
    val displayName: String,
    val baseUrl: String?,
    val defaultModel: String,
) {
    GROQ("Groq", "https://api.groq.com/", "llama-3.3-70b-versatile"),
    OPENAI("OpenAI", "https://api.openai.com/", "gpt-4o-mini"),
    CUSTOM("Custom", null, ""),
}
```

**Design note:** Anthropic uses a different API shape (Messages API, not ChatCompletion). Defer Anthropic to a later phase — Groq, OpenAI, and Custom endpoints all share OpenAI-compatible API format, keeping the implementation simple.

**TDD:**
- Test: each provider has non-blank displayName
- Test: GROQ and OPENAI have non-null baseUrl
- Test: CUSTOM has null baseUrl

### Task 9: Create OpenAiCompatibleApi Interface

**Files:**
- Create: `app/src/main/java/com/voxink/app/data/remote/OpenAiCompatibleApi.kt`
- Keep: `app/src/main/java/com/voxink/app/data/remote/GroqApi.kt` (unchanged, still used for Groq-specific Retrofit instance)

```kotlin
// OpenAiCompatibleApi.kt — identical method signatures to GroqApi
// This interface is used for OpenAI and Custom endpoints
interface OpenAiCompatibleApi {
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

    @POST("v1/chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest,
    ): ChatCompletionResponse

    @GET("v1/models")
    suspend fun listModels(@Header("Authorization") authorization: String): ModelsResponse
}
```

**Key difference:** Groq's base URL includes `openai/` prefix in the path (`https://api.groq.com/openai/v1/...`), while OpenAI uses `https://api.openai.com/v1/...` directly. The existing `GroqApi` interface already has `openai/v1/...` paths hardcoded, so it stays as-is. `OpenAiCompatibleApi` uses `v1/...` paths (no `openai/` prefix), matching OpenAI and most self-hosted Whisper servers.

### Task 10: Expand ApiKeyManager for Multi-Provider

**Files:**
- Modify: `app/src/main/java/com/voxink/app/data/local/ApiKeyManager.kt`
- Modify: `app/src/test/java/com/voxink/app/data/local/ApiKeyManagerTest.kt` (create if not exists)

Add per-provider key storage:
```kotlin
@Singleton
class ApiKeyManager @Inject constructor(
    private val encryptedPrefs: SharedPreferences,
) {
    // Existing (keep for backward compat during migration)
    fun getGroqApiKey(): String? = encryptedPrefs.getString(KEY_GROQ, null)
    fun setGroqApiKey(key: String?) { ... }
    fun isGroqKeyConfigured(): Boolean = ...

    // New: per-provider
    fun getOpenAiApiKey(): String? = encryptedPrefs.getString(KEY_OPENAI, null)
    fun setOpenAiApiKey(key: String?) { ... }
    fun isOpenAiKeyConfigured(): Boolean = !getOpenAiApiKey().isNullOrBlank()

    fun getCustomApiKey(): String? = encryptedPrefs.getString(KEY_CUSTOM, null)
    fun setCustomApiKey(key: String?) { ... }

    fun getCustomEndpointUrl(): String? = encryptedPrefs.getString(KEY_CUSTOM_ENDPOINT, null)
    fun setCustomEndpointUrl(url: String?) { ... }

    // Convenience: get key for any provider
    fun getApiKey(provider: SttProvider): String? = when (provider) {
        SttProvider.GROQ -> getGroqApiKey()
        SttProvider.OPENAI -> getOpenAiApiKey()
        SttProvider.CUSTOM -> getCustomApiKey()
    }

    companion object {
        private const val KEY_GROQ = "groq_api_key"
        private const val KEY_OPENAI = "openai_api_key"
        private const val KEY_CUSTOM = "custom_api_key"
        private const val KEY_CUSTOM_ENDPOINT = "custom_endpoint_url"
    }
}
```

**TDD:**
- Test: set/get OpenAI key round-trips correctly
- Test: set/get custom key + endpoint round-trips correctly
- Test: `getApiKey(SttProvider.GROQ)` returns Groq key
- Test: `getApiKey(SttProvider.OPENAI)` returns OpenAI key

### Task 11: Expand PreferencesManager for Provider Selection

**Files:**
- Modify: `app/src/main/java/com/voxink/app/data/local/PreferencesManager.kt`

Add DataStore preferences for selected STT and LLM providers:
```kotlin
val sttProviderFlow: Flow<SttProvider>     // default: GROQ
val llmProviderFlow: Flow<LlmProvider>     // default: GROQ
val sttModelFlow: Flow<String>             // default: provider's defaultModel
val llmModelFlow: Flow<String>             // default: provider's defaultModel

suspend fun setSttProvider(provider: SttProvider)
suspend fun setLlmProvider(provider: LlmProvider)
suspend fun setSttModel(model: String)
suspend fun setLlmModel(model: String)
```

### Task 12: Expand NetworkModule with Named Retrofit Instances

**Files:**
- Modify: `app/src/main/java/com/voxink/app/di/NetworkModule.kt`

Provide three Retrofit instances via `@Named` qualifiers:
```kotlin
@Provides @Singleton @Named("groq")
fun provideGroqApi(client: OkHttpClient, json: Json): GroqApi { ... }  // existing

@Provides @Singleton @Named("openai")
fun provideOpenAiApi(client: OkHttpClient, json: Json): OpenAiCompatibleApi {
    return Retrofit.Builder()
        .baseUrl("https://api.openai.com/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(OpenAiCompatibleApi::class.java)
}
```

For Custom endpoints: create at runtime (not via Hilt), since the base URL is user-configurable. Add a factory method:
```kotlin
@Provides @Singleton
fun provideCustomApiFactory(client: OkHttpClient, json: Json): CustomApiFactory {
    return CustomApiFactory(client, json)
}

class CustomApiFactory(private val client: OkHttpClient, private val json: Json) {
    fun create(baseUrl: String): OpenAiCompatibleApi {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenAiCompatibleApi::class.java)
    }
}
```

### Task 13: Refactor SttRepository for Multi-Provider

**Files:**
- Modify: `app/src/main/java/com/voxink/app/data/repository/SttRepository.kt`
- Modify: `app/src/test/java/com/voxink/app/data/repository/SttRepositoryTest.kt`

```kotlin
@Singleton
class SttRepository @Inject constructor(
    @Named("groq") private val groqApi: GroqApi,
    @Named("openai") private val openAiApi: OpenAiCompatibleApi,
    private val customApiFactory: CustomApiFactory,
    private val apiKeyManager: ApiKeyManager,
) {
    suspend fun transcribe(
        wavBytes: ByteArray,
        language: SttLanguage,
        provider: SttProvider,
        model: String = provider.defaultModel,
    ): Result<String> {
        val apiKey = apiKeyManager.getApiKey(provider)
            ?: return Result.failure(IllegalStateException("API key not configured for ${provider.displayName}"))

        return try {
            val response = when (provider) {
                SttProvider.GROQ -> groqApi.transcribe(/* ... same as today ... */)
                SttProvider.OPENAI -> openAiApi.transcribe(/* ... */)
                SttProvider.CUSTOM -> {
                    val endpoint = apiKeyManager.getCustomEndpointUrl()
                        ?: return Result.failure(IllegalStateException("Custom endpoint not configured"))
                    customApiFactory.create(endpoint).transcribe(/* ... */)
                }
            }
            Result.success(response.text)
        } catch (e: IOException) { Result.failure(e) }
    }
}
```

**TDD:**
- Test: transcribe with GROQ routes to groqApi
- Test: transcribe with OPENAI routes to openAiApi
- Test: transcribe with CUSTOM uses customApiFactory
- Test: missing API key returns failure
- Test: missing custom endpoint returns failure

### Task 14: Refactor LlmRepository for Multi-Provider

**Files:**
- Modify: `app/src/main/java/com/voxink/app/data/repository/LlmRepository.kt`
- Modify: `app/src/test/java/com/voxink/app/data/repository/LlmRepositoryTest.kt`

Same pattern as `SttRepository`: accept `LlmProvider` + `model` parameters, route to correct API instance.

**TDD:**
- Test: refine with GROQ routes to groqApi.chatCompletion
- Test: refine with OPENAI routes to openAiApi.chatCompletion
- Test: refine with CUSTOM uses customApiFactory
- Test: missing API key returns failure

### Task 15: Update Use Cases and RecordingController

**Files:**
- Modify: `app/src/main/java/com/voxink/app/domain/usecase/TranscribeAudioUseCase.kt`
- Modify: `app/src/main/java/com/voxink/app/domain/usecase/RefineTextUseCase.kt`
- Modify: `app/src/main/java/com/voxink/app/ime/RecordingController.kt`
- Modify corresponding test files

Use cases now read provider + model from `PreferencesManager` and pass to repositories. `RecordingController` no longer needs to know which provider is active — that's encapsulated in the use cases.

### Task 16: Commit Batch 8B

Commit message: `feat: Phase 8B — multi-provider data layer (Groq, OpenAI, Custom endpoint)`

---

## Batch 8C: Multi-Provider Settings UI

### Task 17: STT Provider Selector in Settings

**Files:**
- Modify: `app/src/main/java/com/voxink/app/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/voxink/app/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/voxink/app/ui/settings/SettingsUiState.kt`

Add a "Speech-to-Text Provider" section with:
- Dropdown: Groq / OpenAI / Custom
- API key input field (shown for the selected provider)
- Custom endpoint URL field (shown only when Custom is selected)
- Model text field (pre-filled with provider default, editable)

`SettingsUiState` gains:
```kotlin
val sttProvider: SttProvider = SttProvider.GROQ
val llmProvider: LlmProvider = LlmProvider.GROQ
val sttModel: String = SttProvider.GROQ.defaultModel
val llmModel: String = LlmProvider.GROQ.defaultModel
val openAiKeyDisplay: String = ""
val customKeyDisplay: String = ""
val customEndpointUrl: String = ""
```

### Task 18: LLM Provider Selector in Settings

**Files:** Same as Task 17 (continuation)

Add a "Text Refinement Provider" section with:
- Dropdown: Groq / OpenAI / Custom
- Shares API keys with STT if same provider selected (e.g., if both use OpenAI, one key field suffices)
- Model text field

### Task 19: API Key Validation Per Provider

**Files:** Same as Task 17 (continuation)

Extend the validation logic from Task 4 to work with any provider:
- Groq: `GET https://api.groq.com/openai/v1/models`
- OpenAI: `GET https://api.openai.com/v1/models`
- Custom: `GET {customEndpoint}/v1/models`

All use the same `ModelsResponse` shape (OpenAI-compatible).

### Task 20: Update Onboarding for Multi-Provider

**Files:**
- Modify: `app/src/main/java/com/voxink/app/ui/onboarding/OnboardingScreen.kt`
- Modify: `app/src/main/java/com/voxink/app/ui/onboarding/OnboardingViewModel.kt`

The API key step now shows a simple provider selector (Groq recommended, OpenAI as alternative). Help text updates dynamically:
- Groq: "Get a free key at groq.com"
- OpenAI: "Get a key at platform.openai.com"
- Custom: "Enter your server URL"

### Task 21: Add Bilingual Strings for 8C

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`

New strings for provider names, selector labels, model field hints, custom endpoint placeholder, etc.

### Task 22: Commit Batch 8C

Commit message: `feat: Phase 8C — multi-provider settings UI (STT + LLM provider selectors)`

---

## Batch 8D: Per-Language Custom Refinement Prompts

### Task 23: Custom Prompt Storage

**Files:**
- Modify: `app/src/main/java/com/voxink/app/data/local/PreferencesManager.kt`
- Modify: `app/src/main/java/com/voxink/app/data/model/RefinementPrompt.kt`
- Create: `app/src/test/java/com/voxink/app/data/model/RefinementPromptCustomTest.kt`

Add DataStore keys for per-language custom prompts:
```kotlin
// PreferencesManager
val customPromptZhFlow: Flow<String?>  // null = use default
val customPromptEnFlow: Flow<String?>
val customPromptJaFlow: Flow<String?>
val customPromptMixedFlow: Flow<String?>

suspend fun setCustomPrompt(language: SttLanguage, prompt: String?)
```

`RefinementPrompt.forLanguage()` becomes:
```kotlin
fun forLanguage(language: SttLanguage, customPrompt: String? = null): String {
    return customPrompt ?: defaultForLanguage(language)
}
```

**TDD:**
- Test: custom prompt overrides default
- Test: null custom prompt falls back to default
- Test: set/get round-trip per language

### Task 24: Custom Prompt Settings Screen

**Files:**
- Create: `app/src/main/java/com/voxink/app/ui/settings/CustomPromptScreen.kt`
- Modify: `app/src/main/java/com/voxink/app/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/voxink/app/ui/settings/SettingsViewModel.kt`

Navigate from Settings → "Custom Refinement Prompts" → per-language tabs:
- Each tab shows a `TextField` pre-filled with the current prompt (custom or default)
- "Reset to Default" button per language
- Save automatically on text change (debounced)

Gate behind Pro status: Free users see the screen but prompts are read-only with "Upgrade to Pro" overlay.

### Task 25: Wire Custom Prompts into Refinement Pipeline

**Files:**
- Modify: `app/src/main/java/com/voxink/app/domain/usecase/RefineTextUseCase.kt`
- Modify: `app/src/main/java/com/voxink/app/ime/RecordingController.kt`

`RefineTextUseCase` reads custom prompt from `PreferencesManager` and passes to `LlmRepository.refine()`.

### Task 26: Add Bilingual Strings for 8D

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`

### Task 27: Commit Batch 8D

Commit message: `feat: Phase 8D — per-language custom refinement prompts`

---

## Batch 8E: Release Readiness — Code Changes

### Task 28: AdMob Build Variant IDs

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/voxink/app/ads/AdManager.kt`
- Modify: `app/src/main/AndroidManifest.xml`

Move ad unit IDs to `BuildConfig` fields:
```kotlin
// build.gradle.kts
android {
    buildTypes {
        debug {
            buildConfigField("String", "ADMOB_APP_ID", "\"ca-app-pub-3940256099942544~3347511713\"")
            buildConfigField("String", "BANNER_AD_ID", "\"ca-app-pub-3940256099942544/6300978111\"")
            buildConfigField("String", "INTERSTITIAL_AD_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
            buildConfigField("String", "REWARDED_AD_ID", "\"ca-app-pub-3940256099942544/5224354917\"")
        }
        release {
            // TODO: Replace with real production IDs before release
            buildConfigField("String", "ADMOB_APP_ID", "\"ca-app-pub-XXXXXXXX~YYYYYYYYYY\"")
            buildConfigField("String", "BANNER_AD_ID", "\"ca-app-pub-XXXXXXXX/ZZZZZZZZZZ\"")
            buildConfigField("String", "INTERSTITIAL_AD_ID", "\"ca-app-pub-XXXXXXXX/AAAAAAAAAA\"")
            buildConfigField("String", "REWARDED_AD_ID", "\"ca-app-pub-XXXXXXXX/BBBBBBBBBB\"")
        }
    }
}
```

`AndroidManifest.xml` uses `${ADMOB_APP_ID}` manifest placeholder (injected from `build.gradle.kts` via `manifestPlaceholders`).

`AdManager.kt` reads from `BuildConfig` instead of hardcoded constants.

### Task 29: UMP Consent Flow

**Files:**
- Modify: `app/src/main/java/com/voxink/app/ads/AdManager.kt`
- Create: `app/src/test/java/com/voxink/app/ads/AdManagerTest.kt`

Implement GDPR/UMP consent before loading ads:
```kotlin
fun initialize(activity: Activity) {
    val params = ConsentRequestParameters.Builder()
        .setTagForUnderAgeOfConsent(false)
        .build()

    ConsentInformation.getInstance(context).requestConsentInfoUpdate(
        activity, params,
        {
            if (ConsentInformation.getInstance(context).canRequestAds()) {
                MobileAds.initialize(context)
                initialized = true
            }
        },
        { error -> Timber.w("UMP consent error: ${error.message}") }
    )
}
```

Call from `MainActivity.onCreate()` instead of `VoxInkApplication`.

**TDD:**
- Test: initialize calls consent request
- Test: ads not loaded if consent not granted

### Task 30: ProGuard Verification

**Files:**
- Modify: `app/proguard-rules.pro` (if needed)

Run full release build with minify and verify:
1. `./gradlew assembleRelease` completes without errors
2. No missing class warnings for Billing/AdMob/Retrofit
3. App launches and basic flow works on device

### Task 31: Commit Batch 8E

Commit message: `feat: Phase 8E — AdMob BuildConfig variant IDs, UMP consent flow, ProGuard verification`

---

## Batch 8F: Release Readiness — Assets & Manual Tasks

### Task 32: App Icon

**Files:**
- Create: `app/src/main/res/mipmap-hdpi/ic_launcher.webp`
- Create: `app/src/main/res/mipmap-mdpi/ic_launcher.webp`
- Create: `app/src/main/res/mipmap-xhdpi/ic_launcher.webp`
- Create: `app/src/main/res/mipmap-xxhdpi/ic_launcher.webp`
- Create: `app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp`
- Create: corresponding `ic_launcher_round.webp` variants
- Modify: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`

Design brief: VoxInk icon should convey "voice + ink/writing". Concept: a stylized microphone or sound wave merged with a brush stroke or ink drop. Color: indigo (#6366F1) primary with white foreground. Adaptive icon format with distinct foreground and background layers.

**Note:** This task requires graphic design tooling (Figma, Android Studio Image Asset wizard, or AI image generation). Mark as manual — provide design spec, generate density variants after artwork is created.

### Task 33: Signing Configuration

**Files:**
- Modify: `app/build.gradle.kts`

Add signing config that reads from environment variables or local `keystore.properties` file (git-ignored):

```kotlin
val keystoreProperties = Properties()
val keystoreFile = rootProject.file("keystore.properties")
if (keystoreFile.exists()) keystoreProperties.load(FileInputStream(keystoreFile))

android {
    signingConfigs {
        create("release") {
            storeFile = file(keystoreProperties["storeFile"] as? String ?: "")
            storePassword = keystoreProperties["storePassword"] as? String ?: ""
            keyAlias = keystoreProperties["keyAlias"] as? String ?: ""
            keyPassword = keystoreProperties["keyPassword"] as? String ?: ""
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

Add `keystore.properties` to `.gitignore`.

**Note:** Actual keystore generation is a manual step (`keytool -genkey ...`).

### Task 34: Update plan.md with Phase 8 Status

**Files:**
- Modify: `plan.md`

Mark all completed Phase 1-5 and 7 items. Add Phase 8 section with status.

### Task 35: Commit Batch 8F

Commit message: `feat: Phase 8F — signing config, app icon placeholder, plan.md update`

---

## Items NOT Included (Deferred to v1.1)

These were explicitly deferred in earlier phases and remain deferred:

| Item | Original Phase | Reason for Deferral |
|------|---------------|---------------------|
| Swipe left/right in candidate bar | 2.2 | Nice-to-have gesture, tap works fine |
| Repetition removal (local pre-processing) | 2.3 | LLM handles this adequately via prompt |
| List formatting detection | 2.3 | LLM handles this via prompt |
| Hybrid local pre-clean + LLM | 2.3 | Complexity vs benefit unclear |
| Transcript timestamps (verbose_json) | 3.3 | Requires UI overhaul for timeline view |
| In-app transcript editing | 3.3 | Significant editor UI work |
| Paragraph-by-paragraph refinement | 3.4 | Complex chunking logic for long text |
| Custom mic button animation | 4.1 | Pulse animation (8A) is sufficient for v1 |
| Smooth state transitions | 4.1 | Functional transitions work, polish later |
| Text editing keys | 4.4 | Keyboard layout redesign needed |
| Emoji button | 4.4 | Keyboard layout redesign needed |
| LLM streaming | 4.5 | Major refactor of response handling |
| IME launch time optimization | 4.5 | Measure during beta, optimize if needed |
| Anthropic provider | 8B | Different API shape (Messages API), separate integration effort |

---

## Dependency Graph

```
8A (UX Polish) ─────────────────────────────────→ 8E (Release Code)
                                                     ↓
8B (Multi-Provider Data) → 8C (Multi-Provider UI) → 8E
                              ↓
                           8D (Custom Prompts) ────→ 8E → 8F (Assets & Manual)
```

- 8A and 8B are independent and can be developed in parallel
- 8C depends on 8B (needs provider models and repository refactors)
- 8D depends on 8C (custom prompt UI lives in settings, needs provider context)
- 8E depends on 8A + 8C + 8D (final integration verification)
- 8F depends on 8E (signing config after all code changes are stable)
