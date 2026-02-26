# Monetization v2 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Remove all ads, add LemonSqueezy bundle license validation, tighten free tier limits, and introduce a ProStatusResolver to unify dual Pro activation (Google Play + license key).

**Architecture:** A new `LicenseManager` handles LemonSqueezy API calls and caches license state in EncryptedSharedPreferences. A new `ProStatusResolver` combines `BillingManager.proStatus` and `LicenseManager.proStatus` into a single `StateFlow<ProStatus>`. `UsageLimiter` is updated with stricter limits and duration-based file transcription tracking. All ad code is deleted.

**Tech Stack:** Kotlin, Retrofit, Hilt, EncryptedSharedPreferences, Kotlin Coroutines + Flow, JUnit 5 + MockK + Truth + Turbine

**Design doc:** `docs/plans/2026-02-26-monetization-v2-design.md`

---

## Task 1: Update ProStatus to support source tracking

**Files:**
- Modify: `app/src/main/java/com/voxink/app/billing/ProStatus.kt`
- Modify: `app/src/test/java/com/voxink/app/billing/ProStatusTest.kt`

**Step 1: Update test for ProStatus with source**

Replace `app/src/test/java/com/voxink/app/billing/ProStatusTest.kt` entirely:

```kotlin
package com.voxink.app.billing

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ProStatusTest {
    @Test
    fun `Free status should not be pro`() {
        val status: ProStatus = ProStatus.Free
        assertThat(status.isPro).isFalse()
    }

    @Test
    fun `Pro with GOOGLE_PLAY source should be pro`() {
        val status: ProStatus = ProStatus.Pro(ProSource.GOOGLE_PLAY)
        assertThat(status.isPro).isTrue()
    }

    @Test
    fun `Pro with LICENSE_KEY source should be pro`() {
        val status: ProStatus = ProStatus.Pro(ProSource.LICENSE_KEY)
        assertThat(status.isPro).isTrue()
    }

    @Test
    fun `Free should be distinct from Pro`() {
        assertThat(ProStatus.Free).isNotEqualTo(ProStatus.Pro(ProSource.GOOGLE_PLAY))
    }

    @Test
    fun `Pro sources should be distinguishable`() {
        val gp = ProStatus.Pro(ProSource.GOOGLE_PLAY)
        val lk = ProStatus.Pro(ProSource.LICENSE_KEY)
        assertThat(gp).isNotEqualTo(lk)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.voxink.app.billing.ProStatusTest" --no-daemon`
Expected: FAIL — `ProSource` doesn't exist, `Pro` doesn't take a parameter.

**Step 3: Update ProStatus.kt**

Replace `app/src/main/java/com/voxink/app/billing/ProStatus.kt` entirely:

```kotlin
package com.voxink.app.billing

enum class ProSource {
    GOOGLE_PLAY,
    LICENSE_KEY,
}

sealed interface ProStatus {
    data object Free : ProStatus

    data class Pro(val source: ProSource) : ProStatus

    val isPro: Boolean
        get() = this is Pro
}
```

**Step 4: Fix BillingManager compilation**

In `app/src/main/java/com/voxink/app/billing/BillingManager.kt`:
- Line 75: change `ProStatus.Pro` → `ProStatus.Pro(ProSource.GOOGLE_PLAY)`
- Line 137: change `ProStatus.Pro` → `ProStatus.Pro(ProSource.GOOGLE_PLAY)`
- Line 153: change `if (isPro) ProStatus.Pro else ProStatus.Free` → `if (isPro) ProStatus.Pro(ProSource.GOOGLE_PLAY) else ProStatus.Free`

**Step 5: Fix other compilation errors from ProStatus.Pro → ProStatus.Pro(source)**

In `app/src/test/java/com/voxink/app/ime/RecordingControllerTest.kt`:
- Line 234: change `proStatus = ProStatus.Pro` → `proStatus = ProStatus.Pro(ProSource.GOOGLE_PLAY)`

In `app/src/test/java/com/voxink/app/billing/BillingManagerTest.kt`:
- Any reference to `ProStatus.Pro` as an object → `ProStatus.Pro(ProSource.GOOGLE_PLAY)`

**Step 6: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --no-daemon`
Expected: All tests PASS.

**Step 7: Commit**

```bash
git add app/src/main/java/com/voxink/app/billing/ProStatus.kt \
       app/src/main/java/com/voxink/app/billing/BillingManager.kt \
       app/src/test/java/com/voxink/app/billing/ProStatusTest.kt \
       app/src/test/java/com/voxink/app/ime/RecordingControllerTest.kt \
       app/src/test/java/com/voxink/app/billing/BillingManagerTest.kt
git commit -m "refactor: add ProSource enum to ProStatus for dual activation tracking"
```

---

## Task 2: Update DailyUsage and UsageLimiter for new limits + duration tracking

**Files:**
- Modify: `app/src/main/java/com/voxink/app/billing/DailyUsage.kt`
- Modify: `app/src/main/java/com/voxink/app/billing/UsageLimiter.kt`
- Modify: `app/src/test/java/com/voxink/app/billing/DailyUsageTest.kt`
- Modify: `app/src/test/java/com/voxink/app/billing/UsageLimiterTest.kt`

**Step 1: Update DailyUsageTest for duration field**

Replace `app/src/test/java/com/voxink/app/billing/DailyUsageTest.kt` entirely:

```kotlin
package com.voxink.app.billing

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DailyUsageTest {
    @Test
    fun `default counts should be zero`() {
        val usage = DailyUsage(date = LocalDate.of(2026, 2, 24))
        assertThat(usage.voiceInputCount).isEqualTo(0)
        assertThat(usage.refinementCount).isEqualTo(0)
        assertThat(usage.fileTranscriptionSeconds).isEqualTo(0)
    }

    @Test
    fun `should store date correctly`() {
        val date = LocalDate.of(2026, 1, 15)
        val usage = DailyUsage(date = date)
        assertThat(usage.date).isEqualTo(date)
    }

    @Test
    fun `copy should increment counts correctly`() {
        val usage = DailyUsage(date = LocalDate.now(), voiceInputCount = 5)
        val updated = usage.copy(voiceInputCount = usage.voiceInputCount + 1)
        assertThat(updated.voiceInputCount).isEqualTo(6)
    }

    @Test
    fun `copy should add transcription seconds`() {
        val usage = DailyUsage(date = LocalDate.now(), fileTranscriptionSeconds = 60)
        val updated = usage.copy(fileTranscriptionSeconds = usage.fileTranscriptionSeconds + 120)
        assertThat(updated.fileTranscriptionSeconds).isEqualTo(180)
    }
}
```

**Step 2: Update DailyUsage.kt**

Replace `app/src/main/java/com/voxink/app/billing/DailyUsage.kt`:

```kotlin
package com.voxink.app.billing

import java.time.LocalDate

