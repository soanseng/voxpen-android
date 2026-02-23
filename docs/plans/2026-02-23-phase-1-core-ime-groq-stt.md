# Phase 1: Core IME + Groq STT — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a working voice keyboard: tap mic → speak → see transcription → tap to insert text into any app.

**Architecture:** Audio recording via Android's `AudioRecord` API, PCM→WAV encoding, Groq Whisper STT via Retrofit multipart upload, result displayed in keyboard candidate bar. RecordingController state machine orchestrates the pipeline. Hilt EntryPoint pattern for DI into InputMethodService. All business logic is unit-testable; IME is thin glue.

**Tech Stack (additions to Phase 0):** Retrofit 2.11.0, OkHttp 4.12.0, kotlinx-serialization-json 1.7.3, DataStore Preferences 1.2.0, Security-Crypto 1.1.0-alpha06, MockWebServer (test)

**Prerequisites:** Phase 0 must be complete (buildable project with blank IME, ~21 passing tests, v0.0.1 tag).

**TDD Protocol:** Red-Green-Refactor for all business logic. Android-dependent thin wrappers tested manually.

---

## Part A: Infrastructure (No Tests — Config Only)

---

### Task 1: Add Phase 1 Dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Step 1: Add new versions and libraries to `gradle/libs.versions.toml`**

Add to `[versions]`:
```toml
retrofit = "2.11.0"
okhttp = "4.12.0"
kotlinxSerialization = "1.7.3"
datastore = "1.2.0"
securityCrypto = "1.1.0-alpha06"
```

Add to `[libraries]`:
```toml
# Network
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-converter-kotlinx-serialization = { group = "com.squareup.retrofit2", name = "converter-kotlinx-serialization", version.ref = "retrofit" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }

# Serialization
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }

# Storage
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
security-crypto = { group = "androidx.security", name = "security-crypto", version.ref = "securityCrypto" }
```

Add to `[plugins]`:
```toml
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

**Step 2: Update `app/build.gradle.kts`**

Add the serialization plugin to the plugins block:
```kotlin
alias(libs.plugins.kotlin.serialization)
```

Add to the dependencies block:
```kotlin
// Network
implementation(libs.retrofit)
implementation(libs.retrofit.converter.kotlinx.serialization)
implementation(libs.okhttp)
implementation(libs.okhttp.logging)

// Serialization
implementation(libs.kotlinx.serialization.json)

// Storage
implementation(libs.datastore.preferences)
implementation(libs.security.crypto)

// Testing — MockWebServer
testImplementation(libs.okhttp.mockwebserver)
```

**Step 3: Verify build compiles**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

**Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "chore: add Phase 1 deps (Retrofit, OkHttp, kotlinx-serialization, DataStore, Security-Crypto)"
```

---

### Task 2: Hilt DI — NetworkModule

**Files:**
- Create: `app/src/main/java/com/voxink/app/di/NetworkModule.kt`

**Step 1: Create `NetworkModule.kt`**

```kotlin
package com.voxink.app.di

import com.voxink.app.BuildConfig
import com.voxink.app.data.remote.GroqApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.HEADERS
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                },
            )
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideGroqApi(client: OkHttpClient, json: Json): GroqApi {
        return Retrofit.Builder()
            .baseUrl("https://api.groq.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GroqApi::class.java)
    }
}
```

> **Note:** This won't compile yet — `GroqApi` doesn't exist. It will compile after Task 4. This is expected; we commit the module now and it becomes part of the build in Task 4.

**Step 2: Commit**

```bash
git add app/src/main/java/com/voxink/app/di/NetworkModule.kt
git commit -m "feat: add Hilt NetworkModule for Retrofit + OkHttp"
```

---

## Part B: Domain Models (TDD)

---

### Task 3: TDD — Domain Models

**Purpose:** Define all domain types used across the app. Pure Kotlin — no Android dependencies, fully testable.

**Files:**
- Test: `app/src/test/java/com/voxink/app/data/model/SttLanguageTest.kt`
- Test: `app/src/test/java/com/voxink/app/ime/ImeUiStateTest.kt`
- Create: `app/src/main/java/com/voxink/app/data/model/SttLanguage.kt`
- Create: `app/src/main/java/com/voxink/app/data/model/RecordingMode.kt`
- Create: `app/src/main/java/com/voxink/app/ime/ImeUiState.kt`

**Step 1: RED — Write failing test for SttLanguage**

```kotlin
package com.voxink.app.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SttLanguageTest {

    @Test
    fun `should define auto-detect with no language code`() {
        assertThat(SttLanguage.Auto.code).isNull()
        assertThat(SttLanguage.Auto.prompt).isEqualTo("繁體中文，可能夾雜英文。")
    }

    @Test
    fun `should define Chinese with zh code and Traditional Chinese prompt`() {
        assertThat(SttLanguage.Chinese.code).isEqualTo("zh")
        assertThat(SttLanguage.Chinese.prompt).isEqualTo("繁體中文轉錄。")
    }

    @Test
    fun `should define English with en code`() {
        assertThat(SttLanguage.English.code).isEqualTo("en")
        assertThat(SttLanguage.English.prompt).isNotEmpty()
    }

    @Test
    fun `should define Japanese with ja code`() {
        assertThat(SttLanguage.Japanese.code).isEqualTo("ja")
        assertThat(SttLanguage.Japanese.prompt).isNotEmpty()
    }

    @Test
    fun `should be exhaustive in when expression`() {
        val languages = listOf(
            SttLanguage.Auto,
            SttLanguage.Chinese,
            SttLanguage.English,
            SttLanguage.Japanese,
        )
        languages.forEach { lang ->
            val label = when (lang) {
                SttLanguage.Auto -> "auto"
                SttLanguage.Chinese -> "zh"
                SttLanguage.English -> "en"
                SttLanguage.Japanese -> "ja"
            }
            assertThat(label).isNotEmpty()
        }
    }
}
```

**Step 2: RED — Write failing test for ImeUiState**

```kotlin
package com.voxink.app.ime

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ImeUiStateTest {

    @Test
    fun `should define all IME states`() {
        val states: List<ImeUiState> = listOf(
            ImeUiState.Idle,
            ImeUiState.Recording,
            ImeUiState.Processing,
            ImeUiState.Result("hello"),
            ImeUiState.Error("network error"),
        )
        assertThat(states).hasSize(5)
    }

    @Test
    fun `Result should hold transcription text`() {
        val state = ImeUiState.Result("你好世界")
        assertThat(state.text).isEqualTo("你好世界")
    }

    @Test
    fun `Error should hold error message`() {
        val state = ImeUiState.Error("API key not configured")
        assertThat(state.message).isEqualTo("API key not configured")
    }

    @Test
    fun `should be exhaustive in when expression`() {
        val state: ImeUiState = ImeUiState.Idle
        val label = when (state) {
            ImeUiState.Idle -> "idle"
            ImeUiState.Recording -> "recording"
            ImeUiState.Processing -> "processing"
            is ImeUiState.Result -> "result"
            is ImeUiState.Error -> "error"
        }
        assertThat(label).isEqualTo("idle")
    }
}
```

**Step 3: Run tests to verify RED**

```bash
./gradlew test
```

Expected: FAIL — `Unresolved reference: SttLanguage`, `Unresolved reference: ImeUiState`.

**Step 4: GREEN — Create `SttLanguage.kt`**

```kotlin
package com.voxink.app.data.model

sealed class SttLanguage(
    val code: String?,
    val prompt: String,
) {
    data object Auto : SttLanguage(
        code = null,
        prompt = "繁體中文，可能夾雜英文。",
    )

    data object Chinese : SttLanguage(
        code = "zh",
        prompt = "繁體中文轉錄。",
    )

    data object English : SttLanguage(
        code = "en",
        prompt = "Transcribe the following English speech.",
    )

    data object Japanese : SttLanguage(
        code = "ja",
        prompt = "以下の日本語音声を文字起こししてください。",
    )
}
```

**Step 5: GREEN — Create `RecordingMode.kt`**

```kotlin
package com.voxink.app.data.model

enum class RecordingMode {
    TAP_TO_TOGGLE,
    HOLD_TO_RECORD,
}
```

**Step 6: GREEN — Create `ImeUiState.kt`**

```kotlin
package com.voxink.app.ime

sealed interface ImeUiState {
    data object Idle : ImeUiState
    data object Recording : ImeUiState
    data object Processing : ImeUiState
    data class Result(val text: String) : ImeUiState
    data class Error(val message: String) : ImeUiState
}
```

