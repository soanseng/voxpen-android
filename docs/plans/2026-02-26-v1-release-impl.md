# VoxPen v1 Release — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Complete v1 features (custom STT, file transcription refinement, SRT export with real timestamps, language picker) and audit for release readiness.

**Architecture:** All four features touch existing layers with minimal new classes. Custom STT reuses `ChatCompletionApiFactory` pattern for dynamic Retrofit creation. File refinement wires existing `RefineTextUseCase` into `TranscribeFileUseCase`. SRT timestamps come from already-parsed `WhisperSegment` data. Language picker reuses `SttLanguage` model.

**Tech Stack:** Kotlin, Jetpack Compose, Retrofit, Room, Hilt, JUnit 5, MockK, Truth

---

## Task 1: Custom STT Endpoint — Data Layer

**Files:**
- Create: `app/src/main/java/com/voxpen/app/data/remote/SttApiFactory.kt`
- Modify: `app/src/main/java/com/voxpen/app/data/repository/SttRepository.kt`
- Modify: `app/src/main/java/com/voxpen/app/data/local/ApiKeyManager.kt`
- Test: `app/src/test/java/com/voxpen/app/data/remote/SttApiFactoryTest.kt`
- Test: `app/src/test/java/com/voxpen/app/data/repository/SttRepositoryTest.kt`

**Step 1: Add custom STT base URL storage to ApiKeyManager**

In `ApiKeyManager.kt`, add after the `KEY_CUSTOM_BASE_URL` constant (line 64):

```kotlin
private const val KEY_CUSTOM_STT_BASE_URL = "custom_stt_base_url"
```

Add methods after `setCustomBaseUrl()` (after line 59):

```kotlin
// --- Custom STT provider base URL ---
fun getCustomSttBaseUrl(): String? =
    encryptedPrefs.getString(KEY_CUSTOM_STT_BASE_URL, null)

fun setCustomSttBaseUrl(url: String?) {
    encryptedPrefs.edit().apply {
        if (url != null) putString(KEY_CUSTOM_STT_BASE_URL, url) else remove(KEY_CUSTOM_STT_BASE_URL)
        apply()
    }
}
```

**Step 2: Write SttApiFactory**

Create `SttApiFactory.kt` — mirrors `ChatCompletionApiFactory` pattern:

```kotlin
package com.voxpen.app.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SttApiFactory
    @Inject
    constructor(
        private val client: OkHttpClient,
        private val json: Json,
    ) {
        private val cache = ConcurrentHashMap<String, GroqApi>()

        fun createForCustom(baseUrl: String): GroqApi {
            return cache.getOrPut("custom_stt:$baseUrl") {
                Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()
                    .create(GroqApi::class.java)
            }
        }
    }
```

Note: Reuses `GroqApi` interface since custom STT servers use OpenAI-compatible endpoints (same multipart format).

**Step 3: Write SttApiFactory test**

```kotlin
package com.voxpen.app.data.remote

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test

class SttApiFactoryTest {
    private val factory = SttApiFactory(OkHttpClient(), Json { ignoreUnknownKeys = true })

    @Test
    fun `should create API instance for custom base URL`() {
        val api = factory.createForCustom("https://my-whisper.example.com/")
        assertThat(api).isNotNull()
    }

    @Test
    fun `should cache instances for same base URL`() {
        val api1 = factory.createForCustom("https://my-whisper.example.com/")
        val api2 = factory.createForCustom("https://my-whisper.example.com/")
        assertThat(api1).isSameInstanceAs(api2)
    }

    @Test
    fun `should create separate instances for different URLs`() {
        val api1 = factory.createForCustom("https://server-a.example.com/")
        val api2 = factory.createForCustom("https://server-b.example.com/")
        assertThat(api1).isNotSameInstanceAs(api2)
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.voxpen.app.data.remote.SttApiFactoryTest"`

**Step 5: Modify SttRepository to support custom endpoint**

Update `SttRepository.kt` constructor and method:

```kotlin
@Singleton
class SttRepository
    @Inject
    constructor(
        private val groqApi: GroqApi,
        private val sttApiFactory: SttApiFactory,
    ) {
        suspend fun transcribe(
            wavBytes: ByteArray,
            language: SttLanguage,
            apiKey: String,
            model: String = WHISPER_MODEL,
            vocabularyHint: String? = null,
            customSttBaseUrl: String? = null,
        ): Result<String> {
            if (apiKey.isBlank()) {
                return Result.failure(IllegalStateException("API key not configured"))
            }

            val api = if (!customSttBaseUrl.isNullOrBlank()) {
                sttApiFactory.createForCustom(customSttBaseUrl)
            } else {
                groqApi
            }

            return try {
                val filePart =
                    MultipartBody.Part.createFormData(
                        "file",
                        "recording.wav",
                        wavBytes.toRequestBody("audio/wav".toMediaType()),
                    )
                val modelBody = model.toRequestBody(TEXT_PLAIN)
                val format = RESPONSE_FORMAT.toRequestBody(TEXT_PLAIN)
                val langBody = language.code?.toRequestBody(TEXT_PLAIN)
                val promptBody = (vocabularyHint ?: language.prompt).toRequestBody(TEXT_PLAIN)

                val response =
                    api.transcribe(
                        authorization = "Bearer $apiKey",
                        file = filePart,
                        model = modelBody,
                        responseFormat = format,
                        language = langBody,
                        prompt = promptBody,
                    )

                Result.success(response.text)
            } catch (e: IOException) {
                Result.failure(e)
            }
        }

        companion object {
            private const val WHISPER_MODEL = "whisper-large-v3-turbo"
            private const val RESPONSE_FORMAT = "verbose_json"
            private val TEXT_PLAIN = "text/plain".toMediaType()
        }
    }
```

**Step 6: Update SttRepositoryTest for new constructor**

The existing test at line 23 creates `SttRepository(groqApi)` — update `setUp()`:

```kotlin
private lateinit var sttApiFactory: SttApiFactory

@BeforeEach
fun setUp() {
    sttApiFactory = mockk()
    repository = SttRepository(groqApi, sttApiFactory)
}
```

Add test for custom STT routing:

```kotlin
@Test
fun `should use custom API when customSttBaseUrl is provided`() =
    runTest {
        val customApi: GroqApi = mockk()
        every { sttApiFactory.createForCustom("https://my-whisper.example.com/") } returns customApi
        coEvery { customApi.transcribe(any(), any(), any(), any(), any(), any()) } returns
            WhisperResponse(text = "custom result")

        val result = repository.transcribe(
            wavBytes = ByteArray(10),
            language = SttLanguage.Auto,
            apiKey = "key",
            customSttBaseUrl = "https://my-whisper.example.com/",
        )

        assertThat(result.getOrNull()).isEqualTo("custom result")
        coVerify(exactly = 0) { groqApi.transcribe(any(), any(), any(), any(), any(), any()) }
    }

@Test
fun `should use Groq API when customSttBaseUrl is null`() =
    runTest {
        coEvery { groqApi.transcribe(any(), any(), any(), any(), any(), any()) } returns
            WhisperResponse(text = "groq result")

        val result = repository.transcribe(
            wavBytes = ByteArray(10),
            language = SttLanguage.Auto,
            apiKey = "key",
            customSttBaseUrl = null,
        )

        assertThat(result.getOrNull()).isEqualTo("groq result")
    }
```

Add missing imports: `import io.mockk.every`, `import io.mockk.mockk` (already present), and the `SttApiFactory` import.

**Step 7: Run all SttRepository tests**

Run: `./gradlew testDebugUnitTest --tests "com.voxpen.app.data.repository.SttRepositoryTest"`

**Step 8: Commit**

```bash
git add app/src/main/java/com/voxpen/app/data/remote/SttApiFactory.kt \
       app/src/main/java/com/voxpen/app/data/repository/SttRepository.kt \
       app/src/main/java/com/voxpen/app/data/local/ApiKeyManager.kt \
       app/src/test/java/com/voxpen/app/data/remote/SttApiFactoryTest.kt \
       app/src/test/java/com/voxpen/app/data/repository/SttRepositoryTest.kt
git commit -m "feat: add custom STT endpoint support in data layer"
```

---

## Task 2: Custom STT Endpoint — Settings UI + Wiring

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/data/local/PreferencesManager.kt`
- Modify: `app/src/main/java/com/voxpen/app/ui/settings/SettingsScreen.kt` (add custom STT URL input)
- Modify: `app/src/main/java/com/voxpen/app/ui/settings/SettingsViewModel.kt` (add STT URL state)
- Modify: `app/src/main/java/com/voxpen/app/ime/RecordingController.kt` (pass custom STT URL)
- Modify: `app/src/main/java/com/voxpen/app/domain/usecase/TranscribeAudioUseCase.kt` (pass-through param)
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`