data class DailyUsage(
    val date: LocalDate,
    val voiceInputCount: Int = 0,
    val refinementCount: Int = 0,
    val fileTranscriptionSeconds: Int = 0,
)
```

**Step 3: Update UsageLimiterTest for new limits + duration**

Replace `app/src/test/java/com/voxink/app/billing/UsageLimiterTest.kt` entirely:

```kotlin
package com.voxink.app.billing

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class UsageLimiterTest {
    private lateinit var limiter: UsageLimiter

    @BeforeEach
    fun setUp() {
        limiter = UsageLimiter()
    }

    @Test
    fun `initial usage should have zero counts`() {
        val usage = limiter.currentUsage
        assertThat(usage.voiceInputCount).isEqualTo(0)
        assertThat(usage.refinementCount).isEqualTo(0)
        assertThat(usage.fileTranscriptionSeconds).isEqualTo(0)
    }

    // --- Voice input limits (15/day) ---

    @Test
    fun `canUseVoiceInput should return true when under limit`() {
        assertThat(limiter.canUseVoiceInput()).isTrue()
    }

    @Test
    fun `canUseVoiceInput should return false when at limit`() {
        repeat(UsageLimiter.FREE_VOICE_INPUT_LIMIT) { limiter.incrementVoiceInput() }
        assertThat(limiter.canUseVoiceInput()).isFalse()
    }

    @Test
    fun `voice input limit should be 15`() {
        assertThat(UsageLimiter.FREE_VOICE_INPUT_LIMIT).isEqualTo(15)
    }

    // --- Refinement limits (3/day) ---

    @Test
    fun `canUseRefinement should return true when under limit`() {
        assertThat(limiter.canUseRefinement()).isTrue()
    }

    @Test
    fun `canUseRefinement should return false when at limit`() {
        repeat(UsageLimiter.FREE_REFINEMENT_LIMIT) { limiter.incrementRefinement() }
        assertThat(limiter.canUseRefinement()).isFalse()
    }

    @Test
    fun `refinement limit should be 3`() {
        assertThat(UsageLimiter.FREE_REFINEMENT_LIMIT).isEqualTo(3)
    }

    // --- File transcription duration (300s / 5min per day) ---

    @Test
    fun `canTranscribeFile should return true when under duration limit`() {
        assertThat(limiter.canTranscribeFile(60)).isTrue()
    }

    @Test
    fun `canTranscribeFile should return false when file exceeds remaining duration`() {
        limiter.addFileTranscriptionDuration(250)
        assertThat(limiter.canTranscribeFile(60)).isFalse()
    }

    @Test
    fun `canTranscribeFile should return true when file fits exactly`() {
        limiter.addFileTranscriptionDuration(240)
        assertThat(limiter.canTranscribeFile(60)).isTrue()
    }

    @Test
    fun `file transcription duration limit should be 300 seconds`() {
        assertThat(UsageLimiter.FREE_FILE_TRANSCRIPTION_DURATION).isEqualTo(300)
    }

    @Test
    fun `remainingFileTranscriptionSeconds should decrease after adding duration`() {
        assertThat(limiter.remainingFileTranscriptionSeconds()).isEqualTo(300)
        limiter.addFileTranscriptionDuration(120)
        assertThat(limiter.remainingFileTranscriptionSeconds()).isEqualTo(180)
    }

    // --- Increment methods ---

    @Test
    fun `incrementVoiceInput should increase count by one`() {
        limiter.incrementVoiceInput()
        assertThat(limiter.currentUsage.voiceInputCount).isEqualTo(1)
    }

    @Test
    fun `incrementRefinement should increase count by one`() {
        limiter.incrementRefinement()
        assertThat(limiter.currentUsage.refinementCount).isEqualTo(1)
    }

    @Test
    fun `addFileTranscriptionDuration should add seconds`() {
        limiter.addFileTranscriptionDuration(90)
        assertThat(limiter.currentUsage.fileTranscriptionSeconds).isEqualTo(90)
    }

    // --- Reset ---

    @Test
    fun `resetIfNewDay should reset counts when date changes`() {
        limiter.incrementVoiceInput()
        limiter.incrementRefinement()
        limiter.addFileTranscriptionDuration(120)
        val tomorrow = LocalDate.now().plusDays(1)
        limiter.resetIfNewDay(tomorrow)
        assertThat(limiter.currentUsage.voiceInputCount).isEqualTo(0)
        assertThat(limiter.currentUsage.refinementCount).isEqualTo(0)
        assertThat(limiter.currentUsage.fileTranscriptionSeconds).isEqualTo(0)
    }

    @Test
    fun `resetIfNewDay should not reset counts when same day`() {
        limiter.incrementVoiceInput()
        limiter.resetIfNewDay(LocalDate.now())
        assertThat(limiter.currentUsage.voiceInputCount).isEqualTo(1)
    }

    // --- Remaining balances ---

    @Test
    fun `remainingVoiceInputs should return correct count`() {
        assertThat(limiter.remainingVoiceInputs()).isEqualTo(UsageLimiter.FREE_VOICE_INPUT_LIMIT)
        limiter.incrementVoiceInput()
        assertThat(limiter.remainingVoiceInputs()).isEqualTo(UsageLimiter.FREE_VOICE_INPUT_LIMIT - 1)
    }

    @Test
    fun `remainingRefinements should return correct count`() {
        assertThat(limiter.remainingRefinements()).isEqualTo(UsageLimiter.FREE_REFINEMENT_LIMIT)
    }
}
```

**Step 4: Update UsageLimiter.kt**

Replace `app/src/main/java/com/voxink/app/billing/UsageLimiter.kt` entirely:

```kotlin
package com.voxink.app.billing

import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageLimiter
    @Inject
    constructor() {
        private var usage = DailyUsage(date = LocalDate.now())

        val currentUsage: DailyUsage
            get() {
                resetIfNewDay(LocalDate.now())
                return usage
            }

        fun canUseVoiceInput(): Boolean {
            resetIfNewDay(LocalDate.now())
            return usage.voiceInputCount < FREE_VOICE_INPUT_LIMIT
        }

        fun canUseRefinement(): Boolean {
            resetIfNewDay(LocalDate.now())
            return usage.refinementCount < FREE_REFINEMENT_LIMIT
        }

        fun canTranscribeFile(durationSeconds: Int): Boolean {
            resetIfNewDay(LocalDate.now())
            return usage.fileTranscriptionSeconds + durationSeconds <= FREE_FILE_TRANSCRIPTION_DURATION
        }

        fun incrementVoiceInput() {
            resetIfNewDay(LocalDate.now())
            usage = usage.copy(voiceInputCount = usage.voiceInputCount + 1)
        }

        fun incrementRefinement() {
            resetIfNewDay(LocalDate.now())
            usage = usage.copy(refinementCount = usage.refinementCount + 1)
        }

        fun addFileTranscriptionDuration(seconds: Int) {
            resetIfNewDay(LocalDate.now())
            usage = usage.copy(fileTranscriptionSeconds = usage.fileTranscriptionSeconds + seconds)
        }

        fun remainingVoiceInputs(): Int {
            resetIfNewDay(LocalDate.now())
            return (FREE_VOICE_INPUT_LIMIT - usage.voiceInputCount).coerceAtLeast(0)
        }

        fun remainingRefinements(): Int {
            resetIfNewDay(LocalDate.now())
            return (FREE_REFINEMENT_LIMIT - usage.refinementCount).coerceAtLeast(0)
        }

        fun remainingFileTranscriptionSeconds(): Int {
            resetIfNewDay(LocalDate.now())
            return (FREE_FILE_TRANSCRIPTION_DURATION - usage.fileTranscriptionSeconds).coerceAtLeast(0)
        }

        fun resetIfNewDay(today: LocalDate) {
            if (usage.date != today) {
                usage = DailyUsage(date = today)
            }
        }

        companion object {
            const val FREE_VOICE_INPUT_LIMIT = 15
            const val FREE_REFINEMENT_LIMIT = 3
            const val FREE_FILE_TRANSCRIPTION_DURATION = 300 // 5 minutes in seconds
        }
    }