**Step 7: Run tests to verify GREEN**

```bash
./gradlew test
```

Expected: All PASS.

**Step 8: Commit**

```bash
git add app/src/test/java/com/voxink/app/data/model/SttLanguageTest.kt \
  app/src/test/java/com/voxink/app/ime/ImeUiStateTest.kt \
  app/src/main/java/com/voxink/app/data/model/SttLanguage.kt \
  app/src/main/java/com/voxink/app/data/model/RecordingMode.kt \
  app/src/main/java/com/voxink/app/ime/ImeUiState.kt
git commit -m "feat: add SttLanguage, RecordingMode, ImeUiState domain models (TDD)"
```

---

### Task 4: TDD — Groq Whisper API Models + GroqApi Interface

**Files:**
- Test: `app/src/test/java/com/voxink/app/data/remote/WhisperResponseTest.kt`
- Test: `app/src/test/java/com/voxink/app/data/remote/GroqApiTest.kt`
- Create: `app/src/main/java/com/voxink/app/data/remote/WhisperResponse.kt`
- Create: `app/src/main/java/com/voxink/app/data/remote/GroqApi.kt`

**Step 1: RED — Write failing test for WhisperResponse**

```kotlin
package com.voxink.app.data.remote

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class WhisperResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `should deserialize minimal JSON response`() {
        val jsonString = """{"text": "你好世界"}"""
        val response = json.decodeFromString<WhisperResponse>(jsonString)
        assertThat(response.text).isEqualTo("你好世界")
    }

    @Test
    fun `should deserialize verbose JSON response`() {
        val jsonString = """
        {
            "task": "transcribe",
            "language": "chinese",
            "duration": 3.45,
            "text": "Hello world",
            "segments": [
                {
                    "id": 0,
                    "start": 0.0,
                    "end": 3.45,
                    "text": "Hello world"
                }
            ]
        }
        """.trimIndent()
        val response = json.decodeFromString<WhisperResponse>(jsonString)
        assertThat(response.text).isEqualTo("Hello world")
        assertThat(response.language).isEqualTo("chinese")
        assertThat(response.duration).isEqualTo(3.45)
        assertThat(response.segments).hasSize(1)
        assertThat(response.segments?.first()?.text).isEqualTo("Hello world")
    }

    @Test
    fun `should handle missing optional fields`() {
        val jsonString = """{"text": "test"}"""
        val response = json.decodeFromString<WhisperResponse>(jsonString)
        assertThat(response.task).isNull()
        assertThat(response.language).isNull()
        assertThat(response.duration).isNull()
        assertThat(response.segments).isNull()
    }
}
```

**Step 2: Run test to verify RED**

```bash
./gradlew test
```

Expected: FAIL — `Unresolved reference: WhisperResponse`.

**Step 3: GREEN — Create `WhisperResponse.kt`**

```kotlin
package com.voxink.app.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class WhisperResponse(
    val task: String? = null,
    val language: String? = null,
    val duration: Double? = null,
    val text: String,
    val segments: List<WhisperSegment>? = null,
)

@Serializable
data class WhisperSegment(
    val id: Int,
    val start: Double,
    val end: Double,
    val text: String,
)
```

**Step 4: Run test to verify GREEN**

```bash
./gradlew test
```

Expected: All PASS.

**Step 5: Create `GroqApi.kt` Retrofit interface**