**Step 1: Add custom STT URL preference flow to PreferencesManager**

Add a new preference key and flow, following the existing pattern. In the companion object, add:

```kotlin
private val CUSTOM_STT_BASE_URL_KEY = stringPreferencesKey("custom_stt_base_url_pref")
```

Add flow and setter (following existing pattern):

```kotlin
val customSttBaseUrlFlow: Flow<String> = context.dataStore.data
    .map { it[CUSTOM_STT_BASE_URL_KEY] ?: "" }

suspend fun setCustomSttBaseUrl(url: String) {
    context.dataStore.edit { it[CUSTOM_STT_BASE_URL_KEY] = url }
}
```

**Step 2: Add string resources**

In `values/strings.xml`, add after the STT model section:

```xml
<!-- Custom STT -->
<string name="settings_custom_stt_section">Custom STT Server</string>
<string name="settings_custom_stt_url_hint">Base URL (e.g., https://my-whisper.example.com/)</string>
<string name="settings_custom_stt_description">Use a self-hosted Whisper server with OpenAI-compatible API.</string>
```

In `values-zh-rTW/strings.xml`:

```xml
<!-- Custom STT -->
<string name="settings_custom_stt_section">自訂語音辨識伺服器</string>
<string name="settings_custom_stt_url_hint">伺服器網址（例如 https://my-whisper.example.com/）</string>
<string name="settings_custom_stt_description">使用自架的 Whisper 伺服器（需相容 OpenAI API）。</string>
```

**Step 3: Wire custom STT URL into SettingsViewModel state and SettingsScreen UI**

Add `customSttBaseUrl: String = ""` to the settings UI state. Add a text field in the STT Model section of Settings, below the model radio buttons, for entering the custom STT base URL. Only show when URL is non-empty or user taps an "Add custom STT server" toggle. Persist via `preferencesManager.setCustomSttBaseUrl()`.

**Step 4: Pass custom STT URL through TranscribeAudioUseCase**

Add `customSttBaseUrl: String? = null` parameter to `TranscribeAudioUseCase.invoke()` and pass through to `sttRepository.transcribe()`.

**Step 5: Wire in RecordingController**

In `RecordingController`, collect `preferencesManager.customSttBaseUrlFlow` (like other flows). Read `apiKeyManager.getCustomSttBaseUrl()` at call time and pass to `transcribeUseCase()`.

**Step 6: Run full test suite**

Run: `./gradlew testDebugUnitTest`

**Step 7: Commit**

```bash
git add -A
git commit -m "feat: wire custom STT endpoint through settings and IME"
```

---

## Task 3: File Transcription + LLM Refinement

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/domain/usecase/TranscribeFileUseCase.kt`
- Modify: `app/src/main/java/com/voxpen/app/ui/transcription/TranscriptionScreen.kt`
- Modify: `app/src/main/java/com/voxpen/app/ui/transcription/TranscriptionEntryPoint.kt`
- Test: `app/src/test/java/com/voxpen/app/domain/usecase/TranscribeFileUseCaseTest.kt`

**Step 1: Write failing test for refinement in TranscribeFileUseCase**

Add to `TranscribeFileUseCaseTest.kt`:

```kotlin
private lateinit var refineTextUseCase: RefineTextUseCase