```

**Step 5: Fix compilation errors from removed methods**

The following references will break — fix them now:

- `RecordingController.kt:50` — `usageLimiter.canUseVoiceInput()` still works (no change)
- `RecordingControllerTest.kt:220,235,251` — `UsageLimiter.FREE_VOICE_INPUT_LIMIT` and `FREE_REFINEMENT_LIMIT` still exist (constants renamed in value only)
- `TranscriptionViewModel.kt:40-41` — `usageLimiter.canUseFileTranscription()` → change to `usageLimiter.canTranscribeFile(0)` temporarily (will be properly fixed in Task 6)
- `TranscriptionViewModel.kt:75` — same `canUseFileTranscription()` → `canTranscribeFile(0)`
- `TranscriptionViewModel.kt:85` — `usageLimiter.incrementFileTranscription()` → remove (will be replaced with duration tracking in Task 6)
- `TranscriptionViewModel.kt:92` — same pattern
- `TranscriptionViewModel.kt:100` — `usageLimiter.addBonusVoiceInputs(...)` → delete (ads being removed)
- `TranscriptionViewModel.kt:104` — `usageLimiter.canUseFileTranscription()` → `canTranscribeFile(0)`
- `TranscriptionViewModel.kt:105` — `usageLimiter.remainingFileTranscriptions()` → `remainingFileTranscriptionSeconds()`
- `TranscriptionUiState.kt:14` — `remainingFileTranscriptions: Int` → `remainingFileTranscriptionSeconds: Int`
- `SettingsViewModel.kt:125` — `usageLimiter.remainingFileTranscriptions()` → `usageLimiter.remainingFileTranscriptionSeconds()`
- `SettingsUiState.kt:19` — `remainingFileTranscriptions: Int` → `remainingFileTranscriptionSeconds: Int`

**NOTE:** Many of these callers will be further cleaned up in later tasks (ad removal, TranscriptionViewModel rewrite). For now, just make compilation pass.

**Step 6: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --no-daemon`
Expected: All tests PASS.

**Step 7: Commit**

```bash
git add app/src/main/java/com/voxink/app/billing/DailyUsage.kt \
       app/src/main/java/com/voxink/app/billing/UsageLimiter.kt \
       app/src/test/java/com/voxink/app/billing/DailyUsageTest.kt \
       app/src/test/java/com/voxink/app/billing/UsageLimiterTest.kt \
       app/src/main/java/com/voxink/app/ui/settings/SettingsViewModel.kt \
       app/src/main/java/com/voxink/app/ui/settings/SettingsUiState.kt \
       app/src/main/java/com/voxink/app/ui/transcription/TranscriptionViewModel.kt \
       app/src/main/java/com/voxink/app/ui/transcription/TranscriptionUiState.kt
git commit -m "refactor: update UsageLimiter with stricter limits and duration-based file tracking

Free tier: 15 voice inputs, 3 refinements, 5min file transcription/day.
File transcription now tracks total seconds instead of file count.
Remove bonus voice input system (ad reward)."
```

---

## Task 3: Add LemonSqueezy API interface and data models

**Files:**
- Create: `app/src/main/java/com/voxink/app/data/remote/LemonSqueezyApi.kt`
- Create: `app/src/test/java/com/voxink/app/data/remote/LemonSqueezyApiTest.kt`

**Step 1: Write the API test**

Create `app/src/test/java/com/voxink/app/data/remote/LemonSqueezyApiTest.kt`:

```kotlin
package com.voxink.app.data.remote

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType

class LemonSqueezyApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: LemonSqueezyApi
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(LemonSqueezyApi::class.java)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `activateLicense should send correct request`() = runTest {
        server.enqueue(MockResponse().setBody("""
            {
                "valid": true,
                "license_key": {
                    "id": 123,
                    "key": "test-key-123",
                    "status": "active"
                },
                "instance": {
                    "id": "inst-abc"
                },
                "meta": {
                    "product_name": "VoxInk Bundle"
                }
            }
        """.trimIndent()))

        val result = api.activateLicense(
            ActivateLicenseRequest(licenseKey = "test-key-123", instanceName = "android-device1")
        )
        assertThat(result.valid).isTrue()
        assertThat(result.instance?.id).isEqualTo("inst-abc")
        assertThat(result.meta?.productName).isEqualTo("VoxInk Bundle")

        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/v1/licenses/activate")
        assertThat(request.method).isEqualTo("POST")
    }

    @Test
    fun `validateLicense should parse valid response`() = runTest {
        server.enqueue(MockResponse().setBody("""
            {
                "valid": true,
                "license_key": {
                    "id": 123,
                    "key": "test-key-123",
                    "status": "active"
                },
                "instance": null,
                "meta": {
                    "product_name": "VoxInk Bundle"
                }
            }
        """.trimIndent()))

        val result = api.validateLicense(
            ValidateLicenseRequest(licenseKey = "test-key-123", instanceId = "inst-abc")
        )
        assertThat(result.valid).isTrue()
    }

    @Test
    fun `validateLicense should parse invalid response`() = runTest {
        server.enqueue(MockResponse().setBody("""
            {
                "valid": false,
                "license_key": {
                    "id": 123,
                    "key": "test-key-123",
                    "status": "disabled"
                },
                "instance": null,
                "meta": null
            }
        """.trimIndent()))

        val result = api.validateLicense(
            ValidateLicenseRequest(licenseKey = "test-key-123", instanceId = "inst-abc")
        )
        assertThat(result.valid).isFalse()
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.voxink.app.data.remote.LemonSqueezyApiTest" --no-daemon`
Expected: FAIL — classes don't exist yet.

**Step 3: Create LemonSqueezyApi.kt**

Create `app/src/main/java/com/voxink/app/data/remote/LemonSqueezyApi.kt`:

```kotlin
package com.voxink.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

interface LemonSqueezyApi {
    @POST("v1/licenses/activate")
    suspend fun activateLicense(
        @Body request: ActivateLicenseRequest,
    ): LicenseResponse

    @POST("v1/licenses/validate")
    suspend fun validateLicense(
        @Body request: ValidateLicenseRequest,
    ): LicenseResponse

    @POST("v1/licenses/deactivate")
    suspend fun deactivateLicense(
        @Body request: DeactivateLicenseRequest,
    ): LicenseResponse
}

@Serializable
data class ActivateLicenseRequest(
    @SerialName("license_key") val licenseKey: String,
    @SerialName("instance_name") val instanceName: String,
)

@Serializable
data class ValidateLicenseRequest(
    @SerialName("license_key") val licenseKey: String,
    @SerialName("instance_id") val instanceId: String,
)

@Serializable
data class DeactivateLicenseRequest(
    @SerialName("license_key") val licenseKey: String,
    @SerialName("instance_id") val instanceId: String,
)

@Serializable
data class LicenseResponse(
    val valid: Boolean,
    @SerialName("license_key") val licenseKey: LicenseKeyInfo? = null,
    val instance: InstanceInfo? = null,
    val meta: LicenseMeta? = null,
)

@Serializable
data class LicenseKeyInfo(
    val id: Long,
    val key: String,
    val status: String,
)

@Serializable
data class InstanceInfo(
    val id: String,
)

@Serializable
data class LicenseMeta(
    @SerialName("product_name") val productName: String? = null,
)
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.voxink.app.data.remote.LemonSqueezyApiTest" --no-daemon`
Expected: All 3 tests PASS.

**Step 5: Commit**

```bash
git add app/src/main/java/com/voxink/app/data/remote/LemonSqueezyApi.kt \
       app/src/test/java/com/voxink/app/data/remote/LemonSqueezyApiTest.kt
git commit -m "feat: add LemonSqueezy license API interface and data models"
```

---

## Task 4: Add LemonSqueezy API to Hilt NetworkModule

**Files:**
- Modify: `app/src/main/java/com/voxink/app/di/NetworkModule.kt`

**Step 1: Add LemonSqueezy API provider to NetworkModule**