```kotlin
package com.voxink.app.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface GroqApi {

    @Multipart
    @POST("openai/v1/audio/transcriptions")
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

**Step 6: RED — Write MockWebServer test for GroqApi**

```kotlin
package com.voxink.app.data.remote

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class GroqApiTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: GroqApi

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val json = Json { ignoreUnknownKeys = true }
        api = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GroqApi::class.java)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `should send transcription request with correct headers and parts`() = runTest {
        val responseJson = """{"text": "你好"}"""
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(responseJson)
                .addHeader("Content-Type", "application/json"),
        )

        val fakeAudio = ByteArray(100) { 0 }
        val filePart = MultipartBody.Part.createFormData(
            "file",
            "recording.wav",
            fakeAudio.toRequestBody("audio/wav".toMediaType()),
        )
        val model = "whisper-large-v3-turbo".toRequestBody("text/plain".toMediaType())
        val format = "verbose_json".toRequestBody("text/plain".toMediaType())
        val language = "zh".toRequestBody("text/plain".toMediaType())
        val prompt = "繁體中文轉錄。".toRequestBody("text/plain".toMediaType())

        val response = api.transcribe(
            authorization = "Bearer test-key",
            file = filePart,
            model = model,
            responseFormat = format,
            language = language,
            prompt = prompt,
        )

        assertThat(response.text).isEqualTo("你好")

        val request = mockWebServer.takeRequest()
        assertThat(request.path).isEqualTo("/openai/v1/audio/transcriptions")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-key")
        assertThat(request.getHeader("Content-Type")).contains("multipart/form-data")
    }

    @Test
    fun `should handle auto-detect with null language`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"text": "hello world"}""")
                .addHeader("Content-Type", "application/json"),
        )

        val filePart = MultipartBody.Part.createFormData(
            "file",
            "recording.wav",
            ByteArray(50).toRequestBody("audio/wav".toMediaType()),
        )

        val response = api.transcribe(
            authorization = "Bearer key",
            file = filePart,
            model = "whisper-large-v3-turbo".toRequestBody("text/plain".toMediaType()),
            responseFormat = "verbose_json".toRequestBody("text/plain".toMediaType()),
            language = null,
            prompt = null,
        )

        assertThat(response.text).isEqualTo("hello world")
    }
}
```

**Step 7: Run tests to verify GREEN (both WhisperResponse and GroqApi tests)**

```bash
./gradlew test
```

Expected: All PASS.

**Step 8: Commit**

```bash
git add app/src/test/java/com/voxink/app/data/remote/ \
  app/src/main/java/com/voxink/app/data/remote/
git commit -m "feat: add Groq Whisper API models and Retrofit interface (TDD)"
```

---

## Part C: Audio (TDD)

---

### Task 5: TDD — AudioEncoder (PCM → WAV)

**Purpose:** Pure Kotlin function that wraps raw PCM bytes with a WAV header. Fully testable, no Android dependencies.

**Files:**
- Test: `app/src/test/java/com/voxink/app/util/AudioEncoderTest.kt`
- Create: `app/src/main/java/com/voxink/app/util/AudioEncoder.kt`

**Step 1: RED — Write failing test**

```kotlin
package com.voxink.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioEncoderTest {

    @Test
    fun `should produce valid WAV header with RIFF magic bytes`() {
        val pcm = ByteArray(100) { it.toByte() }
        val wav = AudioEncoder.pcmToWav(pcm, sampleRate = 16000, channels = 1, bitsPerSample = 16)
        val header = String(wav.copyOfRange(0, 4), Charsets.US_ASCII)
        assertThat(header).isEqualTo("RIFF")
    }

    @Test
    fun `should have WAVE format marker`() {
        val pcm = ByteArray(100)
        val wav = AudioEncoder.pcmToWav(pcm, sampleRate = 16000, channels = 1, bitsPerSample = 16)
        val format = String(wav.copyOfRange(8, 12), Charsets.US_ASCII)
        assertThat(format).isEqualTo("WAVE")
    }

    @Test
    fun `should have correct total file size in header`() {
        val pcm = ByteArray(200)
        val wav = AudioEncoder.pcmToWav(pcm, sampleRate = 16000, channels = 1, bitsPerSample = 16)
        val fileSize = ByteBuffer.wrap(wav, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
        // RIFF chunk size = total file size - 8 (RIFF header)
        assertThat(fileSize).isEqualTo(wav.size - 8)
    }

    @Test
    fun `should have correct data chunk size`() {
        val pcmSize = 500
        val pcm = ByteArray(pcmSize)
        val wav = AudioEncoder.pcmToWav(pcm, sampleRate = 16000, channels = 1, bitsPerSample = 16)
        // Data chunk size is at offset 40, little-endian
        val dataSize = ByteBuffer.wrap(wav, 40, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertThat(dataSize).isEqualTo(pcmSize)
    }

    @Test
    fun `should have 44-byte header before PCM data`() {
        val pcm = byteArrayOf(1, 2, 3, 4)
        val wav = AudioEncoder.pcmToWav(pcm, sampleRate = 16000, channels = 1, bitsPerSample = 16)
        assertThat(wav.size).isEqualTo(44 + pcm.size)
        // PCM data starts at offset 44
        assertThat(wav[44]).isEqualTo(1.toByte())
        assertThat(wav[45]).isEqualTo(2.toByte())
    }

    @Test
    fun `should encode sample rate in header`() {
        val pcm = ByteArray(10)
        val wav = AudioEncoder.pcmToWav(pcm, sampleRate = 16000, channels = 1, bitsPerSample = 16)
        val sampleRate = ByteBuffer.wrap(wav, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertThat(sampleRate).isEqualTo(16000)
    }

    @Test
    fun `should handle empty PCM data`() {
        val pcm = ByteArray(0)
        val wav = AudioEncoder.pcmToWav(pcm, sampleRate = 16000, channels = 1, bitsPerSample = 16)
        assertThat(wav.size).isEqualTo(44)
    }
}
```

**Step 2: Run test to verify RED**

```bash
./gradlew test
```

Expected: FAIL — `Unresolved reference: AudioEncoder`.

**Step 3: GREEN — Create `AudioEncoder.kt`**

```kotlin
package com.voxink.app.util

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioEncoder {

    private const val WAV_HEADER_SIZE = 44

    fun pcmToWav(
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val totalSize = WAV_HEADER_SIZE + dataSize

        val buffer = ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(totalSize - 8) // file size - 8
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))

        // fmt sub-chunk
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16) // sub-chunk size (PCM)
        buffer.putShort(1) // audio format (PCM = 1)
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())

        // data sub-chunk
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)

        val output = ByteArrayOutputStream(totalSize)
        output.write(buffer.array())
        output.write(pcmData)
        return output.toByteArray()
    }
}
```

**Step 4: Run tests to verify GREEN**

```bash
./gradlew test
```

Expected: All PASS.

**Step 5: Commit**

```bash
git add app/src/test/java/com/voxink/app/util/AudioEncoderTest.kt \
  app/src/main/java/com/voxink/app/util/AudioEncoder.kt
git commit -m "feat: add AudioEncoder for PCM→WAV conversion (TDD)"
```

---

## Part D: Network (TDD)

---

### Task 6: TDD — SttRepository

**Purpose:** Orchestrates STT API calls. Accepts WAV bytes + language, returns transcribed text. Testable with mocked GroqApi.

**Files:**
- Test: `app/src/test/java/com/voxink/app/data/repository/SttRepositoryTest.kt`
- Create: `app/src/main/java/com/voxink/app/data/repository/SttRepository.kt`

**Step 1: RED — Write failing test**

```kotlin
package com.voxink.app.data.repository

import com.google.common.truth.Truth.assertThat
import com.voxink.app.data.model.SttLanguage
import com.voxink.app.data.remote.GroqApi
import com.voxink.app.data.remote.WhisperResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import java.io.IOException

class SttRepositoryTest {

    private val groqApi: GroqApi = mockk()
    private lateinit var repository: SttRepository

    @BeforeEach
    fun setUp() {
        repository = SttRepository(groqApi)
    }

    @Test
    fun `should return transcription text on successful API call`() = runTest {
        coEvery {
            groqApi.transcribe(any(), any(), any(), any(), any(), any())
        } returns WhisperResponse(text = "你好世界")

        val result = repository.transcribe(
            wavBytes = ByteArray(100),
            language = SttLanguage.Chinese,
            apiKey = "test-key",
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo("你好世界")
    }

    @Test
    fun `should pass authorization header with Bearer prefix`() = runTest {
        val authSlot = slot<String>()
        coEvery {
            groqApi.transcribe(capture(authSlot), any(), any(), any(), any(), any())
        } returns WhisperResponse(text = "test")

        repository.transcribe(ByteArray(10), SttLanguage.Auto, "my-api-key")

        assertThat(authSlot.captured).isEqualTo("Bearer my-api-key")
    }

    @Test
    fun `should omit language parameter for auto-detect`() = runTest {
        val langSlot = slot<RequestBody?>()
        coEvery {
            groqApi.transcribe(any(), any(), any(), any(), captureNullable(langSlot), any())
        } returns WhisperResponse(text = "test")

        repository.transcribe(ByteArray(10), SttLanguage.Auto, "key")

        assertThat(langSlot.captured).isNull()
    }

    @Test
    fun `should pass language code for specific language`() = runTest {
        coEvery {
            groqApi.transcribe(any(), any(), any(), any(), any(), any())
        } returns WhisperResponse(text = "test")

        repository.transcribe(ByteArray(10), SttLanguage.Chinese, "key")

        coVerify {
            groqApi.transcribe(any(), any(), any(), any(), match { it != null }, any())
        }
    }

    @Test
    fun `should return failure on IOException`() = runTest {
        coEvery {
            groqApi.transcribe(any(), any(), any(), any(), any(), any())
        } throws IOException("Network error")

        val result = repository.transcribe(ByteArray(10), SttLanguage.Auto, "key")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Network error")
    }

    @Test
    fun `should return failure on empty API key`() = runTest {
        val result = repository.transcribe(ByteArray(10), SttLanguage.Auto, "")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("API key")
    }
}
```

**Step 2: Run test to verify RED**

```bash
./gradlew test
```

Expected: FAIL — `Unresolved reference: SttRepository`.

**Step 3: GREEN — Create `SttRepository.kt`**

```kotlin
package com.voxink.app.data.repository

import com.voxink.app.data.model.SttLanguage
import com.voxink.app.data.remote.GroqApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SttRepository @Inject constructor(
    private val groqApi: GroqApi,
) {

    suspend fun transcribe(
        wavBytes: ByteArray,
        language: SttLanguage,
        apiKey: String,
    ): Result<String> {
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("API key not configured"))
        }

        return try {
            val filePart = MultipartBody.Part.createFormData(
                "file",
                "recording.wav",
                wavBytes.toRequestBody("audio/wav".toMediaType()),
            )
            val model = WHISPER_MODEL.toRequestBody(TEXT_PLAIN)
            val format = RESPONSE_FORMAT.toRequestBody(TEXT_PLAIN)
            val langBody = language.code?.toRequestBody(TEXT_PLAIN)
            val promptBody = language.prompt.toRequestBody(TEXT_PLAIN)

            val response = groqApi.transcribe(
                authorization = "Bearer $apiKey",
                file = filePart,
                model = model,
                responseFormat = format,
                language = langBody,
                prompt = promptBody,
            )

            Result.success(response.text)
        } catch (e: Exception) {
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

**Step 4: Run tests to verify GREEN**

```bash
./gradlew test
```

Expected: All PASS.

**Step 5: Commit**

```bash
git add app/src/test/java/com/voxink/app/data/repository/SttRepositoryTest.kt \
  app/src/main/java/com/voxink/app/data/repository/SttRepository.kt
git commit -m "feat: add SttRepository with Groq Whisper integration (TDD)"
```

---

## Part E: Storage (TDD)

---

### Task 7: TDD — ApiKeyManager + PreferencesManager

**Files:**
- Test: `app/src/test/java/com/voxink/app/data/local/ApiKeyManagerTest.kt`
- Test: `app/src/test/java/com/voxink/app/data/local/PreferencesManagerTest.kt`
- Create: `app/src/main/java/com/voxink/app/data/local/ApiKeyManager.kt`
- Create: `app/src/main/java/com/voxink/app/data/local/PreferencesManager.kt`

**Step 1: RED — Write failing test for ApiKeyManager**

> `ApiKeyManager` wraps `SharedPreferences` (encrypted). For testing, we use a plain `SharedPreferences` interface — the encryption is a runtime concern, not a logic concern.

```kotlin
package com.voxink.app.data.local

import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ApiKeyManagerTest {

    private val sharedPreferences: SharedPreferences = mockk(relaxed = true)
    private val editor: SharedPreferences.Editor = mockk(relaxed = true)
    private lateinit var manager: ApiKeyManager

    @BeforeEach
    fun setUp() {
        every { sharedPreferences.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        manager = ApiKeyManager(sharedPreferences)
    }

    @Test
    fun `should return null when no Groq key is stored`() {
        every { sharedPreferences.getString("groq_api_key", null) } returns null
        assertThat(manager.getGroqApiKey()).isNull()
    }

    @Test
    fun `should return stored Groq API key`() {
        every { sharedPreferences.getString("groq_api_key", null) } returns "gsk_test123"
        assertThat(manager.getGroqApiKey()).isEqualTo("gsk_test123")
    }

    @Test
    fun `should save Groq API key`() {
        manager.setGroqApiKey("gsk_new_key")
        verify { editor.putString("groq_api_key", "gsk_new_key") }
        verify { editor.apply() }
    }

    @Test
    fun `should clear Groq API key when set to null`() {
        every { editor.remove(any()) } returns editor
        manager.setGroqApiKey(null)
        verify { editor.remove("groq_api_key") }
        verify { editor.apply() }
    }

    @Test
    fun `should report key configured when key exists`() {
        every { sharedPreferences.getString("groq_api_key", null) } returns "gsk_key"
        assertThat(manager.isGroqKeyConfigured()).isTrue()
    }

    @Test
    fun `should report key not configured when key is null`() {
        every { sharedPreferences.getString("groq_api_key", null) } returns null
        assertThat(manager.isGroqKeyConfigured()).isFalse()
    }

    @Test
    fun `should report key not configured when key is blank`() {
        every { sharedPreferences.getString("groq_api_key", null) } returns "  "
        assertThat(manager.isGroqKeyConfigured()).isFalse()
    }
}
```

**Step 2: RED — Write failing test for PreferencesManager**

```kotlin
package com.voxink.app.data.local

import com.google.common.truth.Truth.assertThat
import com.voxink.app.data.model.RecordingMode
import com.voxink.app.data.model.SttLanguage
import org.junit.jupiter.api.Test

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
}
```

**Step 3: Run tests to verify RED**

```bash
./gradlew test
```

Expected: FAIL — unresolved references.

**Step 4: GREEN — Create `ApiKeyManager.kt`**

```kotlin
package com.voxink.app.data.local

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiKeyManager @Inject constructor(
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
```

**Step 5: GREEN — Create `PreferencesManager.kt`**

```kotlin
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
```

**Step 6: Create Hilt AppModule for encrypted prefs**

Create `app/src/main/java/com/voxink/app/di/AppModule.kt`:

```kotlin
package com.voxink.app.di

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideEncryptedSharedPreferences(
        @ApplicationContext context: Context,
    ): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            "voxink_secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
```

**Step 7: Run tests to verify GREEN**

```bash
./gradlew test
```

Expected: All PASS.

**Step 8: Commit**

```bash
git add app/src/test/java/com/voxink/app/data/local/ \
  app/src/main/java/com/voxink/app/data/local/ \
  app/src/main/java/com/voxink/app/di/AppModule.kt
git commit -m "feat: add ApiKeyManager (encrypted) + PreferencesManager (DataStore) (TDD)"
```

---

## Part F: Orchestration (TDD)

---

### Task 8: TDD — TranscribeAudioUseCase

**Purpose:** Coordinates audio encoding + API call. Accepts raw PCM, returns transcription text.

**Files:**
- Test: `app/src/test/java/com/voxink/app/domain/usecase/TranscribeAudioUseCaseTest.kt`
- Create: `app/src/main/java/com/voxink/app/domain/usecase/TranscribeAudioUseCase.kt`

**Step 1: RED — Write failing test**

```kotlin
package com.voxink.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.voxink.app.data.model.SttLanguage
import com.voxink.app.data.repository.SttRepository
import com.voxink.app.util.AudioEncoder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TranscribeAudioUseCaseTest {

    private val sttRepository: SttRepository = mockk()
    private lateinit var useCase: TranscribeAudioUseCase

    @BeforeEach
    fun setUp() {
        mockkObject(AudioEncoder)
        useCase = TranscribeAudioUseCase(sttRepository)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(AudioEncoder)
    }

    @Test
    fun `should encode PCM to WAV and call repository`() = runTest {
        val pcm = ByteArray(100) { 1 }
        val wav = ByteArray(144) { 2 }
        io.mockk.every { AudioEncoder.pcmToWav(pcm, 16000, 1, 16) } returns wav
        coEvery { sttRepository.transcribe(wav, SttLanguage.Chinese, "key") } returns Result.success("你好")

        val result = useCase(pcm, SttLanguage.Chinese, "key")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo("你好")
        coVerify { sttRepository.transcribe(wav, SttLanguage.Chinese, "key") }
    }

    @Test
    fun `should propagate repository errors`() = runTest {
        val pcm = ByteArray(10)
        io.mockk.every { AudioEncoder.pcmToWav(any(), any(), any(), any()) } returns ByteArray(54)
        coEvery { sttRepository.transcribe(any(), any(), any()) } returns
            Result.failure(Exception("Network error"))

        val result = useCase(pcm, SttLanguage.Auto, "key")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Network error")
    }

    @Test
    fun `should use 16kHz mono 16-bit encoding defaults`() = runTest {
        val pcm = ByteArray(50)
        io.mockk.every { AudioEncoder.pcmToWav(pcm, 16000, 1, 16) } returns ByteArray(94)
        coEvery { sttRepository.transcribe(any(), any(), any()) } returns Result.success("test")

        useCase(pcm, SttLanguage.English, "key")

        io.mockk.verify { AudioEncoder.pcmToWav(pcm, 16000, 1, 16) }
    }
}
```

**Step 2: Run test to verify RED**

```bash
./gradlew test
```

Expected: FAIL — `Unresolved reference: TranscribeAudioUseCase`.

**Step 3: GREEN — Create `TranscribeAudioUseCase.kt`**

```kotlin
package com.voxink.app.domain.usecase

import com.voxink.app.data.model.SttLanguage
import com.voxink.app.data.repository.SttRepository
import com.voxink.app.util.AudioEncoder
import javax.inject.Inject

class TranscribeAudioUseCase @Inject constructor(
    private val sttRepository: SttRepository,
) {

    suspend operator fun invoke(
        pcmData: ByteArray,
        language: SttLanguage,
        apiKey: String,
        sampleRate: Int = SAMPLE_RATE,
        channels: Int = CHANNELS,
        bitsPerSample: Int = BITS_PER_SAMPLE,
    ): Result<String> {
        val wavBytes = AudioEncoder.pcmToWav(pcmData, sampleRate, channels, bitsPerSample)
        return sttRepository.transcribe(wavBytes, language, apiKey)
    }

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNELS = 1
        const val BITS_PER_SAMPLE = 16
    }
}
```

**Step 4: Run tests to verify GREEN**

```bash
./gradlew test
```

Expected: All PASS.

**Step 5: Commit**

```bash
git add app/src/test/java/com/voxink/app/domain/usecase/TranscribeAudioUseCaseTest.kt \
  app/src/main/java/com/voxink/app/domain/usecase/TranscribeAudioUseCase.kt
git commit -m "feat: add TranscribeAudioUseCase (PCM→WAV→STT pipeline) (TDD)"
```

---

### Task 9: TDD — RecordingController

**Purpose:** State machine orchestrating mic tap → recording → transcription. Emits `ImeUiState` flow.

**Files:**
- Test: `app/src/test/java/com/voxink/app/ime/RecordingControllerTest.kt`
- Create: `app/src/main/java/com/voxink/app/ime/RecordingController.kt`

**Step 1: RED — Write failing test**

```kotlin
package com.voxink.app.ime

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.voxink.app.data.local.ApiKeyManager
import com.voxink.app.data.model.SttLanguage
import com.voxink.app.domain.usecase.TranscribeAudioUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingControllerTest {

    private val transcribeUseCase: TranscribeAudioUseCase = mockk()
    private val apiKeyManager: ApiKeyManager = mockk()
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var controller: RecordingController

    private var fakeRecordedAudio: ByteArray = ByteArray(100) { it.toByte() }
    private var isRecording = false
    private val startRecording: () -> Unit = { isRecording = true }
    private val stopRecording: () -> ByteArray = {
        isRecording = false
        fakeRecordedAudio
    }

    @BeforeEach
    fun setUp() {
        every { apiKeyManager.getGroqApiKey() } returns "test-key"
        controller = RecordingController(
            transcribeUseCase = transcribeUseCase,
            apiKeyManager = apiKeyManager,
            ioDispatcher = testDispatcher,
        )
    }

    @Test
    fun `should start in Idle state`() = runTest {
        controller.uiState.test {
            assertThat(awaitItem()).isEqualTo(ImeUiState.Idle)
        }
    }

    @Test
    fun `should transition to Recording on start`() = runTest {
        controller.uiState.test {
            assertThat(awaitItem()).isEqualTo(ImeUiState.Idle)
            controller.onStartRecording(startRecording)
            assertThat(awaitItem()).isEqualTo(ImeUiState.Recording)
        }
    }

    @Test
    fun `should transition to Processing then Result on stop`() = runTest {
        coEvery { transcribeUseCase(any(), any(), any(), any(), any(), any()) } returns
            Result.success("你好世界")

        controller.uiState.test {
            assertThat(awaitItem()).isEqualTo(ImeUiState.Idle)
            controller.onStartRecording(startRecording)
            assertThat(awaitItem()).isEqualTo(ImeUiState.Recording)

            controller.onStopRecording(stopRecording, SttLanguage.Chinese)
            assertThat(awaitItem()).isEqualTo(ImeUiState.Processing)
            assertThat(awaitItem()).isEqualTo(ImeUiState.Result("你好世界"))
        }
    }

    @Test
    fun `should transition to Error on transcription failure`() = runTest {
        coEvery { transcribeUseCase(any(), any(), any(), any(), any(), any()) } returns
            Result.failure(Exception("API error"))

        controller.uiState.test {
            assertThat(awaitItem()).isEqualTo(ImeUiState.Idle)
            controller.onStartRecording(startRecording)
            skipItems(1)

            controller.onStopRecording(stopRecording, SttLanguage.Auto)
            skipItems(1)
            val error = awaitItem()
            assertThat(error).isInstanceOf(ImeUiState.Error::class.java)
            assertThat((error as ImeUiState.Error).message).contains("API error")
        }
    }

    @Test
    fun `should show error when API key not configured`() = runTest {
        every { apiKeyManager.getGroqApiKey() } returns null

        controller.uiState.test {
            assertThat(awaitItem()).isEqualTo(ImeUiState.Idle)
            controller.onStartRecording(startRecording)
            skipItems(1)

            controller.onStopRecording(stopRecording, SttLanguage.Auto)
            val state = awaitItem()
            assertThat(state).isInstanceOf(ImeUiState.Error::class.java)
            assertThat((state as ImeUiState.Error).message).contains("API key")
        }
    }

    @Test
    fun `should return to Idle on dismiss`() = runTest {
        controller.uiState.test {
            assertThat(awaitItem()).isEqualTo(ImeUiState.Idle)
            controller.onStartRecording(startRecording)
            skipItems(1)
            controller.dismiss()
            assertThat(awaitItem()).isEqualTo(ImeUiState.Idle)
        }
    }
}
```

**Step 2: Run test to verify RED**

```bash
./gradlew test
```

Expected: FAIL — `Unresolved reference: RecordingController`.

**Step 3: GREEN — Create `RecordingController.kt`**

```kotlin
package com.voxink.app.ime

import com.voxink.app.data.local.ApiKeyManager
import com.voxink.app.data.model.SttLanguage
import com.voxink.app.domain.usecase.TranscribeAudioUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RecordingController(
    private val transcribeUseCase: TranscribeAudioUseCase,
    private val apiKeyManager: ApiKeyManager,
    private val ioDispatcher: CoroutineDispatcher,
) {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val _uiState = MutableStateFlow<ImeUiState>(ImeUiState.Idle)
    val uiState: StateFlow<ImeUiState> = _uiState.asStateFlow()

    fun onStartRecording(startRecording: () -> Unit) {
        startRecording()
        _uiState.value = ImeUiState.Recording
    }

    fun onStopRecording(stopRecording: () -> ByteArray, language: SttLanguage) {
        val pcmData = stopRecording()
        val apiKey = apiKeyManager.getGroqApiKey()

        if (apiKey.isNullOrBlank()) {
            _uiState.value = ImeUiState.Error("API key not configured")
            return
        }

        _uiState.value = ImeUiState.Processing
        scope.launch {
            val result = transcribeUseCase(pcmData, language, apiKey)
            _uiState.value = result.fold(
                onSuccess = { ImeUiState.Result(it) },
                onFailure = { ImeUiState.Error(it.message ?: "Transcription failed") },
            )
        }
    }

    fun dismiss() {
        _uiState.value = ImeUiState.Idle
    }

    fun destroy() {
        scope.cancel()
    }
}
```

**Step 4: Run tests to verify GREEN**

```bash
./gradlew test
```

Expected: All PASS.

**Step 5: Commit**

```bash
git add app/src/test/java/com/voxink/app/ime/RecordingControllerTest.kt \
  app/src/main/java/com/voxink/app/ime/RecordingController.kt
git commit -m "feat: add RecordingController state machine (TDD)"
```

---

## Part G: Android Integration

---

### Task 10: AudioRecorder (Android)

**Purpose:** Thin wrapper around Android's `AudioRecord` API. Not unit-testable — logic tested via `RecordingController`.

**Files:**
- Create: `app/src/main/java/com/voxink/app/ime/AudioRecorder.kt`

**Step 1: Create `AudioRecorder.kt`**

```kotlin
package com.voxink.app.ime

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import timber.log.Timber
import java.io.ByteArrayOutputStream

class AudioRecorder(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var pcmOutput: ByteArrayOutputStream? = null

    @Volatile
    private var isRecording = false

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startRecording(): Boolean {
        if (!hasPermission()) {
            Timber.w("RECORD_AUDIO permission not granted")
            return false
        }

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            Timber.e("Invalid buffer size: $bufferSize")
            return false
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize,
            )
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException creating AudioRecord")
            return false
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Timber.e("AudioRecord failed to initialize")
            audioRecord?.release()
            audioRecord = null
            return false
        }

        pcmOutput = ByteArrayOutputStream()
        isRecording = true
        audioRecord?.startRecording()

        recordingThread = Thread {
            val buffer = ByteArray(bufferSize)
            while (isRecording) {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (bytesRead > 0) {
                    pcmOutput?.write(buffer, 0, bytesRead)
                }
            }
        }.apply { start() }

        Timber.d("Recording started")
        return true
    }

    fun stopRecording(): ByteArray {
        isRecording = false
        recordingThread?.join(STOP_TIMEOUT_MS)
        recordingThread = null

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        val pcmData = pcmOutput?.toByteArray() ?: ByteArray(0)
        pcmOutput?.close()
        pcmOutput = null

        Timber.d("Recording stopped, captured ${pcmData.size} bytes")
        return pcmData
    }

    fun release() {
        if (isRecording) stopRecording()
    }

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val STOP_TIMEOUT_MS = 2000L
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/voxink/app/ime/AudioRecorder.kt
git commit -m "feat: add AudioRecorder wrapping Android AudioRecord API"
```

---

### Task 11: Update VoxInkIME + Keyboard Layout

**Purpose:** Wire recording pipeline into IME. Update candidate bar for transcription states. Support tap-to-toggle and hold-to-record.

**Files:**
- Modify: `app/src/main/res/layout/keyboard_view.xml`
- Modify: `app/src/main/java/com/voxink/app/ime/VoxInkIME.kt`
- Create: `app/src/main/java/com/voxink/app/ime/VoxInkIMEEntryPoint.kt`

**Step 1: Update `keyboard_view.xml` — enhanced candidate bar**

Replace entire file with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:background="@color/keyboard_background"
    android:padding="4dp">

    <!-- Candidate bar -->
    <LinearLayout
        android:id="@+id/candidate_bar"
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:paddingStart="12dp"
        android:paddingEnd="12dp"
        android:visibility="gone">

        <ProgressBar
            android:id="@+id/candidate_progress"
            android:layout_width="20dp"
            android:layout_height="20dp"
            android:layout_marginEnd="8dp"
            android:visibility="gone"
            style="?android:attr/progressBarStyleSmall" />

        <TextView
            android:id="@+id/candidate_text"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:textColor="@color/key_text"
            android:textSize="16sp"
            android:ellipsize="end"
            android:maxLines="2" />
    </LinearLayout>

    <!-- Key row -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:orientation="horizontal"
        android:gravity="center">

        <ImageButton
            android:id="@+id/btn_switch"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:background="@color/key_background"
            android:contentDescription="Switch keyboard"
            android:src="@android:drawable/ic_menu_sort_by_size"
            android:layout_margin="2dp" />

        <ImageButton
            android:id="@+id/btn_backspace"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:background="@color/key_background"
            android:contentDescription="Backspace"
            android:src="@android:drawable/ic_delete"
            android:layout_margin="2dp" />

        <ImageButton
            android:id="@+id/btn_mic"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="2"
            android:background="@color/mic_idle"
            android:contentDescription="Record"
            android:src="@android:drawable/ic_btn_speak_now"
            android:layout_margin="2dp" />

        <ImageButton
            android:id="@+id/btn_enter"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:background="@color/key_background"
            android:contentDescription="Enter"
            android:src="@android:drawable/ic_menu_send"
            android:layout_margin="2dp" />

        <ImageButton
            android:id="@+id/btn_settings"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:background="@color/key_background"
            android:contentDescription="Settings"
            android:src="@android:drawable/ic_menu_preferences"
            android:layout_margin="2dp" />
    </LinearLayout>
</LinearLayout>
```

**Step 2: Create Hilt `EntryPoint` for IME**

```kotlin
package com.voxink.app.ime

import com.voxink.app.data.local.ApiKeyManager
import com.voxink.app.data.local.PreferencesManager
import com.voxink.app.domain.usecase.TranscribeAudioUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface VoxInkIMEEntryPoint {
    fun transcribeAudioUseCase(): TranscribeAudioUseCase
    fun apiKeyManager(): ApiKeyManager
    fun preferencesManager(): PreferencesManager
}
```

**Step 3: Rewrite `VoxInkIME.kt` with full recording pipeline**

```kotlin
package com.voxink.app.ime

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.voxink.app.R
import com.voxink.app.data.local.PreferencesManager
import com.voxink.app.data.model.RecordingMode
import com.voxink.app.data.model.SttLanguage
import com.voxink.app.ui.MainActivity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

class VoxInkIME : InputMethodService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var actionHandler: KeyboardActionHandler
    private lateinit var recordingController: RecordingController
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var preferencesManager: PreferencesManager

    private var candidateBar: LinearLayout? = null
    private var candidateText: TextView? = null
    private var candidateProgress: ProgressBar? = null
    private var micButton: ImageButton? = null

    override fun onCreateInputView(): View {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            VoxInkIMEEntryPoint::class.java,
        )

        audioRecorder = AudioRecorder(this)
        preferencesManager = entryPoint.preferencesManager()
        recordingController = RecordingController(
            transcribeUseCase = entryPoint.transcribeAudioUseCase(),
            apiKeyManager = entryPoint.apiKeyManager(),
            ioDispatcher = Dispatchers.IO,
        )

        actionHandler = KeyboardActionHandler(
            onSendKeyEvent = { keyCode -> sendDownUpKeyEvents(keyCode) },
            onSwitchKeyboard = {
                switchToPreviousInputMethod(currentInputBinding?.connectionToken)
            },
            onOpenSettings = { launchSettings() },
            onMicTap = { handleMicTap() },
        )

        val view = layoutInflater.inflate(R.layout.keyboard_view, null)
        bindViews(view)
        bindButtons(view)
        observeUiState()
        return view
    }

    private fun bindViews(view: View) {
        candidateBar = view.findViewById(R.id.candidate_bar)
        candidateText = view.findViewById(R.id.candidate_text)
        candidateProgress = view.findViewById(R.id.candidate_progress)
        micButton = view.findViewById(R.id.btn_mic)
    }

    private fun bindButtons(view: View) {
        view.findViewById<ImageButton>(R.id.btn_backspace)?.setOnClickListener {
            actionHandler.handle(KeyboardAction.Backspace)
        }
        view.findViewById<ImageButton>(R.id.btn_enter)?.setOnClickListener {
            actionHandler.handle(KeyboardAction.Enter)
        }
        view.findViewById<ImageButton>(R.id.btn_switch)?.setOnClickListener {
            actionHandler.handle(KeyboardAction.SwitchKeyboard)
        }
        view.findViewById<ImageButton>(R.id.btn_settings)?.setOnClickListener {
            actionHandler.handle(KeyboardAction.OpenSettings)
        }
        setupMicButton(view.findViewById(R.id.btn_mic))
    }

    private fun setupMicButton(micBtn: ImageButton?) {
        micBtn ?: return
        serviceScope.launch {
            val mode = preferencesManager.recordingModeFlow.first()
            when (mode) {
                RecordingMode.TAP_TO_TOGGLE -> {
                    micBtn.setOnClickListener { handleMicTap() }
                }
                RecordingMode.HOLD_TO_RECORD -> {
                    micBtn.setOnTouchListener { _, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> { startRecording(); true }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { stopRecording(); true }
                            else -> false
                        }
                    }
                }
            }
        }
    }

    private fun handleMicTap() {
        when (recordingController.uiState.value) {
            ImeUiState.Idle, is ImeUiState.Error, is ImeUiState.Result -> startRecording()
            ImeUiState.Recording -> stopRecording()
            ImeUiState.Processing -> { /* ignore */ }
        }
    }

    private fun startRecording() {
        if (!audioRecorder.hasPermission()) {
            candidateBar?.visibility = View.VISIBLE
            candidateText?.text = getString(R.string.mic_permission_required)
            candidateProgress?.visibility = View.GONE
            return
        }
        recordingController.onStartRecording { audioRecorder.startRecording() }
    }

    private fun stopRecording() {
        serviceScope.launch {
            val language = preferencesManager.languageFlow.first()
            recordingController.onStopRecording(
                stopRecording = { audioRecorder.stopRecording() },
                language = language,
            )
        }
    }

    private fun observeUiState() {
        serviceScope.launch {
            recordingController.uiState.collect { state -> updateUi(state) }
        }
    }

    private fun updateUi(state: ImeUiState) {
        when (state) {
            ImeUiState.Idle -> {
                candidateBar?.visibility = View.GONE
                candidateBar?.setOnClickListener(null)
            }
            ImeUiState.Recording -> {
                candidateBar?.visibility = View.VISIBLE
                candidateProgress?.visibility = View.GONE
                candidateText?.text = getString(R.string.recording)
            }
            ImeUiState.Processing -> {
                candidateBar?.visibility = View.VISIBLE
                candidateProgress?.visibility = View.VISIBLE
                candidateText?.text = getString(R.string.processing)
            }
            is ImeUiState.Result -> {
                candidateBar?.visibility = View.VISIBLE
                candidateProgress?.visibility = View.GONE
                candidateText?.text = state.text
                candidateBar?.setOnClickListener {
                    currentInputConnection?.commitText(state.text, 1)
                    recordingController.dismiss()
                }
            }
            is ImeUiState.Error -> {
                candidateBar?.visibility = View.VISIBLE
                candidateProgress?.visibility = View.GONE
                candidateText?.text = state.message
                candidateBar?.setOnClickListener { recordingController.dismiss() }
            }
        }
        micButton?.setBackgroundColor(
            getColor(if (state == ImeUiState.Recording) R.color.mic_active else R.color.mic_idle),
        )
    }

    private fun launchSettings() {
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    override fun onDestroy() {
        audioRecorder.release()
        recordingController.destroy()
        serviceScope.cancel()
        super.onDestroy()
    }
}
```

**Step 4: Commit**

```bash
git add app/src/main/res/layout/keyboard_view.xml \
  app/src/main/java/com/voxink/app/ime/VoxInkIMEEntryPoint.kt \
  app/src/main/java/com/voxink/app/ime/VoxInkIME.kt
git commit -m "feat: wire recording pipeline into VoxInkIME with candidate bar"
```

---

## Part H: Settings UI

---

### Task 12: TDD — SettingsViewModel

**Files:**
- Test: `app/src/test/java/com/voxink/app/ui/settings/SettingsViewModelTest.kt`
- Create: `app/src/main/java/com/voxink/app/ui/settings/SettingsUiState.kt`
- Create: `app/src/main/java/com/voxink/app/ui/settings/SettingsViewModel.kt`

**Step 1: RED — Write failing test**

```kotlin
package com.voxink.app.ui.settings

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.voxink.app.data.local.ApiKeyManager
import com.voxink.app.data.local.PreferencesManager
import com.voxink.app.data.model.RecordingMode
import com.voxink.app.data.model.SttLanguage
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val apiKeyManager: ApiKeyManager = mockk(relaxed = true)
    private val preferencesManager: PreferencesManager = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { apiKeyManager.isGroqKeyConfigured() } returns false
        every { apiKeyManager.getGroqApiKey() } returns null
        every { preferencesManager.languageFlow } returns flowOf(SttLanguage.Auto)
        every { preferencesManager.recordingModeFlow } returns flowOf(RecordingMode.TAP_TO_TOGGLE)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SettingsViewModel(apiKeyManager, preferencesManager)

    @Test
    fun `should emit initial state with defaults`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertThat(state.isApiKeyConfigured).isFalse()
            assertThat(state.language).isEqualTo(SttLanguage.Auto)
            assertThat(state.recordingMode).isEqualTo(RecordingMode.TAP_TO_TOGGLE)
        }
    }

    @Test
    fun `should save API key and update state`() = runTest {
        val vm = createViewModel()
        every { apiKeyManager.isGroqKeyConfigured() } returns true

        vm.saveApiKey("gsk_test123")

        verify { apiKeyManager.setGroqApiKey("gsk_test123") }
        vm.uiState.test {
            assertThat(awaitItem().isApiKeyConfigured).isTrue()
        }
    }

    @Test
    fun `should update language`() = runTest {
        val vm = createViewModel()
        vm.setLanguage(SttLanguage.Chinese)
        coVerify { preferencesManager.setLanguage(SttLanguage.Chinese) }
    }

    @Test
    fun `should update recording mode`() = runTest {
        val vm = createViewModel()
        vm.setRecordingMode(RecordingMode.HOLD_TO_RECORD)
        coVerify { preferencesManager.setRecordingMode(RecordingMode.HOLD_TO_RECORD) }
    }
}
```

**Step 2: Run test to verify RED**

```bash
./gradlew test
```

Expected: FAIL — unresolved references.

**Step 3: GREEN — Create `SettingsUiState.kt`**

```kotlin
package com.voxink.app.ui.settings

import com.voxink.app.data.model.RecordingMode
import com.voxink.app.data.model.SttLanguage

data class SettingsUiState(
    val isApiKeyConfigured: Boolean = false,
    val apiKeyDisplay: String = "",
    val language: SttLanguage = SttLanguage.Auto,
    val recordingMode: RecordingMode = RecordingMode.TAP_TO_TOGGLE,
)
```

**Step 4: GREEN — Create `SettingsViewModel.kt`**

```kotlin
package com.voxink.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxink.app.data.local.ApiKeyManager
import com.voxink.app.data.local.PreferencesManager
import com.voxink.app.data.model.RecordingMode
import com.voxink.app.data.model.SttLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val apiKeyManager: ApiKeyManager,
    private val preferencesManager: PreferencesManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        _uiState.update {
            it.copy(
                isApiKeyConfigured = apiKeyManager.isGroqKeyConfigured(),
                apiKeyDisplay = maskApiKey(apiKeyManager.getGroqApiKey()),
            )
        }
        viewModelScope.launch {
            preferencesManager.languageFlow.collect { lang ->
                _uiState.update { it.copy(language = lang) }
            }
        }
        viewModelScope.launch {
            preferencesManager.recordingModeFlow.collect { mode ->
                _uiState.update { it.copy(recordingMode = mode) }
            }
        }
    }

    fun saveApiKey(key: String) {
        apiKeyManager.setGroqApiKey(key)
        _uiState.update {
            it.copy(
                isApiKeyConfigured = apiKeyManager.isGroqKeyConfigured(),
                apiKeyDisplay = maskApiKey(key),
            )
        }
    }

    fun setLanguage(language: SttLanguage) {
        viewModelScope.launch { preferencesManager.setLanguage(language) }
    }

    fun setRecordingMode(mode: RecordingMode) {
        viewModelScope.launch { preferencesManager.setRecordingMode(mode) }
    }

    private fun maskApiKey(key: String?): String {
        if (key.isNullOrBlank()) return ""
        if (key.length <= 8) return "••••••••"
        return key.take(4) + "••••" + key.takeLast(4)
    }
}
```

**Step 5: Run tests to verify GREEN**

```bash
./gradlew test
```

Expected: All PASS.

**Step 6: Commit**

```bash
git add app/src/test/java/com/voxink/app/ui/settings/ \
  app/src/main/java/com/voxink/app/ui/settings/
git commit -m "feat: add SettingsViewModel with API key and preferences (TDD)"
```

---

### Task 13: Settings UI + String Resources + Navigation

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`
- Create: `app/src/main/java/com/voxink/app/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/voxink/app/ui/HomeScreen.kt`
- Modify: `app/src/main/java/com/voxink/app/ui/MainActivity.kt`

**Step 1: Replace English strings `res/values/strings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">VoxInk</string>
    <string name="ime_name">VoxInk Voice</string>
    <string name="ime_subtype_auto">Auto-detect</string>
    <string name="ime_subtype_zh">Chinese (Traditional)</string>
    <string name="ime_subtype_en">English</string>
    <string name="ime_subtype_ja">Japanese</string>
    <string name="welcome_message">Welcome to VoxInk</string>
    <string name="welcome_description">AI voice keyboard with BYOK (Bring Your Own Key)</string>
    <string name="enable_keyboard_prompt">Enable VoxInk keyboard in system settings to get started.</string>
    <string name="open_keyboard_settings">Open Keyboard Settings</string>
    <string name="open_settings">Settings</string>
    <string name="setup_keyboard">Keyboard: %1$s</string>
    <string name="setup_api_key">API Key: %1$s</string>
    <string name="setup_permission">Microphone: %1$s</string>
    <string name="status_configured">Configured</string>
    <string name="status_not_configured">Not configured</string>
    <string name="status_enabled">Enabled</string>
    <string name="status_disabled">Disabled</string>
    <string name="status_granted">Granted</string>
    <string name="status_denied">Denied</string>
    <string name="settings_title">Settings</string>
    <string name="settings_api_key_section">API Key</string>
    <string name="settings_groq_api_key">Groq API Key</string>
    <string name="settings_api_key_hint">Enter your Groq API key (gsk_…)</string>
    <string name="settings_save">Save</string>
    <string name="settings_language_section">Language</string>
    <string name="settings_recording_section">Recording</string>
    <string name="settings_tap_to_toggle">Tap to toggle</string>
    <string name="settings_hold_to_record">Hold to record</string>
    <string name="settings_permission_section">Permissions</string>
    <string name="settings_grant_mic">Grant Microphone Permission</string>
    <string name="lang_auto">Auto-detect</string>
    <string name="lang_zh">中文（繁體）</string>
    <string name="lang_en">English</string>
    <string name="lang_ja">日本語</string>
    <string name="recording">Recording…</string>
    <string name="processing">Processing…</string>
    <string name="mic_permission_required">Microphone permission required. Open Settings to grant.</string>
</resources>
```

**Step 2: Replace Traditional Chinese strings `res/values-zh-rTW/strings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">語墨</string>
    <string name="ime_name">語墨語音</string>
    <string name="ime_subtype_auto">自動偵測</string>
    <string name="ime_subtype_zh">中文（繁體）</string>
    <string name="ime_subtype_en">英文</string>
    <string name="ime_subtype_ja">日文</string>
    <string name="welcome_message">歡迎使用語墨</string>
    <string name="welcome_description">自帶 API 金鑰的 AI 語音鍵盤</string>
    <string name="enable_keyboard_prompt">請至系統設定啟用語墨鍵盤以開始使用。</string>
    <string name="open_keyboard_settings">開啟鍵盤設定</string>
    <string name="open_settings">設定</string>
    <string name="setup_keyboard">鍵盤：%1$s</string>
    <string name="setup_api_key">API 金鑰：%1$s</string>
    <string name="setup_permission">麥克風：%1$s</string>
    <string name="status_configured">已設定</string>
    <string name="status_not_configured">未設定</string>
    <string name="status_enabled">已啟用</string>
    <string name="status_disabled">未啟用</string>
    <string name="status_granted">已授權</string>
    <string name="status_denied">未授權</string>
    <string name="settings_title">設定</string>
    <string name="settings_api_key_section">API 金鑰</string>
    <string name="settings_groq_api_key">Groq API 金鑰</string>
    <string name="settings_api_key_hint">輸入 Groq API 金鑰（gsk_…）</string>
    <string name="settings_save">儲存</string>
    <string name="settings_language_section">語言</string>
    <string name="settings_recording_section">錄音</string>
    <string name="settings_tap_to_toggle">點擊切換</string>
    <string name="settings_hold_to_record">按住錄音</string>
    <string name="settings_permission_section">權限</string>
    <string name="settings_grant_mic">授權麥克風權限</string>
    <string name="lang_auto">自動偵測</string>
    <string name="lang_zh">中文（繁體）</string>
    <string name="lang_en">English</string>
    <string name="lang_ja">日本語</string>
    <string name="recording">錄音中…</string>
    <string name="processing">處理中…</string>
    <string name="mic_permission_required">需要麥克風權限。請前往設定授權。</string>
</resources>
```

**Step 3: Create `SettingsScreen.kt`**

```kotlin
package com.voxink.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.voxink.app.R
import com.voxink.app.data.model.RecordingMode
import com.voxink.app.data.model.SttLanguage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var apiKeyInput by remember { mutableStateOf("") }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasMicPermission = granted }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_revert),
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // API Key
            SectionHeader(stringResource(R.string.settings_api_key_section))
            if (state.isApiKeyConfigured) {
                Text(state.apiKeyDisplay, style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text(stringResource(R.string.settings_groq_api_key)) },
                placeholder = { Text(stringResource(R.string.settings_api_key_hint)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    if (apiKeyInput.isNotBlank()) {
                        viewModel.saveApiKey(apiKeyInput)
                        apiKeyInput = ""
                    }
                },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text(stringResource(R.string.settings_save)) }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Language
            SectionHeader(stringResource(R.string.settings_language_section))
            listOf(
                SttLanguage.Auto to stringResource(R.string.lang_auto),
                SttLanguage.Chinese to stringResource(R.string.lang_zh),
                SttLanguage.English to stringResource(R.string.lang_en),
                SttLanguage.Japanese to stringResource(R.string.lang_ja),
            ).forEach { (lang, label) ->
                RadioRow(label, state.language == lang) { viewModel.setLanguage(lang) }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Recording Mode
            SectionHeader(stringResource(R.string.settings_recording_section))
            RadioRow(
                stringResource(R.string.settings_tap_to_toggle),
                state.recordingMode == RecordingMode.TAP_TO_TOGGLE,
            ) { viewModel.setRecordingMode(RecordingMode.TAP_TO_TOGGLE) }
            RadioRow(
                stringResource(R.string.settings_hold_to_record),
                state.recordingMode == RecordingMode.HOLD_TO_RECORD,
            ) { viewModel.setRecordingMode(RecordingMode.HOLD_TO_RECORD) }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Permission
            SectionHeader(stringResource(R.string.settings_permission_section))
            if (hasMicPermission) {
                Text(stringResource(R.string.status_granted), color = MaterialTheme.colorScheme.primary)
            } else {
                Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                    Text(stringResource(R.string.settings_grant_mic))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
```

**Step 4: Update `HomeScreen.kt` — add setup status + navigation**

```kotlin
package com.voxink.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.voxink.app.R
import com.voxink.app.ui.settings.SettingsViewModel

class HomeScreen { companion object }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    onNavigateToSettings: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isKeyboardEnabled = try {
        val imm = context.getSystemService(InputMethodManager::class.java)
        imm.enabledInputMethodList.any { it.packageName == context.packageName }
    } catch (_: Exception) { false }
    val hasMicPerm = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.welcome_message), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.welcome_description), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(32.dp))

            Text(stringResource(R.string.setup_keyboard, if (isKeyboardEnabled) stringResource(R.string.status_enabled) else stringResource(R.string.status_disabled)))
            Text(stringResource(R.string.setup_api_key, if (state.isApiKeyConfigured) stringResource(R.string.status_configured) else stringResource(R.string.status_not_configured)))
            Text(stringResource(R.string.setup_permission, if (hasMicPerm) stringResource(R.string.status_granted) else stringResource(R.string.status_denied)))
            Spacer(Modifier.height(24.dp))

            if (!isKeyboardEnabled) {
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.open_keyboard_settings))
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(onClick = onNavigateToSettings, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.open_settings))
            }
        }
    }
}
```

**Step 5: Update `MainActivity.kt` — add screen navigation**

```kotlin
package com.voxink.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.voxink.app.ui.settings.SettingsScreenContent
import com.voxink.app.ui.theme.VoxInkTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoxInkTheme {
                var showSettings by remember { mutableStateOf(false) }
                if (showSettings) {
                    BackHandler { showSettings = false }
                    SettingsScreenContent(onNavigateBack = { showSettings = false })
                } else {
                    HomeScreenContent(onNavigateToSettings = { showSettings = true })
                }
            }
        }
    }
}
```

**Step 6: Commit**

```bash
git add app/src/main/res/values/ app/src/main/res/values-zh-rTW/ \
  app/src/main/java/com/voxink/app/ui/settings/SettingsScreen.kt \
  app/src/main/java/com/voxink/app/ui/HomeScreen.kt \
  app/src/main/java/com/voxink/app/ui/MainActivity.kt
git commit -m "feat: add Settings UI, HomeScreen status, bilingual strings (en + zh-TW)"
```

---

## Part I: Verify

---

### Task 14: Build, Test, Tag

**Step 1: Run full test suite**

```bash
./gradlew test
```

Expected Phase 1 tests (~51 new):
| Test File | Count |
|-----------|-------|
| `SttLanguageTest` | 5 |
| `ImeUiStateTest` | 4 |
| `WhisperResponseTest` | 3 |
| `GroqApiTest` | 2 |
| `AudioEncoderTest` | 7 |
| `SttRepositoryTest` | 6 |
| `ApiKeyManagerTest` | 7 |
| `PreferencesManagerTest` | 4 |
| `TranscribeAudioUseCaseTest` | 3 |
| `RecordingControllerTest` | 6 |
| `SettingsViewModelTest` | 4 |
| **Phase 1 total** | **~51** |
| **Combined (Phase 0 + 1)** | **~72** |

**Step 2: Run lint**

```bash
./gradlew ktlintCheck detekt
```

Fix if needed: `./gradlew ktlintFormat`

**Step 3: Build debug APK**

```bash
./gradlew clean ktlintCheck detekt test assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

**Step 4: Fix any issues, commit if needed**

```bash
git add -A
git commit -m "fix: resolve build and lint issues from Phase 1"
```

**Step 5: Merge and tag**

```bash
git checkout main
git merge dev --no-ff -m "feat: Phase 1 — Core IME + Groq STT (voice keyboard working)"
git tag v0.1.0 -m "Phase 1 complete: working voice keyboard with Groq Whisper STT"
git checkout dev
```

---

## Phase 1 File Summary

### New Files (18 production, 11 test)

```
app/src/main/java/com/voxink/app/
├── di/AppModule.kt                         # EncryptedSharedPreferences provider
├── di/NetworkModule.kt                     # Retrofit + OkHttp provider
├── data/local/ApiKeyManager.kt             # Encrypted API key storage
├── data/local/PreferencesManager.kt        # DataStore settings
├── data/model/RecordingMode.kt             # TAP_TO_TOGGLE / HOLD_TO_RECORD
├── data/model/SttLanguage.kt               # Auto / Chinese / English / Japanese
├── data/remote/GroqApi.kt                  # Retrofit interface for Whisper
├── data/remote/WhisperResponse.kt          # API response models
├── data/repository/SttRepository.kt        # STT orchestration
├── domain/usecase/TranscribeAudioUseCase.kt # PCM→WAV→API pipeline
├── ime/AudioRecorder.kt                    # Android AudioRecord wrapper
├── ime/ImeUiState.kt                       # Sealed interface for IME states
├── ime/RecordingController.kt              # Recording state machine
├── ime/VoxInkIMEEntryPoint.kt              # Hilt EntryPoint for IME
├── ui/settings/SettingsScreen.kt           # Settings Compose UI
├── ui/settings/SettingsUiState.kt          # Settings state model
├── ui/settings/SettingsViewModel.kt        # Settings logic
└── util/AudioEncoder.kt                    # PCM→WAV encoding
```

### Modified Files
- `gradle/libs.versions.toml` — new deps
- `app/build.gradle.kts` — new deps
- `app/src/main/res/layout/keyboard_view.xml` — candidate bar
- `app/src/main/res/values/strings.xml` — ~30 new strings
- `app/src/main/res/values-zh-rTW/strings.xml` — ~30 new strings
- `app/src/main/java/com/voxink/app/ime/VoxInkIME.kt` — full pipeline
- `app/src/main/java/com/voxink/app/ui/HomeScreen.kt` — setup status
- `app/src/main/java/com/voxink/app/ui/MainActivity.kt` — navigation

## Acceptance Criteria

- [ ] `./gradlew assembleDebug` succeeds
- [ ] `./gradlew test` passes (~72 tests)
- [ ] `./gradlew ktlintCheck detekt` passes
- [ ] APK installs on device/emulator
- [ ] User can enter Groq API key in Settings (encrypted storage)
- [ ] User can select language (Auto / 中文 / English / 日本語)
- [ ] User can select recording mode (Tap / Hold)
- [ ] Mic tap starts recording (red mic button feedback)
- [ ] Second tap stops recording and sends to Groq Whisper API
- [ ] Transcription text appears in candidate bar
- [ ] Tapping candidate text commits it to current app's input field
- [ ] Error states shown (no API key, network error)
- [ ] RECORD_AUDIO permission requested in Settings
- [ ] Bilingual UI (English + Traditional Chinese)
- [ ] HomeScreen shows setup checklist (keyboard, API key, permission)
