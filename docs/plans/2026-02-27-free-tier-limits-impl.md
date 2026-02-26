# Free Tier Usage Limits Update — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Change free tier limits to 30 voice/10 refinement/2 transcriptions(count-based), add promotional copy in Settings Pro section.

**Architecture:** Modify `UsageLimiter` constants and convert file transcription from duration-based to count-based. Propagate renamed fields through `DailyUsage`, UI state classes, ViewModels, and Compose screens. Add new string resources for the free plan copy.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit 5, Truth assertions

---

### Task 1: Update DailyUsage data class

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/billing/DailyUsage.kt:9`
- Test: `app/src/test/java/com/voxpen/app/billing/DailyUsageTest.kt`

**Step 1: Update the test to use new field name**

In `DailyUsageTest.kt`, replace all occurrences of `fileTranscriptionSeconds` with `fileTranscriptionCount`:

```kotlin
// Line 13: in `default counts should be zero`
assertThat(usage.fileTranscriptionCount).isEqualTo(0)

// Lines 32-34: in `copy should add transcription seconds` — rename test + update logic
@Test
fun `copy should increment transcription count`() {
    val usage = DailyUsage(date = LocalDate.now(), fileTranscriptionCount = 1)
    val updated = usage.copy(fileTranscriptionCount = usage.fileTranscriptionCount + 1)
    assertThat(updated.fileTranscriptionCount).isEqualTo(2)
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.voxpen.app.billing.DailyUsageTest" --info 2>&1 | tail -20`
Expected: FAIL — `fileTranscriptionCount` does not exist on `DailyUsage`

**Step 3: Update DailyUsage.kt**

Change `DailyUsage.kt:9` from:
```kotlin
val fileTranscriptionSeconds: Int = 0,
```
to:
```kotlin
val fileTranscriptionCount: Int = 0,
```

**Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.voxpen.app.billing.DailyUsageTest" --info 2>&1 | tail -20`
Expected: PASS

---

### Task 2: Update UsageLimiter constants and API

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/billing/UsageLimiter.kt`
- Test: `app/src/test/java/com/voxpen/app/billing/UsageLimiterTest.kt`

**Step 1: Update UsageLimiterTest.kt**

Replace the entire test file with:

```kotlin
package com.voxpen.app.billing

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
        assertThat(usage.fileTranscriptionCount).isEqualTo(0)
    }

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
    fun `voice input limit should be 30`() {
        assertThat(UsageLimiter.FREE_VOICE_INPUT_LIMIT).isEqualTo(30)
    }

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
    fun `refinement limit should be 10`() {
        assertThat(UsageLimiter.FREE_REFINEMENT_LIMIT).isEqualTo(10)
    }

    @Test
    fun `canTranscribeFile should return true when under limit`() {
        assertThat(limiter.canTranscribeFile()).isTrue()
    }

    @Test
    fun `canTranscribeFile should return false when at limit`() {
        repeat(UsageLimiter.FREE_FILE_TRANSCRIPTION_LIMIT) { limiter.incrementFileTranscription() }
        assertThat(limiter.canTranscribeFile()).isFalse()
    }

    @Test
    fun `file transcription limit should be 2`() {
        assertThat(UsageLimiter.FREE_FILE_TRANSCRIPTION_LIMIT).isEqualTo(2)
    }

    @Test
    fun `remainingFileTranscriptions should decrease after increment`() {
        assertThat(limiter.remainingFileTranscriptions()).isEqualTo(2)
        limiter.incrementFileTranscription()
        assertThat(limiter.remainingFileTranscriptions()).isEqualTo(1)
    }

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
    fun `incrementFileTranscription should increase count by one`() {
        limiter.incrementFileTranscription()
        assertThat(limiter.currentUsage.fileTranscriptionCount).isEqualTo(1)
    }

    @Test
    fun `resetIfNewDay should reset counts when date changes`() {
        limiter.incrementVoiceInput()
        limiter.incrementRefinement()
        limiter.incrementFileTranscription()
        val tomorrow = LocalDate.now().plusDays(1)
        limiter.resetIfNewDay(tomorrow)
        assertThat(limiter.currentUsage.voiceInputCount).isEqualTo(0)
        assertThat(limiter.currentUsage.refinementCount).isEqualTo(0)
        assertThat(limiter.currentUsage.fileTranscriptionCount).isEqualTo(0)
    }

    @Test
    fun `resetIfNewDay should not reset counts when same day`() {
        limiter.incrementVoiceInput()
        limiter.resetIfNewDay(LocalDate.now())
        assertThat(limiter.currentUsage.voiceInputCount).isEqualTo(1)
    }

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

**Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.voxpen.app.billing.UsageLimiterTest" --info 2>&1 | tail -30`
Expected: FAIL — old methods/constants don't match

**Step 3: Update UsageLimiter.kt**

Replace the entire file with:

```kotlin
package com.voxpen.app.billing

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

        fun canTranscribeFile(): Boolean {
            resetIfNewDay(LocalDate.now())
            return usage.fileTranscriptionCount < FREE_FILE_TRANSCRIPTION_LIMIT
        }

        fun incrementVoiceInput() {
            resetIfNewDay(LocalDate.now())
            usage = usage.copy(voiceInputCount = usage.voiceInputCount + 1)
        }

        fun incrementRefinement() {
            resetIfNewDay(LocalDate.now())
            usage = usage.copy(refinementCount = usage.refinementCount + 1)
        }

        fun incrementFileTranscription() {
            resetIfNewDay(LocalDate.now())
            usage = usage.copy(fileTranscriptionCount = usage.fileTranscriptionCount + 1)
        }

        fun remainingVoiceInputs(): Int {
            resetIfNewDay(LocalDate.now())
            return (FREE_VOICE_INPUT_LIMIT - usage.voiceInputCount).coerceAtLeast(0)
        }

        fun remainingRefinements(): Int {
            resetIfNewDay(LocalDate.now())
            return (FREE_REFINEMENT_LIMIT - usage.refinementCount).coerceAtLeast(0)
        }

        fun remainingFileTranscriptions(): Int {
            resetIfNewDay(LocalDate.now())
            return (FREE_FILE_TRANSCRIPTION_LIMIT - usage.fileTranscriptionCount).coerceAtLeast(0)
        }

        fun resetIfNewDay(today: LocalDate) {
            if (usage.date != today) {
                usage = DailyUsage(date = today)
            }
        }

        companion object {
            const val FREE_VOICE_INPUT_LIMIT = 30
            const val FREE_REFINEMENT_LIMIT = 10
            const val FREE_FILE_TRANSCRIPTION_LIMIT = 2
        }
    }
```

**Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.voxpen.app.billing.*" --info 2>&1 | tail -20`
Expected: PASS

---