Add after the `provideGroqApi` function (after line 62) in `NetworkModule.kt`:

```kotlin
    @Provides
    @Singleton
    @LemonSqueezyRetrofit
    fun provideLemonSqueezyApi(
        client: OkHttpClient,
        json: Json,
    ): LemonSqueezyApi {
        return Retrofit.Builder()
            .baseUrl("https://api.lemonsqueezy.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(LemonSqueezyApi::class.java)
    }
```

Also add the qualifier annotation. Create `app/src/main/java/com/voxink/app/di/Qualifiers.kt`:

```kotlin
package com.voxink.app.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LemonSqueezyRetrofit
```

Add import for `LemonSqueezyApi` in NetworkModule.kt.

**NOTE:** Actually, since `LemonSqueezyApi` is a unique type, Hilt can distinguish it from `GroqApi` without a qualifier. Remove the qualifier — just provide it directly:

```kotlin
    @Provides
    @Singleton
    fun provideLemonSqueezyApi(
        client: OkHttpClient,
        json: Json,
    ): LemonSqueezyApi {
        return Retrofit.Builder()
            .baseUrl("https://api.lemonsqueezy.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(LemonSqueezyApi::class.java)
    }
```

Delete `Qualifiers.kt` if created.

**Step 2: Build to verify compilation**

Run: `./gradlew assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL.

**Step 3: Commit**

```bash
git add app/src/main/java/com/voxink/app/di/NetworkModule.kt
git commit -m "feat: add LemonSqueezy API provider to Hilt NetworkModule"
```

---

## Task 5: Create LicenseManager

**Files:**
- Create: `app/src/main/java/com/voxink/app/billing/LicenseManager.kt`
- Create: `app/src/test/java/com/voxink/app/billing/LicenseManagerTest.kt`

**Step 1: Write LicenseManager tests**

Create `app/src/test/java/com/voxink/app/billing/LicenseManagerTest.kt`:

```kotlin
package com.voxink.app.billing