@BeforeEach
fun setUp() {
    groqApi = mockk()
    sttRepository = SttRepository(groqApi, mockk()) // SttApiFactory not used in these tests
    transcriptionRepository = mockk(relaxed = true)
    refineTextUseCase = mockk()
    useCase = TranscribeFileUseCase(sttRepository, transcriptionRepository, refineTextUseCase)
}
```

```kotlin
@Test
fun `should refine transcription when refinement params provided`() =
    runTest {
        val pcmData = ByteArray(100) { (it % 256).toByte() }
        val wavBytes = AudioEncoder.pcmToWav(pcmData, 16000, 1, 16)

        coEvery { groqApi.transcribe(any(), any(), any(), any(), any(), any()) } returns
            WhisperResponse(text = "raw text")
        coEvery { refineTextUseCase(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            Result.success("polished text")
        val entitySlot = slot<TranscriptionEntity>()
        coEvery { transcriptionRepository.insert(capture(entitySlot)) } returns 1L

        val result = useCase(
            fileBytes = wavBytes,
            fileName = "test.wav",
            language = SttLanguage.English,
            apiKey = "key",
            refinementApiKey = "llm-key",
            llmModel = "gpt-4o-mini",
            llmProvider = LlmProvider.OpenAI,
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(entitySlot.captured.refinedText).isEqualTo("polished text")
    }

@Test
fun `should skip refinement when refinementApiKey is null`() =
    runTest {
        val pcmData = ByteArray(100) { (it % 256).toByte() }
        val wavBytes = AudioEncoder.pcmToWav(pcmData, 16000, 1, 16)

        coEvery { groqApi.transcribe(any(), any(), any(), any(), any(), any()) } returns
            WhisperResponse(text = "raw text")
        val entitySlot = slot<TranscriptionEntity>()
        coEvery { transcriptionRepository.insert(capture(entitySlot)) } returns 1L

        val result = useCase(
            fileBytes = wavBytes,
            fileName = "test.wav",
            language = SttLanguage.English,
            apiKey = "key",
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(entitySlot.captured.refinedText).isNull()
        coVerify(exactly = 0) { refineTextUseCase(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }
```

**Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.voxpen.app.domain.usecase.TranscribeFileUseCaseTest"`
Expected: FAIL — `TranscribeFileUseCase` constructor doesn't accept `RefineTextUseCase`.

**Step 3: Implement refinement in TranscribeFileUseCase**

```kotlin
class TranscribeFileUseCase
    @Inject
    constructor(
        private val sttRepository: SttRepository,
        private val transcriptionRepository: TranscriptionRepository,
        private val refineTextUseCase: RefineTextUseCase,
    ) {
        suspend operator fun invoke(
            fileBytes: ByteArray,
            fileName: String,
            language: SttLanguage,
            apiKey: String,
            maxChunkBytes: Int = DEFAULT_MAX_CHUNK_BYTES,
            refinementApiKey: String? = null,
            llmModel: String? = null,
            llmProvider: LlmProvider? = null,
            customLlmBaseUrl: String? = null,
            tone: ToneStyle = ToneStyle.Casual,
            vocabulary: List<String> = emptyList(),
            customPrompt: String? = null,
        ): Result<TranscriptionEntity> {
            val chunks =
                if (AudioChunker.isWav(fileBytes)) {
                    AudioChunker.chunkWav(fileBytes, maxChunkBytes)
                } else {
                    AudioChunker.chunk(fileBytes, maxChunkBytes)
                }

            val transcriptions = mutableListOf<String>()
            for (chunk in chunks) {
                val result = sttRepository.transcribe(chunk, language, apiKey)
                result.fold(
                    onSuccess = { transcriptions.add(it) },
                    onFailure = { return Result.failure(it) },
                )
            }

            val mergedText = transcriptions.joinToString(" ")

            // Refine if LLM parameters provided
            val refinedText = if (!refinementApiKey.isNullOrBlank() && llmProvider != null && llmModel != null) {
                refineTextUseCase(
                    text = mergedText,
                    language = language,
                    apiKey = refinementApiKey,
                    model = llmModel,
                    vocabulary = vocabulary,
                    customPrompt = customPrompt,
                    tone = tone,
                    provider = llmProvider,
                    customBaseUrl = customLlmBaseUrl,
                ).getOrNull()
            } else {
                null
            }

            val languageKey = PreferencesManager.languageToKey(language)
            val entity =
                TranscriptionEntity(
                    fileName = fileName,
                    originalText = mergedText,
                    refinedText = refinedText,
                    language = languageKey,
                    fileSizeBytes = fileBytes.size.toLong(),
                    createdAt = System.currentTimeMillis(),
                )
            val id = transcriptionRepository.insert(entity)
            return Result.success(entity.copy(id = id))
        }

        companion object {
            private const val DEFAULT_MAX_CHUNK_BYTES = 25 * 1024 * 1024
        }
    }
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.voxpen.app.domain.usecase.TranscribeFileUseCaseTest"`

**Step 5: Update TranscriptionEntryPoint to expose refinement dependencies**

```kotlin
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TranscriptionEntryPoint {
    fun transcribeFileUseCase(): TranscribeFileUseCase
    fun apiKeyManager(): ApiKeyManager
    fun preferencesManager(): PreferencesManager
    fun dictionaryRepository(): DictionaryRepository
}
```

**Step 6: Wire refinement params in TranscriptionScreen.kt**

In the `filePicker` callback (around line 102–113), read LLM settings from EntryPoint and pass to `useCase()`:

```kotlin
val useCase = entryPoint.transcribeFileUseCase()
val apiKeyManager = entryPoint.apiKeyManager()
val prefsManager = entryPoint.preferencesManager()
val dictRepo = entryPoint.dictionaryRepository()

val groqKey = apiKeyManager.getGroqApiKey() ?: ""
val llmProvider = prefsManager.llmProviderFlow.first()
val llmApiKey = apiKeyManager.getApiKey(llmProvider) ?: groqKey
val llmModel = if (llmProvider == LlmProvider.Custom) {
    prefsManager.customLlmModelFlow.first().ifBlank { prefsManager.llmModelFlow.first() }
} else {
    prefsManager.llmModelFlow.first()
}
val refinementEnabled = prefsManager.refinementEnabledFlow.first()
val tone = prefsManager.toneStyleFlow.first()
val vocabulary = dictRepo.getWords(500)
val langKey = PreferencesManager.languageToKey(selectedLanguage)
val customPrompt = prefsManager.customPromptFlow(langKey).first()
val customLlmBaseUrl = if (llmProvider == LlmProvider.Custom) {
    apiKeyManager.getCustomBaseUrl()
} else null

val result = useCase(
    fileBytes = fileBytes,
    fileName = fileName,
    language = selectedLanguage,
    apiKey = groqKey,
    refinementApiKey = if (refinementEnabled) llmApiKey else null,
    llmModel = llmModel,
    llmProvider = llmProvider,
    customLlmBaseUrl = customLlmBaseUrl,
    tone = tone,
    vocabulary = vocabulary,
    customPrompt = customPrompt,
)
```

Note: `selectedLanguage` comes from Task 5 (language picker). For now use a local `var selectedLanguage = SttLanguage.Auto` that will be wired to the picker later.

**Step 7: Run full test suite**

Run: `./gradlew testDebugUnitTest`

**Step 8: Commit**

```bash
git add -A
git commit -m "feat: add LLM refinement to file transcription"
```

---

## Task 4: SRT Export — Real Timestamps from Whisper Segments

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/data/repository/SttRepository.kt` (return segments)
- Modify: `app/src/main/java/com/voxpen/app/domain/usecase/TranscribeFileUseCase.kt` (collect segments)
- Modify: `app/src/main/java/com/voxpen/app/data/local/TranscriptionEntity.kt` (add segments JSON field)
- Modify: `app/src/main/java/com/voxpen/app/util/ExportHelper.kt` (use real timestamps)
- Test: `app/src/test/java/com/voxpen/app/util/ExportHelperTest.kt`
- Test: `app/src/test/java/com/voxpen/app/data/repository/SttRepositoryTest.kt`

**Step 1: Create a domain model for transcription result with segments**

Instead of changing `SttRepository` to return `WhisperResponse` directly (leaks API model), create a lightweight result:

```kotlin
// In SttRepository.kt, add inner data class or at file level:
data class TranscriptionResult(
    val text: String,
    val segments: List<TranscriptionSegment> = emptyList(),
)

data class TranscriptionSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)
```

**Step 2: Update SttRepository.transcribe() to return TranscriptionResult**

Change return type from `Result<String>` to `Result<TranscriptionResult>`:

```kotlin
suspend fun transcribe(
    wavBytes: ByteArray,
    language: SttLanguage,
    apiKey: String,
    model: String = WHISPER_MODEL,
    vocabularyHint: String? = null,
    customSttBaseUrl: String? = null,
): Result<TranscriptionResult> {
    // ... existing code ...
    val response = api.transcribe(...)
    val segments = response.segments?.map { seg ->
        TranscriptionSegment(
            startMs = (seg.start * 1000).toLong(),
            endMs = (seg.end * 1000).toLong(),
            text = seg.text,
        )
    } ?: emptyList()
    Result.success(TranscriptionResult(response.text, segments))
}
```

**Step 3: Update all callers of SttRepository.transcribe()**

- `TranscribeAudioUseCase`: change `Result<String>` → `Result<String>` (extract `.text` — IME doesn't need segments)
  ```kotlin
  return sttRepository.transcribe(wavBytes, language, apiKey, model, vocabularyHint, customSttBaseUrl)
      .map { it.text }
  ```
- `TranscribeFileUseCase`: collect segments per chunk, merge them with offset adjustment.

**Step 4: Add segments JSON field to TranscriptionEntity**

```kotlin
@Entity(tableName = "transcriptions")
data class TranscriptionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val originalText: String,
    val refinedText: String? = null,
    val language: String,
    val durationMs: Long? = null,
    val fileSizeBytes: Long? = null,
    val segmentsJson: String? = null,
    val createdAt: Long,
) {
    val displayText: String
        get() = refinedText ?: originalText
}
```

This requires a Room migration (version 2 → 3). Add in `AppDatabase.kt`:

```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transcriptions ADD COLUMN segmentsJson TEXT DEFAULT NULL")
    }
}
```

Update database version to 3 and add migration.

**Step 5: Store segments in TranscribeFileUseCase**

Serialize segment list as JSON string using `kotlinx.serialization`:

```kotlin
@Serializable
data class StoredSegment(val startMs: Long, val endMs: Long, val text: String)
```

In `TranscribeFileUseCase`, collect segments per chunk, adjust offsets for multi-chunk, serialize:

```kotlin
val allSegments = mutableListOf<StoredSegment>()
var offsetMs = 0L
for (chunk in chunks) {
    val result = sttRepository.transcribe(chunk, language, apiKey)
    result.fold(
        onSuccess = { tr ->
            transcriptions.add(tr.text)
            tr.segments.forEach { seg ->
                allSegments.add(StoredSegment(seg.startMs + offsetMs, seg.endMs + offsetMs, seg.text))
            }
            // Update offset from last segment end
            tr.segments.lastOrNull()?.let { offsetMs = it.endMs + offsetMs }
        },
        onFailure = { return Result.failure(it) },
    )
}
val segmentsJson = if (allSegments.isNotEmpty()) {
    Json.encodeToString(allSegments)
} else null
```

Pass `segmentsJson` into entity constructor.

**Step 6: Update ExportHelper.toSrt() to use real segments**

```kotlin
fun toSrt(entity: TranscriptionEntity): String {
    // Try real segments first
    val segments = entity.segmentsJson?.let {
        try {
            Json.decodeFromString<List<StoredSegment>>(it)
        } catch (_: Exception) {
            null
        }
    }

    if (!segments.isNullOrEmpty()) {
        return buildString {
            segments.forEachIndexed { index, seg ->
                appendLine("${index + 1}")
                appendLine("${formatSrtTimestamp(seg.startMs)} --> ${formatSrtTimestamp(seg.endMs)}")
                appendLine(seg.text.trim())
                appendLine()
            }
        }
    }

    // Fallback to estimated timestamps for old entries
    val text = entity.displayText
    val sentences = splitIntoSentences(text)
    return buildString {
        sentences.forEachIndexed { index, sentence ->
            val startMs = index * ESTIMATED_SECONDS_PER_SENTENCE * MS_PER_SECOND
            val endMs = (index + 1) * ESTIMATED_SECONDS_PER_SENTENCE * MS_PER_SECOND
            appendLine("${index + 1}")
            appendLine("${formatSrtTimestamp(startMs)} --> ${formatSrtTimestamp(endMs)}")
            appendLine(sentence.trim())
            appendLine()
        }
    }
}
```

**Step 7: Write tests for real-timestamp SRT**

Add to `ExportHelperTest.kt`:

```kotlin
@Test
fun `should use real segments for SRT when available`() {
    val segments = Json.encodeToString(listOf(
        ExportHelper.StoredSegment(0, 2500, "Hello world."),
        ExportHelper.StoredSegment(2500, 5000, "How are you?"),
    ))
    val entity = TranscriptionEntity(
        fileName = "test.wav",
        originalText = "Hello world. How are you?",
        language = "en",
        segmentsJson = segments,
        createdAt = 1000L,
    )

    val srt = ExportHelper.toSrt(entity)

    assertThat(srt).contains("00:00:00,000 --> 00:00:02,500")
    assertThat(srt).contains("00:00:02,500 --> 00:00:05,000")
    assertThat(srt).contains("Hello world.")
    assertThat(srt).contains("How are you?")
}

@Test
fun `should fall back to estimated timestamps when no segments`() {
    val entity = TranscriptionEntity(
        fileName = "test.wav",
        originalText = "One sentence. Two sentence.",
        language = "en",
        createdAt = 1000L,
    )

    val srt = ExportHelper.toSrt(entity)

    assertThat(srt).contains("00:00:00,000 --> 00:00:05,000")
}
```

**Step 8: Run tests**

Run: `./gradlew testDebugUnitTest --tests "com.voxpen.app.util.ExportHelperTest"`

**Step 9: Update SttRepositoryTest for new return type**

All existing tests check `result.getOrNull()` which returned `String` — now returns `TranscriptionResult`. Update assertions, e.g.:

```kotlin
// Before:
assertThat(result.getOrNull()).isEqualTo("你好世界")
// After:
assertThat(result.getOrNull()?.text).isEqualTo("你好世界")
```

**Step 10: Run full test suite**

Run: `./gradlew testDebugUnitTest`

**Step 11: Commit**

```bash
git add -A
git commit -m "feat: parse Whisper segments for real SRT timestamps"
```

---

## Task 5: SRT Export — UI Button in Detail Screen

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/ui/transcription/TranscriptionScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`

**Step 1: Add string resources**

In `values/strings.xml`:

```xml
<string name="transcription_export_srt">Export SRT</string>
<string name="transcription_srt_exported">SRT file exported</string>
```

In `values-zh-rTW/strings.xml`:

```xml
<string name="transcription_export_srt">匯出 SRT 字幕</string>
<string name="transcription_srt_exported">SRT 檔案已匯出</string>
```

**Step 2: Add "Export SRT" button to TranscriptionDetailScreen**

In `TranscriptionDetailScreen`, add an SRT share icon in the TopAppBar actions (after the copy button, before share):

```kotlin
// Add SubtitlesOutlined or use text button
IconButton(onClick = {
    val srtText = ExportHelper.toSrt(entity)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/x-subrip"
        putExtra(Intent.EXTRA_TEXT, srtText)
        putExtra(Intent.EXTRA_SUBJECT, entity.fileName.substringBeforeLast('.') + ".srt")
    }
    context.startActivity(Intent.createChooser(intent, null))
    Toast.makeText(context, R.string.transcription_srt_exported, Toast.LENGTH_SHORT).show()
}) {
    Icon(Icons.Default.Subtitles, contentDescription = stringResource(R.string.transcription_export_srt))
}
```

Note: `Icons.Default.Subtitles` may need `material-icons-extended`. If not available, use a `TextButton("SRT")` instead.

**Step 3: Test manually on device**

Build and run the app. Create a file transcription, open detail, verify SRT export button appears and produces correct SRT content.

**Step 4: Commit**

```bash
git add -A
git commit -m "feat: add SRT export button to transcription detail screen"
```

---

## Task 6: File Transcription Language Picker

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/ui/transcription/TranscriptionScreen.kt`
- Modify: `app/src/main/java/com/voxpen/app/ui/transcription/TranscriptionUiState.kt`
- Modify: `app/src/main/java/com/voxpen/app/ui/transcription/TranscriptionViewModel.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`

**Step 1: Add language state to TranscriptionUiState**

```kotlin
data class TranscriptionUiState(
    val transcriptions: List<TranscriptionEntity> = emptyList(),
    val selectedTranscription: TranscriptionEntity? = null,
    val isTranscribing: Boolean = false,
    val progress: String = "",
    val error: String? = null,
    val proStatus: ProStatus = ProStatus.Free,
    val canTranscribeFile: Boolean = true,
    val remainingFileTranscriptionSeconds: Int = 0,
    val showUpgradePrompt: Boolean = false,
    val selectedLanguage: SttLanguage = SttLanguage.Auto,
)
```

**Step 2: Add language setter to TranscriptionViewModel**

```kotlin
fun setLanguage(language: SttLanguage) {
    _uiState.update { it.copy(selectedLanguage = language) }
}
```

**Step 3: Add string resources**

In `values/strings.xml`:

```xml
<string name="transcription_language">Language</string>
```

In `values-zh-rTW/strings.xml`:

```xml
<string name="transcription_language">語言</string>
```

**Step 4: Add language selector to TranscriptionScreen**

Above the transcription list (after the usage remaining text, before the transcribing indicator), add a compact language row:

```kotlin
// Language selector row — primary 4 languages as FilterChips
val languages = listOf(SttLanguage.Auto, SttLanguage.Chinese, SttLanguage.English, SttLanguage.Japanese)
Row(
    Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
) {
    languages.forEach { lang ->
        FilterChip(
            selected = state.selectedLanguage == lang,
            onClick = { viewModel.setLanguage(lang) },
            label = { Text("${lang.emoji} ${stringResource(langStringRes(lang))}") },
        )
    }
}
```

Where `langStringRes()` maps `SttLanguage` to the existing `R.string.lang_*` resources.

**Step 5: Wire selectedLanguage into filePicker callback**

Replace the hardcoded `SttLanguage.Auto` at line 111 with `state.selectedLanguage`:

```kotlin
language = state.selectedLanguage,
```

Also wire into the `PreferencesManager.languageToKey()` call for refinement prompt lookup.

**Step 6: Run full test suite**

Run: `./gradlew testDebugUnitTest`

**Step 7: Commit**

```bash
git add -A
git commit -m "feat: add language picker to file transcription screen"
```

---

## Task 7: Debug Audit for Release

**Files:**
- Scan all source files for `BuildConfig.DEBUG`, `Log.d`, hardcoded URLs/keys, test-only code

**Step 1: Audit debug-only code**

Search the codebase for:

```bash
grep -rn "BuildConfig.DEBUG" app/src/main/
grep -rn "TODO\|FIXME\|HACK\|XXX" app/src/main/
grep -rn "Log\.d\|Log\.v\|println" app/src/main/
grep -rn "hardcoded\|test-key\|fake" app/src/main/ --include="*.kt"
```

**Step 2: Verify all debug toggles are properly gated**

Known: `DebugProToggle` in SettingsScreen is gated by `BuildConfig.DEBUG`. Verify no other debug code leaks into release.

**Step 3: Check for exposed API keys or test credentials**

```bash
grep -rn "gsk_\|sk-\|api_key.*=.*\"" app/src/main/ --include="*.kt"
```

**Step 4: Verify ProGuard/R8 rules**

Check `app/proguard-rules.pro` preserves kotlinx.serialization classes and Retrofit interfaces.

**Step 5: Document findings and fix issues**

Create list of issues found, fix each one.

**Step 6: Commit**

```bash
git add -A
git commit -m "chore: audit and clean debug-only code for release"
```

---

## Task 8: Release Signing Setup

**Files:**
- Modify: `app/build.gradle.kts` (add release signing config)

**Step 1: Generate release keystore** (if not exists)

```bash
keytool -genkeypair -v -keystore voxpen-release.jks \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -alias voxpen
```

Store in a secure location outside the repo. Add to `.gitignore`.

**Step 2: Configure signing in build.gradle.kts**

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("VOXPEN_KEYSTORE_PATH") ?: "voxpen-release.jks")
            storePassword = System.getenv("VOXPEN_KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("VOXPEN_KEY_ALIAS") ?: "voxpen"
            keyPassword = System.getenv("VOXPEN_KEY_PASSWORD") ?: ""
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // ... existing release config
        }
    }
}
```

**Step 3: Verify release build**

Run: `./gradlew assembleRelease` (will fail without env vars — that's expected and correct)

**Step 4: Commit**

```bash
git add app/build.gradle.kts .gitignore
git commit -m "chore: add release signing configuration"
```

---

## Summary: Execution Order

| Task | Feature | Depends On |
|------|---------|------------|
| 1 | Custom STT — Data layer | — |
| 2 | Custom STT — UI + wiring | Task 1 |
| 3 | File transcription refinement | — (parallel with Task 1) |
| 4 | SRT real timestamps | — (parallel with Task 1) |
| 5 | SRT export UI button | Task 4 |
| 6 | Language picker | Task 3 (uses selectedLanguage in filePicker) |
| 7 | Debug audit | Tasks 1–6 complete |
| 8 | Release signing | — (parallel with Task 7) |

**Parallelizable:** Tasks 1, 3, 4 can run concurrently. Tasks 7 and 8 can run concurrently.