### Task 3: Update UI state classes and ViewModels

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/ui/settings/SettingsUiState.kt:27`
- Modify: `app/src/main/java/com/voxpen/app/ui/settings/SettingsViewModel.kt:48,192`
- Modify: `app/src/main/java/com/voxpen/app/ui/transcription/TranscriptionUiState.kt:14-15`
- Modify: `app/src/main/java/com/voxpen/app/ui/transcription/TranscriptionViewModel.kt:41-42,76,86,93-94`

**Step 1: Update SettingsUiState.kt**

Change line 27 from:
```kotlin
val remainingFileTranscriptionSeconds: Int = 0,
```
to:
```kotlin
val remainingFileTranscriptions: Int = 0,
```

**Step 2: Update SettingsViewModel.kt**

Line 48 — change:
```kotlin
remainingFileTranscriptionSeconds = usageLimiter.remainingFileTranscriptionSeconds(),
```
to:
```kotlin
remainingFileTranscriptions = usageLimiter.remainingFileTranscriptions(),
```

Line 192 — change:
```kotlin
remainingFileTranscriptionSeconds = usageLimiter.remainingFileTranscriptionSeconds(),
```
to:
```kotlin
remainingFileTranscriptions = usageLimiter.remainingFileTranscriptions(),
```

**Step 3: Update TranscriptionUiState.kt**

Change line 15 from:
```kotlin
val remainingFileTranscriptionSeconds: Int = 0,
```
to:
```kotlin
val remainingFileTranscriptions: Int = 0,
```

**Step 4: Update TranscriptionViewModel.kt**

Line 41 — change:
```kotlin
canTranscribeFile = status.isPro || usageLimiter.canTranscribeFile(0),
```
to:
```kotlin
canTranscribeFile = status.isPro || usageLimiter.canTranscribeFile(),
```

Line 42 — change:
```kotlin
remainingFileTranscriptionSeconds = usageLimiter.remainingFileTranscriptionSeconds(),
```
to:
```kotlin
remainingFileTranscriptions = usageLimiter.remainingFileTranscriptions(),
```

Line 76 — change:
```kotlin
if (!proStatus.isPro && !usageLimiter.canTranscribeFile(0)) {
```
to:
```kotlin
if (!proStatus.isPro && !usageLimiter.canTranscribeFile()) {
```

Line 86 — change:
```kotlin
usageLimiter.addFileTranscriptionDuration(0)
```
to:
```kotlin
usageLimiter.incrementFileTranscription()
```

Line 93 — change:
```kotlin
canTranscribeFile = proStatus.isPro || usageLimiter.canTranscribeFile(0),
```
to:
```kotlin
canTranscribeFile = proStatus.isPro || usageLimiter.canTranscribeFile(),
```

Line 94 — change:
```kotlin
remainingFileTranscriptionSeconds = usageLimiter.remainingFileTranscriptionSeconds(),
```
to:
```kotlin
remainingFileTranscriptions = usageLimiter.remainingFileTranscriptions(),
```

**Step 5: Run all tests**

Run: `./gradlew testDebugUnitTest --info 2>&1 | tail -30`
Expected: Compilation errors in TranscriptionViewModelTest — fix in Task 4

---

### Task 4: Update TranscriptionViewModelTest

**Files:**
- Modify: `app/src/test/java/com/voxpen/app/ui/transcription/TranscriptionViewModelTest.kt`

**Step 1: Fix test references**

Line 168 — change:
```kotlin
usageLimiter.addFileTranscriptionDuration(UsageLimiter.FREE_FILE_TRANSCRIPTION_DURATION + 1)
```
to:
```kotlin
repeat(UsageLimiter.FREE_FILE_TRANSCRIPTION_LIMIT) { usageLimiter.incrementFileTranscription() }
```

Line 184 — change:
```kotlin
usageLimiter.addFileTranscriptionDuration(UsageLimiter.FREE_FILE_TRANSCRIPTION_DURATION + 1)
```
to:
```kotlin
repeat(UsageLimiter.FREE_FILE_TRANSCRIPTION_LIMIT) { usageLimiter.incrementFileTranscription() }
```

Lines 213-214 — change:
```kotlin
assertThat(state.remainingFileTranscriptionSeconds)
    .isEqualTo(UsageLimiter.FREE_FILE_TRANSCRIPTION_DURATION)
```
to:
```kotlin
assertThat(state.remainingFileTranscriptions)
    .isEqualTo(UsageLimiter.FREE_FILE_TRANSCRIPTION_LIMIT - 1)
```

**Step 2: Run all tests**

Run: `./gradlew testDebugUnitTest --info 2>&1 | tail -30`
Expected: PASS

---

### Task 5: Update UI screens and strings

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/ui/settings/SettingsScreen.kt:194`
- Modify: `app/src/main/java/com/voxpen/app/ui/HomeScreen.kt:144,148,173-179`
- Modify: `app/src/main/java/com/voxpen/app/ui/transcription/TranscriptionScreen.kt:224`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`

**Step 1: Add new string resources to values/strings.xml**

After the existing `usage_transcription_remaining` string (line 141), add:
```xml
<string name="free_plan_description">Free plan — 30 voice inputs, 10 refinements, 2 transcriptions daily. Upgrade to Pro for unlimited use.</string>
```

**Step 2: Add Chinese string to values-zh-rTW/strings.xml**

After the existing `usage_transcription_remaining` string (line 141), add:
```xml
<string name="free_plan_description">Free plan — 每日 30 次語音、10 次潤稿、2 次轉稿。升級 Pro 解鎖無限使用。</string>
```

**Step 3: Update SettingsScreen.kt — Pro section**

In `ProStatusSection`, line 194, change:
```kotlin
stringResource(R.string.usage_transcription_remaining, state.remainingFileTranscriptionSeconds),
```
to:
```kotlin
stringResource(R.string.usage_transcription_remaining, state.remainingFileTranscriptions),
```

Then, after the three usage Text lines (after line 196), add the free plan description:
```kotlin
Spacer(Modifier.height(8.dp))
Text(
    stringResource(R.string.free_plan_description),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)
```

**Step 4: Update HomeScreen.kt — UsageSummaryCard**

Line 144 — change:
```kotlin
val transcribeLimit = UsageLimiter.FREE_FILE_TRANSCRIPTION_DURATION
```
to:
```kotlin
val transcribeLimit = UsageLimiter.FREE_FILE_TRANSCRIPTION_LIMIT
```

Line 148 — change:
```kotlin
val transcribeUsed = transcribeLimit - state.remainingFileTranscriptionSeconds
```
to:
```kotlin
val transcribeUsed = transcribeLimit - state.remainingFileTranscriptions
```

**Step 5: Update TranscriptionScreen.kt — remaining display**

Line 224 — change:
```kotlin
stringResource(R.string.usage_transcription_remaining, state.remainingFileTranscriptionSeconds),
```
to:
```kotlin
stringResource(R.string.usage_transcription_remaining, state.remainingFileTranscriptions),
```

**Step 6: Build to verify**

Run: `./gradlew assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

---

### Task 6: Final verification and commit

**Step 1: Run all tests**

Run: `./gradlew testDebugUnitTest --info 2>&1 | tail -30`
Expected: All tests PASS

**Step 2: Commit**

```bash
git add app/src/main/java/com/voxpen/app/billing/DailyUsage.kt \
       app/src/main/java/com/voxpen/app/billing/UsageLimiter.kt \
       app/src/main/java/com/voxpen/app/ui/settings/SettingsUiState.kt \
       app/src/main/java/com/voxpen/app/ui/settings/SettingsViewModel.kt \
       app/src/main/java/com/voxpen/app/ui/settings/SettingsScreen.kt \
       app/src/main/java/com/voxpen/app/ui/transcription/TranscriptionUiState.kt \
       app/src/main/java/com/voxpen/app/ui/transcription/TranscriptionViewModel.kt \
       app/src/main/java/com/voxpen/app/ui/transcription/TranscriptionScreen.kt \
       app/src/main/java/com/voxpen/app/ui/HomeScreen.kt \
       app/src/main/res/values/strings.xml \
       app/src/main/res/values-zh-rTW/strings.xml \
       app/src/test/java/com/voxpen/app/billing/DailyUsageTest.kt \
       app/src/test/java/com/voxpen/app/billing/UsageLimiterTest.kt \
       app/src/test/java/com/voxpen/app/ui/transcription/TranscriptionViewModelTest.kt
git commit -m "feat: update free tier limits to 30 voice / 10 refine / 2 transcriptions

- Change voice input limit from 15 to 30 per day
- Change refinement limit from 3 to 10 per day
- Change file transcription from duration-based (300s) to count-based (2/day)
- Add free plan description copy in Settings Pro section
- Update all UI state, ViewModels, and tests accordingly"
```