import android.content.SharedPreferences
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.voxink.app.data.remote.ActivateLicenseRequest
import com.voxink.app.data.remote.InstanceInfo
import com.voxink.app.data.remote.LemonSqueezyApi
import com.voxink.app.data.remote.LicenseKeyInfo
import com.voxink.app.data.remote.LicenseMeta
import com.voxink.app.data.remote.LicenseResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LicenseManagerTest {
    private val api: LemonSqueezyApi = mockk()
    private val prefs: SharedPreferences = mockk(relaxed = true)
    private val editor: SharedPreferences.Editor = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var manager: LicenseManager

    private val validResponse = LicenseResponse(
        valid = true,
        licenseKey = LicenseKeyInfo(id = 1, key = "test-key", status = "active"),
        instance = InstanceInfo(id = "inst-123"),
        meta = LicenseMeta(productName = "VoxInk Bundle"),
    )

    private val invalidResponse = LicenseResponse(
        valid = false,
        licenseKey = LicenseKeyInfo(id = 1, key = "test-key", status = "disabled"),
    )

    @BeforeEach
    fun setUp() {
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.putLong(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        every { editor.apply() } returns Unit
        // Default: no cached license
        every { prefs.getString("license_key", null) } returns null
        every { prefs.getString("license_instance_id", null) } returns null
        every { prefs.getBoolean("license_valid", false) } returns false
        every { prefs.getLong("license_validated_at", 0L) } returns 0L
        every { prefs.getString("license_product_name", null) } returns null

        manager = LicenseManager(api, prefs, "android-test-device", testDispatcher)
    }

    @Test
    fun `initial proStatus should be Free when no cached license`() = runTest {
        manager.proStatus.test {
            assertThat(awaitItem()).isEqualTo(ProStatus.Free)
        }
    }

    @Test
    fun `initial proStatus should be Pro when valid cached license exists`() = runTest {
        every { prefs.getString("license_key", null) } returns "cached-key"
        every { prefs.getBoolean("license_valid", false) } returns true
        val cachedManager = LicenseManager(api, prefs, "android-test-device", testDispatcher)

        cachedManager.proStatus.test {
            assertThat(awaitItem()).isEqualTo(ProStatus.Pro(ProSource.LICENSE_KEY))
        }
    }

    @Test
    fun `activateLicense should set Pro on valid response`() = runTest {
        coEvery { api.activateLicense(any()) } returns validResponse

        manager.proStatus.test {
            assertThat(awaitItem()).isEqualTo(ProStatus.Free)
            val result = manager.activateLicense("test-key")
            assertThat(result.isSuccess).isTrue()
            assertThat(awaitItem()).isEqualTo(ProStatus.Pro(ProSource.LICENSE_KEY))
        }
    }

    @Test
    fun `activateLicense should return failure on invalid response`() = runTest {
        coEvery { api.activateLicense(any()) } returns invalidResponse

        val result = manager.activateLicense("bad-key")
        assertThat(result.isFailure).isTrue()

        manager.proStatus.test {
            assertThat(awaitItem()).isEqualTo(ProStatus.Free)
        }
    }

    @Test
    fun `activateLicense should return failure on network error`() = runTest {
        coEvery { api.activateLicense(any()) } throws IOException("No internet")

        val result = manager.activateLicense("test-key")
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("No internet")
    }

    @Test
    fun `activateLicense should cache license data on success`() = runTest {
        coEvery { api.activateLicense(any()) } returns validResponse

        manager.activateLicense("test-key")

        verify { editor.putString("license_key", "test-key") }
        verify { editor.putString("license_instance_id", "inst-123") }
        verify { editor.putBoolean("license_valid", true) }
        verify { editor.putString("license_product_name", "VoxInk Bundle") }
    }

    @Test
    fun `validateCachedLicense should keep Pro on valid response`() = runTest {
        every { prefs.getString("license_key", null) } returns "cached-key"
        every { prefs.getString("license_instance_id", null) } returns "inst-123"
        every { prefs.getBoolean("license_valid", false) } returns true
        val cachedManager = LicenseManager(api, prefs, "android-test-device", testDispatcher)

        coEvery { api.validateLicense(any()) } returns validResponse

        cachedManager.validateCachedLicense()

        cachedManager.proStatus.test {
            assertThat(awaitItem()).isEqualTo(ProStatus.Pro(ProSource.LICENSE_KEY))
        }
    }

    @Test
    fun `validateCachedLicense should revoke on invalid response`() = runTest {
        every { prefs.getString("license_key", null) } returns "cached-key"
        every { prefs.getString("license_instance_id", null) } returns "inst-123"
        every { prefs.getBoolean("license_valid", false) } returns true
        val cachedManager = LicenseManager(api, prefs, "android-test-device", testDispatcher)

        coEvery { api.validateLicense(any()) } returns invalidResponse

        cachedManager.proStatus.test {
            assertThat(awaitItem()).isEqualTo(ProStatus.Pro(ProSource.LICENSE_KEY))
            cachedManager.validateCachedLicense()
            assertThat(awaitItem()).isEqualTo(ProStatus.Free)
        }

        verify { editor.remove("license_key") }
    }

    @Test
    fun `validateCachedLicense should keep Pro on network error (trust until online)`() = runTest {
        every { prefs.getString("license_key", null) } returns "cached-key"
        every { prefs.getString("license_instance_id", null) } returns "inst-123"
        every { prefs.getBoolean("license_valid", false) } returns true
        val cachedManager = LicenseManager(api, prefs, "android-test-device", testDispatcher)

        coEvery { api.validateLicense(any()) } throws IOException("No internet")

        cachedManager.validateCachedLicense()

        cachedManager.proStatus.test {
            assertThat(awaitItem()).isEqualTo(ProStatus.Pro(ProSource.LICENSE_KEY))
        }
    }

    @Test
    fun `deactivateLicense should clear Pro status`() = runTest {
        coEvery { api.activateLicense(any()) } returns validResponse
        coEvery { api.deactivateLicense(any()) } returns validResponse

        manager.activateLicense("test-key")

        manager.proStatus.test {
            assertThat(awaitItem()).isEqualTo(ProStatus.Pro(ProSource.LICENSE_KEY))
            manager.deactivateLicense()
            assertThat(awaitItem()).isEqualTo(ProStatus.Free)
        }
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.voxink.app.billing.LicenseManagerTest" --no-daemon`
Expected: FAIL — `LicenseManager` doesn't exist.

**Step 3: Create LicenseManager.kt**

Create `app/src/main/java/com/voxink/app/billing/LicenseManager.kt`:

```kotlin
package com.voxink.app.billing

import android.content.SharedPreferences
import com.voxink.app.data.remote.ActivateLicenseRequest
import com.voxink.app.data.remote.DeactivateLicenseRequest
import com.voxink.app.data.remote.LemonSqueezyApi
import com.voxink.app.data.remote.ValidateLicenseRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LicenseManager
    @Inject
    constructor(
        private val api: LemonSqueezyApi,
        private val encryptedPrefs: SharedPreferences,
        private val instanceName: String,
        private val ioDispatcher: CoroutineDispatcher,
    ) {
        private val _proStatus = MutableStateFlow(loadCachedStatus())
        val proStatus: StateFlow<ProStatus> = _proStatus.asStateFlow()

        private fun loadCachedStatus(): ProStatus {
            val key = encryptedPrefs.getString(KEY_LICENSE_KEY, null)
            val valid = encryptedPrefs.getBoolean(KEY_LICENSE_VALID, false)
            return if (key != null && valid) {
                ProStatus.Pro(ProSource.LICENSE_KEY)
            } else {
                ProStatus.Free
            }
        }

        suspend fun activateLicense(licenseKey: String): Result<Unit> =
            withContext(ioDispatcher) {
                try {
                    val response = api.activateLicense(
                        ActivateLicenseRequest(
                            licenseKey = licenseKey,
                            instanceName = instanceName,
                        ),
                    )
                    if (response.valid) {
                        cacheLicense(
                            key = licenseKey,
                            instanceId = response.instance?.id,
                            productName = response.meta?.productName,
                        )
                        _proStatus.value = ProStatus.Pro(ProSource.LICENSE_KEY)
                        Result.success(Unit)
                    } else {
                        Result.failure(LicenseActivationException("Invalid license key"))
                    }
                } catch (e: Exception) {
                    Timber.w(e, "License activation failed")
                    Result.failure(e)
                }
            }

        suspend fun validateCachedLicense() {
            val key = encryptedPrefs.getString(KEY_LICENSE_KEY, null) ?: return
            val instanceId = encryptedPrefs.getString(KEY_INSTANCE_ID, null) ?: return

            withContext(ioDispatcher) {
                try {
                    val response = api.validateLicense(
                        ValidateLicenseRequest(licenseKey = key, instanceId = instanceId),
                    )
                    if (response.valid) {
                        updateValidationTimestamp()
                        Timber.d("License re-validated successfully")
                    } else {
                        Timber.w("License no longer valid, revoking Pro status")
                        clearCachedLicense()
                        _proStatus.value = ProStatus.Free
                    }
                } catch (e: Exception) {
                    // Trust until online — keep Pro on network errors
                    Timber.d(e, "License validation failed (network), keeping Pro status")
                }
            }
        }

        suspend fun deactivateLicense() {
            val key = encryptedPrefs.getString(KEY_LICENSE_KEY, null) ?: return
            val instanceId = encryptedPrefs.getString(KEY_INSTANCE_ID, null) ?: return

            withContext(ioDispatcher) {
                try {
                    api.deactivateLicense(
                        DeactivateLicenseRequest(licenseKey = key, instanceId = instanceId),
                    )
                } catch (e: Exception) {
                    Timber.w(e, "License deactivation API call failed")
                }
            }
            clearCachedLicense()
            _proStatus.value = ProStatus.Free
        }

        private fun cacheLicense(key: String, instanceId: String?, productName: String?) {
            encryptedPrefs.edit()
                .putString(KEY_LICENSE_KEY, key)
                .putString(KEY_INSTANCE_ID, instanceId)
                .putBoolean(KEY_LICENSE_VALID, true)
                .putLong(KEY_VALIDATED_AT, System.currentTimeMillis())
                .putString(KEY_PRODUCT_NAME, productName)
                .apply()
        }

        private fun updateValidationTimestamp() {
            encryptedPrefs.edit()
                .putLong(KEY_VALIDATED_AT, System.currentTimeMillis())
                .apply()
        }

        private fun clearCachedLicense() {
            encryptedPrefs.edit()
                .remove(KEY_LICENSE_KEY)
                .remove(KEY_INSTANCE_ID)
                .remove(KEY_LICENSE_VALID)
                .remove(KEY_VALIDATED_AT)
                .remove(KEY_PRODUCT_NAME)
                .apply()
        }

        companion object {
            const val KEY_LICENSE_KEY = "license_key"
            const val KEY_INSTANCE_ID = "license_instance_id"
            const val KEY_LICENSE_VALID = "license_valid"
            const val KEY_VALIDATED_AT = "license_validated_at"
            const val KEY_PRODUCT_NAME = "license_product_name"
        }
    }

class LicenseActivationException(message: String) : Exception(message)
```

**Step 4: Add Hilt provider for LicenseManager dependencies**

In `app/src/main/java/com/voxink/app/di/AppModule.kt`, add a provider for the `instanceName`:

```kotlin
    @Provides
    @Singleton
    @LicenseInstanceName
    fun provideLicenseInstanceName(@ApplicationContext context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return "android-$androidId"
    }
```

Create a qualifier or use `@Named("licenseInstanceName")`. Simpler approach: use `@Named`:

Add to `AppModule.kt`:

```kotlin
    @Provides
    @Singleton
    @Named("licenseInstanceName")
    fun provideLicenseInstanceName(@ApplicationContext context: Context): String {
        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID,
        )
        return "android-$androidId"
    }

    @Provides
    @Singleton
    @Named("ioDispatcher")
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
```

Update `LicenseManager` constructor to use `@Named`:

```kotlin
    @Inject
    constructor(
        private val api: LemonSqueezyApi,
        private val encryptedPrefs: SharedPreferences,
        @Named("licenseInstanceName") private val instanceName: String,
        @Named("ioDispatcher") private val ioDispatcher: CoroutineDispatcher,
    )
```

**Step 5: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.voxink.app.billing.LicenseManagerTest" --no-daemon`
Expected: All tests PASS.

**Step 6: Commit**

```bash
git add app/src/main/java/com/voxink/app/billing/LicenseManager.kt \
       app/src/test/java/com/voxink/app/billing/LicenseManagerTest.kt \
       app/src/main/java/com/voxink/app/di/AppModule.kt
git commit -m "feat: add LicenseManager for LemonSqueezy license validation and caching"
```

---

## Task 6: Create ProStatusResolver

**Files:**
- Create: `app/src/main/java/com/voxink/app/billing/ProStatusResolver.kt`
- Create: `app/src/test/java/com/voxink/app/billing/ProStatusResolverTest.kt`

**Step 1: Write ProStatusResolver tests**

Create `app/src/test/java/com/voxink/app/billing/ProStatusResolverTest.kt`:

```kotlin
package com.voxink.app.billing

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProStatusResolverTest {
    private val billingFlow = MutableStateFlow<ProStatus>(ProStatus.Free)
    private val licenseFlow = MutableStateFlow<ProStatus>(ProStatus.Free)
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var resolver: ProStatusResolver

    @BeforeEach
    fun setUp() {
        resolver = ProStatusResolver(billingFlow, licenseFlow, testScope)
    }

    @Test
    fun `should be Free when both sources are Free`() = runTest {
        resolver.proStatus.test {
            assertThat(awaitItem()).isEqualTo(ProStatus.Free)
        }
    }

    @Test
    fun `should be Pro GOOGLE_PLAY when billing is Pro`() = runTest {
        billingFlow.value = ProStatus.Pro(ProSource.GOOGLE_PLAY)
        resolver.proStatus.test {
            assertThat(awaitItem()).isEqualTo(ProStatus.Pro(ProSource.GOOGLE_PLAY))
        }
    }

    @Test
    fun `should be Pro LICENSE_KEY when license is Pro`() = runTest {
        licenseFlow.value = ProStatus.Pro(ProSource.LICENSE_KEY)
        resolver.proStatus.test {
            assertThat(awaitItem()).isEqualTo(ProStatus.Pro(ProSource.LICENSE_KEY))
        }
    }

    @Test
    fun `should prefer GOOGLE_PLAY when both are Pro`() = runTest {
        billingFlow.value = ProStatus.Pro(ProSource.GOOGLE_PLAY)
        licenseFlow.value = ProStatus.Pro(ProSource.LICENSE_KEY)
        resolver.proStatus.test {
            assertThat(awaitItem()).isEqualTo(ProStatus.Pro(ProSource.GOOGLE_PLAY))
        }
    }

    @Test
    fun `should revert to Free when Pro source reverts`() = runTest {
        resolver.proStatus.test {
            assertThat(awaitItem()).isEqualTo(ProStatus.Free)
            licenseFlow.value = ProStatus.Pro(ProSource.LICENSE_KEY)
            assertThat(awaitItem()).isEqualTo(ProStatus.Pro(ProSource.LICENSE_KEY))
            licenseFlow.value = ProStatus.Free
            assertThat(awaitItem()).isEqualTo(ProStatus.Free)
        }
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.voxink.app.billing.ProStatusResolverTest" --no-daemon`
Expected: FAIL — `ProStatusResolver` doesn't exist.

**Step 3: Create ProStatusResolver.kt**

Create `app/src/main/java/com/voxink/app/billing/ProStatusResolver.kt`:

```kotlin
package com.voxink.app.billing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProStatusResolver
    @Inject
    constructor(
        billingStatusFlow: StateFlow<ProStatus>,
        licenseStatusFlow: StateFlow<ProStatus>,
        scope: CoroutineScope,
    ) {
        val proStatus: StateFlow<ProStatus> =
            combine(billingStatusFlow, licenseStatusFlow) { billing, license ->
                when {
                    billing.isPro -> billing
                    license.isPro -> license
                    else -> ProStatus.Free
                }
            }.stateIn(scope, SharingStarted.Eagerly, ProStatus.Free)
    }
```

Also add Hilt wiring. In `AppModule.kt`, add providers to bridge the manager flows:

```kotlin
    @Provides
    @Singleton
    fun provideProStatusResolver(
        billingManager: BillingManager,
        licenseManager: LicenseManager,
        @ApplicationScope scope: CoroutineScope,
    ): ProStatusResolver {
        return ProStatusResolver(
            billingStatusFlow = billingManager.proStatus,
            licenseStatusFlow = licenseManager.proStatus,
            scope = scope,
        )
    }

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
```

Create the `@ApplicationScope` qualifier:

```kotlin
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
```

(Can be placed at the bottom of `AppModule.kt` or in a separate file.)

**Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.voxink.app.billing.ProStatusResolverTest" --no-daemon`
Expected: All 5 tests PASS.

**Step 5: Commit**

```bash
git add app/src/main/java/com/voxink/app/billing/ProStatusResolver.kt \
       app/src/test/java/com/voxink/app/billing/ProStatusResolverTest.kt \
       app/src/main/java/com/voxink/app/di/AppModule.kt
git commit -m "feat: add ProStatusResolver combining Google Play and LemonSqueezy status"
```

---

## Task 7: Delete all ad code

**Files:**
- Delete: `app/src/main/java/com/voxink/app/ads/AdManager.kt`
- Delete: `app/src/main/java/com/voxink/app/ads/RewardedAdLoader.kt`
- Delete: `app/src/main/java/com/voxink/app/ads/InterstitialAdLoader.kt`
- Delete: `app/src/main/java/com/voxink/app/ads/BannerAdView.kt`
- Delete: `app/src/test/java/com/voxink/app/ads/AdManagerTest.kt`
- Delete: `app/src/test/java/com/voxink/app/ads/RewardedAdLoaderTest.kt`
- Delete: `app/src/test/java/com/voxink/app/ads/InterstitialAdLoaderTest.kt`
- Modify: `app/build.gradle.kts` — remove `play-services-ads` and `ump` dependencies
- Modify: `gradle/libs.versions.toml` — remove `playServicesAds` and `ump` entries
- Modify: `app/src/main/AndroidManifest.xml` — remove AD_ID permission and AdMob meta-data
- Modify: `app/src/main/java/com/voxink/app/VoxInkApplication.kt` — remove AdManager injection and initialization

**Step 1: Delete ad source files and test files**

```bash
rm app/src/main/java/com/voxink/app/ads/AdManager.kt
rm app/src/main/java/com/voxink/app/ads/RewardedAdLoader.kt
rm app/src/main/java/com/voxink/app/ads/InterstitialAdLoader.kt
rm app/src/main/java/com/voxink/app/ads/BannerAdView.kt
rm app/src/test/java/com/voxink/app/ads/AdManagerTest.kt
rm app/src/test/java/com/voxink/app/ads/RewardedAdLoaderTest.kt
rm app/src/test/java/com/voxink/app/ads/InterstitialAdLoaderTest.kt
rmdir app/src/main/java/com/voxink/app/ads/
rmdir app/src/test/java/com/voxink/app/ads/
```

**Step 2: Remove AdMob dependencies from build.gradle.kts**

In `app/build.gradle.kts`, remove lines 123-124:

```
    implementation(libs.play.services.ads)
    implementation(libs.ump)
```

Keep `implementation(libs.billing)` (line 122).

Change the comment on line 121 from `// Billing & Ads` to `// Billing`.

**Step 3: Remove from libs.versions.toml**

In `gradle/libs.versions.toml`:
- Remove line 39: `playServicesAds = "23.5.0"`
- Remove line 40: `ump = "3.1.0"`
- Remove lines 107-108: `play-services-ads` and `ump` library entries
- Update section comment from `# Billing & Ads` to `# Billing`

**Step 4: Clean up AndroidManifest.xml**

In `app/src/main/AndroidManifest.xml`:
- Remove line 7: `<uses-permission android:name="com.google.android.gms.permission.AD_ID" />`
- Remove lines 17-20 (the AdMob meta-data block):
  ```xml
  <!-- AdMob Application ID (test ID for development) -->
  <meta-data
      android:name="com.google.android.gms.ads.APPLICATION_ID"
      android:value="ca-app-pub-3940256099942544~3347511713" />
  ```

**Step 5: Clean up VoxInkApplication.kt**

Remove AdManager import and usage. Updated file:

```kotlin
package com.voxink.app

import android.app.Application
import com.voxink.app.billing.BillingManager
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class VoxInkApplication : Application() {
    @Inject lateinit var billingManager: BillingManager

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        billingManager.initialize()
    }
}
```

**Step 6: Build to verify no remaining ad references (will fail — fix in next steps)**

Run: `./gradlew assembleDebug --no-daemon 2>&1 | head -50`
Expected: Compilation errors in files that still import ad classes. Note which files fail.

**Step 7: Do NOT commit yet — continue to Task 8 to fix remaining references**

---

## Task 8: Clean up all ad references from UI and DI

**Files:**
- Modify: `app/src/main/java/com/voxink/app/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/voxink/app/ui/settings/SettingsUiState.kt`
- Modify: `app/src/main/java/com/voxink/app/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/voxink/app/ui/transcription/TranscriptionViewModel.kt`
- Modify: `app/src/main/java/com/voxink/app/ui/transcription/TranscriptionUiState.kt`
- Modify: `app/src/main/java/com/voxink/app/ui/transcription/TranscriptionScreen.kt`
- Modify: `app/src/main/java/com/voxink/app/ui/transcription/TranscriptionEntryPoint.kt`
- Modify: `app/src/main/java/com/voxink/app/ime/VoxInkIMEEntryPoint.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`

**Step 1: SettingsViewModel — remove ad code, wire ProStatusResolver**

In `app/src/main/java/com/voxink/app/ui/settings/SettingsViewModel.kt`:

- Remove import: `com.voxink.app.ads.RewardedAdLoader`
- Remove constructor param: `private val rewardedAdLoader: RewardedAdLoader`
- Replace `private val billingManager: BillingManager` with `private val proStatusResolver: ProStatusResolver` (keep `billingManager` if purchase flow is initiated here, or add both)
- Actually: keep `billingManager` for purchase/restore, add `proStatusResolver` for status.
- Change line 75: `billingManager.proStatus.collect` → `proStatusResolver.proStatus.collect`
- Change line 116: `billingManager.proStatus.value` → `proStatusResolver.proStatus.value`
- Delete `watchRewardedAd()` method (lines 130-148)
- Delete `clearAdError()` method (lines 150-152)
- Add imports for `ProStatusResolver`, `LicenseManager`
- Add `private val licenseManager: LicenseManager` to constructor (for license activation from settings)
- Add license activation methods:

```kotlin
    fun activateLicense(key: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isActivatingLicense = true, licenseError = null) }
            val result = licenseManager.activateLicense(key)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isActivatingLicense = false) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isActivatingLicense = false, licenseError = error.message)
                    }
                },
            )
        }
    }

    fun deactivateLicense() {
        viewModelScope.launch {
            licenseManager.deactivateLicense()
        }
    }

    fun clearLicenseError() {
        _uiState.update { it.copy(licenseError = null) }
    }
```

**Step 2: SettingsUiState — remove ad fields, add license fields**

Replace `app/src/main/java/com/voxink/app/ui/settings/SettingsUiState.kt`:

```kotlin
package com.voxink.app.ui.settings

import com.voxink.app.billing.ProStatus
import com.voxink.app.data.local.PreferencesManager
import com.voxink.app.data.model.RecordingMode
import com.voxink.app.data.model.SttLanguage

data class SettingsUiState(
    val isApiKeyConfigured: Boolean = false,
    val apiKeyDisplay: String = "",
    val language: SttLanguage = SttLanguage.Auto,
    val recordingMode: RecordingMode = RecordingMode.TAP_TO_TOGGLE,
    val refinementEnabled: Boolean = true,
    val sttModel: String = PreferencesManager.DEFAULT_STT_MODEL,
    val llmModel: String = PreferencesManager.DEFAULT_LLM_MODEL,
    val proStatus: ProStatus = ProStatus.Free,
    val remainingVoiceInputs: Int = 0,
    val remainingRefinements: Int = 0,
    val remainingFileTranscriptionSeconds: Int = 0,
    val isActivatingLicense: Boolean = false,
    val licenseError: String? = null,
    val customPrompt: String? = null,
    val customPromptDraft: String = "",
    val promptSnackbar: String? = null,
)
```

**Step 3: TranscriptionViewModel — remove ad code, wire ProStatusResolver**

In `app/src/main/java/com/voxink/app/ui/transcription/TranscriptionViewModel.kt`:
- Replace `billingManager: BillingManager` with `proStatusResolver: ProStatusResolver` in constructor
- Replace all `billingManager.proStatus` with `proStatusResolver.proStatus`
- Delete `onRewardedAdWatched()` method
- Delete `onInterstitialShown()` method
- Remove `showRewardedAdPrompt` and `showInterstitialAfterTranscription` from UI state updates
- Change file transcription limit check from count-based to duration-based (will need file duration param)

**Step 4: TranscriptionUiState — remove ad fields**

Replace `app/src/main/java/com/voxink/app/ui/transcription/TranscriptionUiState.kt`:

```kotlin
package com.voxink.app.ui.transcription

import com.voxink.app.billing.ProStatus
import com.voxink.app.data.local.TranscriptionEntity

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
)
```

**Step 5: TranscriptionEntryPoint — remove ad loaders**

Replace `app/src/main/java/com/voxink/app/ui/transcription/TranscriptionEntryPoint.kt`:

```kotlin
package com.voxink.app.ui.transcription

import com.voxink.app.data.local.ApiKeyManager
import com.voxink.app.domain.usecase.TranscribeFileUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TranscriptionEntryPoint {
    fun transcribeFileUseCase(): TranscribeFileUseCase

    fun apiKeyManager(): ApiKeyManager
}
```

**Step 6: VoxInkIMEEntryPoint — remove ad loader, add ProStatusResolver**

Replace `app/src/main/java/com/voxink/app/ime/VoxInkIMEEntryPoint.kt`:

```kotlin
package com.voxink.app.ime

import com.voxink.app.billing.ProStatusResolver
import com.voxink.app.billing.UsageLimiter
import com.voxink.app.data.local.ApiKeyManager
import com.voxink.app.data.local.PreferencesManager
import com.voxink.app.data.repository.DictionaryRepository
import com.voxink.app.domain.usecase.RefineTextUseCase
import com.voxink.app.domain.usecase.TranscribeAudioUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface VoxInkIMEEntryPoint {
    fun transcribeAudioUseCase(): TranscribeAudioUseCase

    fun refineTextUseCase(): RefineTextUseCase

    fun apiKeyManager(): ApiKeyManager

    fun preferencesManager(): PreferencesManager

    fun dictionaryRepository(): DictionaryRepository

    fun usageLimiter(): UsageLimiter

    fun proStatusResolver(): ProStatusResolver
}
```

**Step 7: Update VoxInkIME to use ProStatusResolver**

Wherever `VoxInkIME` accesses `billingManager()` from the entry point for ProStatus, change to `proStatusResolver()`. The `RecordingController` already uses `proStatusProvider: () -> ProStatus` — change the lambda to read from `proStatusResolver().proStatus.value`.

**Step 8: Clean up SettingsScreen.kt and TranscriptionScreen.kt**

These are large Compose files. Key changes:
- **SettingsScreen.kt**: Remove `BannerAdView()` calls, remove rewarded ad button, add license key activation dialog, add countdown to midnight display, update usage display to show seconds for file transcription.
- **TranscriptionScreen.kt**: Remove rewarded ad dialog, remove interstitial ad logic, add upgrade prompt dialog instead.

These UI changes are substantial — the implementer should grep for all ad references and remove them, then add the new license activation UI. The exact Compose code is best written during implementation after seeing the full SettingsScreen.kt context.

**Step 9: Remove ad-related strings from strings.xml files**

In `app/src/main/res/values/strings.xml` — remove strings with keys containing `ad_reward`, `ad_loading`, `ad_not_available`, `ad_error`.

In `app/src/main/res/values-zh-rTW/strings.xml` — remove the same keys.

Add new strings for license activation:

English (`values/strings.xml`):
```xml
<string name="license_activate_title">Activate License Key</string>
<string name="license_activate_hint">Enter your VoxInk Bundle key</string>
<string name="license_activate_button">Activate</string>
<string name="license_deactivate_button">Deactivate License</string>
<string name="license_activating">Activating…</string>
<string name="license_error_invalid">Invalid license key</string>
<string name="license_error_network">Network error. Please check your connection.</string>
<string name="license_status_active">Pro (License)</string>
<string name="upgrade_prompt_title">Daily limit reached</string>
<string name="upgrade_prompt_voice">You\'ve used %1$d/%2$d voice inputs</string>
<string name="upgrade_prompt_refinement">You\'ve used %1$d/%2$d refinements</string>
<string name="upgrade_prompt_file">You\'ve used %1$s/%2$s of file transcription time</string>
<string name="upgrade_prompt_reset">Resets in %1$s</string>
<string name="upgrade_prompt_button">Upgrade to Pro</string>
<string name="upgrade_prompt_license">I have a license key</string>
```

Traditional Chinese (`values-zh-rTW/strings.xml`):
```xml
<string name="license_activate_title">啟用授權碼</string>
<string name="license_activate_hint">輸入你的 VoxInk Bundle 授權碼</string>
<string name="license_activate_button">啟用</string>
<string name="license_deactivate_button">停用授權</string>
<string name="license_activating">啟用中…</string>
<string name="license_error_invalid">無效的授權碼</string>
<string name="license_error_network">網路錯誤，請檢查連線</string>
<string name="license_status_active">Pro（授權碼）</string>
<string name="upgrade_prompt_title">已達每日上限</string>
<string name="upgrade_prompt_voice">已使用 %1$d/%2$d 次語音輸入</string>
<string name="upgrade_prompt_refinement">已使用 %1$d/%2$d 次潤飾</string>
<string name="upgrade_prompt_file">已使用 %1$s/%2$s 的轉錄時間</string>
<string name="upgrade_prompt_reset">%1$s 後重置</string>
<string name="upgrade_prompt_button">升級至 Pro</string>
<string name="upgrade_prompt_license">我有授權碼</string>
```

**Step 10: Build and run all tests**

Run: `./gradlew assembleDebug --no-daemon && ./gradlew testDebugUnitTest --no-daemon`
Expected: BUILD SUCCESSFUL, all tests PASS.

**Step 11: Commit**

```bash
git add -A
git commit -m "feat: remove all ads, wire ProStatusResolver, add license activation UI

- Delete ads/ directory and all ad code
- Remove AdMob dependency, AD_ID permission, and manifest metadata
- Update SettingsViewModel with license activation methods
- Update TranscriptionViewModel to use ProStatusResolver
- Update IME entry points to use ProStatusResolver
- Add license activation dialog strings (en + zh-TW)
- Remove ad-related strings"
```

---

## Task 9: Add LicenseManager re-validation on app foreground

**Files:**
- Modify: `app/src/main/java/com/voxink/app/VoxInkApplication.kt`

**Step 1: Add lifecycle observer for foreground re-validation**

Update `VoxInkApplication.kt` to observe `ProcessLifecycleOwner` and trigger license re-validation when app comes to foreground:

```kotlin
package com.voxink.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.voxink.app.billing.BillingManager
import com.voxink.app.billing.LicenseManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

@HiltAndroidApp
class VoxInkApplication : Application() {
    @Inject lateinit var billingManager: BillingManager
    @Inject lateinit var licenseManager: LicenseManager
    @Inject @Named("applicationScope") lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        billingManager.initialize()

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    applicationScope.launch {
                        licenseManager.validateCachedLicense()
                    }
                }
            },
        )
    }
}
```

**NOTE:** Ensure `androidx.lifecycle:lifecycle-process` dependency is available. Check `build.gradle.kts` and add if needed:

```kotlin
implementation("androidx.lifecycle:lifecycle-process:2.8.7")
```

**Step 2: Build to verify**

Run: `./gradlew assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL.

**Step 3: Commit**

```bash
git add app/src/main/java/com/voxink/app/VoxInkApplication.kt \
       app/build.gradle.kts
git commit -m "feat: add license re-validation on app foreground via ProcessLifecycleOwner"
```

---

## Task 10: Update RecordingController to use ProStatusResolver pattern

**Files:**
- Modify: `app/src/main/java/com/voxink/app/ime/RecordingController.kt` (only if IME wiring changed)
- Modify: `app/src/test/java/com/voxink/app/ime/RecordingControllerTest.kt`

`RecordingController` takes `proStatusProvider: () -> ProStatus` which is already abstract. The IME wiring change (Task 8 Step 7) passes `proStatusResolver().proStatus.value` — so the controller itself doesn't change, only its test might need updating for the new constant values.

**Step 1: Update RecordingControllerTest for new limits**

The test references `UsageLimiter.FREE_VOICE_INPUT_LIMIT` (was 20, now 15) and `FREE_REFINEMENT_LIMIT` (was 5, now 3). Since the test uses the constants directly, it should still pass. But verify:

Run: `./gradlew testDebugUnitTest --tests "com.voxink.app.ime.RecordingControllerTest" --no-daemon`
Expected: All tests PASS.

**Step 2: Commit if any changes were needed**

---

## Task 11: Final integration test — full build + all tests

**Step 1: Clean build**

Run: `./gradlew clean assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL.

**Step 2: Run all unit tests**

Run: `./gradlew testDebugUnitTest --no-daemon`
Expected: All tests PASS.

**Step 3: Verify no ad references remain**

Run: `grep -r "AdManager\|RewardedAd\|InterstitialAd\|BannerAd\|play.services.ads\|AD_ID\|adManager\|rewardedAd\|interstitialAd" app/src/main/ --include="*.kt" --include="*.xml" --include="*.kts"`
Expected: No output (zero matches).

**Step 4: Verify no ad references in test files**

Run: `grep -r "AdManager\|RewardedAd\|InterstitialAd\|BannerAd" app/src/test/ --include="*.kt"`
Expected: No output.

**Step 5: Commit any remaining fixes**

If all clean, no commit needed. Otherwise fix and commit.

---

## Summary of Changes

| Category | Action |
|----------|--------|
| **Deleted** | All 4 ad files + 3 ad test files + ads/ directory |
| **Created** | `LemonSqueezyApi.kt`, `LicenseManager.kt`, `ProStatusResolver.kt` + tests |
| **Modified** | `ProStatus.kt`, `DailyUsage.kt`, `UsageLimiter.kt`, `BillingManager.kt` |
| **Modified** | `SettingsViewModel.kt`, `SettingsUiState.kt`, `SettingsScreen.kt` |
| **Modified** | `TranscriptionViewModel.kt`, `TranscriptionUiState.kt`, `TranscriptionScreen.kt` |
| **Modified** | `VoxInkApplication.kt`, `VoxInkIMEEntryPoint.kt`, `TranscriptionEntryPoint.kt` |
| **Modified** | `AndroidManifest.xml`, `build.gradle.kts`, `libs.versions.toml` |
| **Modified** | `strings.xml` (en + zh-TW) |

## Execution Order

Tasks 1-6 are foundational (model + tests). Task 7-8 are the big ad removal + UI wiring. Task 9-10 are polish. Task 11 is final verification. Tasks are designed to be executed sequentially — each builds on the previous.
