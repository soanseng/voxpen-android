# Auto Context-Aware Tone — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Automatically select the appropriate tone style when the user activates VoxPen in a text field, based on the active app's package name and input type, without changing the user's saved preference.

**Architecture:** New `BuiltinAppToneTable` (hardcoded package→tone map) and `AppToneDetector` (detection logic) are introduced. `VoxPenIME` gains an `effectiveTone` field updated in `onStartInput()`. `RecordingController.onStopRecording()` receives a `toneOverride` parameter used instead of the flow-collected `toneStyle` for refinement. `PreferencesManager` gains `autoToneEnabledFlow` and `customAppToneRulesFlow`. `SettingsScreen` gains an Auto Tone section.

**Tech Stack:** Kotlin, DataStore Preferences, `kotlinx.serialization` (JSON), Jetpack Compose, JUnit 5, MockK, Turbine

---

## Reference Files

- `app/src/main/java/com/voxpen/app/data/model/ToneStyle.kt` — sealed class, `ToneStyle.all`, `ToneStyle.fromKey()`
- `app/src/main/java/com/voxpen/app/data/local/PreferencesManager.kt` — DataStore pattern reference
- `app/src/main/java/com/voxpen/app/ime/RecordingController.kt` — how toneStyle is used in refinement
- `app/src/main/java/com/voxpen/app/ime/VoxPenIME.kt` — `onStartInput()` lifecycle hook
- `app/src/main/java/com/voxpen/app/ui/settings/SettingsScreen.kt` — `TranslationSection` as UI pattern reference
- `app/src/main/java/com/voxpen/app/ui/settings/SettingsViewModel.kt` — init/collect/update pattern
- `app/src/main/java/com/voxpen/app/ui/settings/SettingsUiState.kt` — add new fields here

---

## Task 1: BuiltinAppToneTable

**Files:**
- Create: `app/src/main/java/com/voxpen/app/data/model/BuiltinAppToneTable.kt`
- Create: `app/src/test/java/com/voxpen/app/data/model/BuiltinAppToneTableTest.kt`

### Step 1: Write the failing smoke test

```kotlin
// app/src/test/java/com/voxpen/app/data/model/BuiltinAppToneTableTest.kt
package com.voxpen.app.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class BuiltinAppToneTableTest {
    @Test
    fun `all entries map to a valid ToneStyle`() {
        BuiltinAppToneTable.rules.forEach { (pkg, tone) ->
            assertThat(ToneStyle.all).contains(tone)
        }
    }

    @Test
    fun `whatsapp maps to Casual`() {
        assertThat(BuiltinAppToneTable.rules["com.whatsapp"]).isEqualTo(ToneStyle.Casual)
    }

    @Test
    fun `gmail maps to Email`() {
        assertThat(BuiltinAppToneTable.rules["com.google.android.gm"]).isEqualTo(ToneStyle.Email)
    }

    @Test
    fun `slack maps to Professional`() {
        assertThat(BuiltinAppToneTable.rules["com.slack"]).isEqualTo(ToneStyle.Professional)
    }

    @Test
    fun `notion maps to Note`() {
        assertThat(BuiltinAppToneTable.rules["com.notion.id"]).isEqualTo(ToneStyle.Note)
    }

    @Test
    fun `twitter maps to Social`() {
        assertThat(BuiltinAppToneTable.rules["com.twitter.android"]).isEqualTo(ToneStyle.Social)
    }
}
```

### Step 2: Run test to verify it fails

```bash
./gradlew :app:test --tests "com.voxpen.app.data.model.BuiltinAppToneTableTest" -x lint
```
Expected: `FAILED` — `BuiltinAppToneTable` not found.

### Step 3: Implement BuiltinAppToneTable

```kotlin
// app/src/main/java/com/voxpen/app/data/model/BuiltinAppToneTable.kt
package com.voxpen.app.data.model

object BuiltinAppToneTable {
    val rules: Map<String, ToneStyle> = mapOf(
        // Casual — messaging apps
        "com.whatsapp" to ToneStyle.Casual,
        "org.telegram.messenger" to ToneStyle.Casual,
        "com.facebook.orca" to ToneStyle.Casual,
        "jp.naver.line.android" to ToneStyle.Casual,
        "com.discord" to ToneStyle.Casual,
        "com.kakao.talk" to ToneStyle.Casual,
        "com.viber.voip" to ToneStyle.Casual,
        // Email
        "com.google.android.gm" to ToneStyle.Email,
        "com.microsoft.office.outlook" to ToneStyle.Email,
        "me.proton.android.mail" to ToneStyle.Email,
        // Professional — work collaboration
        "com.slack" to ToneStyle.Professional,
        "com.microsoft.teams" to ToneStyle.Professional,
        // Note — personal note-taking
        "com.google.android.keep" to ToneStyle.Note,
        "com.notion.id" to ToneStyle.Note,
        "md.obsidian" to ToneStyle.Note,
        "com.evernote" to ToneStyle.Note,
        // Social — public social media
        "com.twitter.android" to ToneStyle.Social,
        "com.instagram.android" to ToneStyle.Social,
        "com.instagram.threads" to ToneStyle.Social,
        "com.zhiliaoapp.musically" to ToneStyle.Social,
        "com.facebook.katana" to ToneStyle.Social,
        "com.dcard.app" to ToneStyle.Social,
    )
}
```

### Step 4: Run tests to verify they pass

```bash
./gradlew :app:test --tests "com.voxpen.app.data.model.BuiltinAppToneTableTest" -x lint
```
Expected: `BUILD SUCCESSFUL`, all 6 tests pass.

### Step 5: Commit

```bash
git add app/src/main/java/com/voxpen/app/data/model/BuiltinAppToneTable.kt \
        app/src/test/java/com/voxpen/app/data/model/BuiltinAppToneTableTest.kt
git commit -m "feat: add BuiltinAppToneTable with 21 app-to-tone mappings"
```

---

## Task 2: AppToneDetector

**Files:**
- Create: `app/src/main/java/com/voxpen/app/ime/AppToneDetector.kt`
- Create: `app/src/test/java/com/voxpen/app/ime/AppToneDetectorTest.kt`

### Step 1: Write the failing tests

```kotlin
// app/src/test/java/com/voxpen/app/ime/AppToneDetectorTest.kt
package com.voxpen.app.ime

import android.text.InputType
import com.google.common.truth.Truth.assertThat
import com.voxpen.app.data.model.ToneStyle
import org.junit.jupiter.api.Test

class AppToneDetectorTest {
    @Test
    fun `custom rule wins over builtin`() {
        val customRules = mapOf("com.whatsapp" to ToneStyle.Professional)
        val result = AppToneDetector.detect(
            packageName = "com.whatsapp",
            inputType = 0,
            customRules = customRules,
        )
        assertThat(result).isEqualTo(ToneStyle.Professional)
    }

    @Test
    fun `builtin package match returned when no custom rule`() {
        val result = AppToneDetector.detect(
            packageName = "com.slack",
            inputType = 0,
            customRules = emptyMap(),
        )
        assertThat(result).isEqualTo(ToneStyle.Professional)
    }

    @Test
    fun `SHORT_MESSAGE inputType falls back to Casual when no package match`() {
        // TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_SHORT_MESSAGE = 0x00000011
        val shortMessageInputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE
        val result = AppToneDetector.detect(
            packageName = "com.unknown.app",
            inputType = shortMessageInputType,
            customRules = emptyMap(),
        )
        assertThat(result).isEqualTo(ToneStyle.Casual)
    }

    @Test
    fun `unknown app with non-message inputType returns null`() {
        val result = AppToneDetector.detect(
            packageName = "com.unknown.app",
            inputType = InputType.TYPE_CLASS_TEXT,
            customRules = emptyMap(),
        )
        assertThat(result).isNull()
    }

    @Test
    fun `empty package name returns null`() {
        val result = AppToneDetector.detect(
            packageName = "",
            inputType = 0,
            customRules = emptyMap(),
        )
        assertThat(result).isNull()
    }
}
```

### Step 2: Run tests to verify they fail

```bash
./gradlew :app:test --tests "com.voxpen.app.ime.AppToneDetectorTest" -x lint
```
Expected: `FAILED` — `AppToneDetector` not found.

### Step 3: Implement AppToneDetector

```kotlin
// app/src/main/java/com/voxpen/app/ime/AppToneDetector.kt
package com.voxpen.app.ime

import android.text.InputType
import com.voxpen.app.data.model.BuiltinAppToneTable
import com.voxpen.app.data.model.ToneStyle

object AppToneDetector {
    /**
     * Detects the appropriate tone for the given context.
     * Priority: custom rules → builtin table → SHORT_MESSAGE inputType → null
     */
    fun detect(
        packageName: String,
        inputType: Int,
        customRules: Map<String, ToneStyle>,
    ): ToneStyle? {
        if (packageName.isBlank()) return null

        // 1. User custom rules (highest priority)
        customRules[packageName]?.let { return it }

        // 2. Builtin package name table
        BuiltinAppToneTable.rules[packageName]?.let { return it }

        // 3. SHORT_MESSAGE inputType fallback
        val variation = inputType and InputType.TYPE_TEXT_VARIATION_MASK
        if (variation == InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE) {
            return ToneStyle.Casual
        }

        return null
    }
}
```

### Step 4: Run tests to verify they pass

```bash
./gradlew :app:test --tests "com.voxpen.app.ime.AppToneDetectorTest" -x lint
```
Expected: `BUILD SUCCESSFUL`, all 5 tests pass.

### Step 5: Commit

```bash
git add app/src/main/java/com/voxpen/app/ime/AppToneDetector.kt \
        app/src/test/java/com/voxpen/app/ime/AppToneDetectorTest.kt
git commit -m "feat: add AppToneDetector with priority-cascade detection logic"
```

---

## Task 3: PreferencesManager — autoToneEnabled + customAppToneRules

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/data/local/PreferencesManager.kt`
- Modify: `app/src/test/java/com/voxpen/app/data/local/PreferencesManagerTest.kt`

Note: DataStore has no native Map type. Custom rules are stored as a JSON string: `{"com.myapp":"professional"}`.
`ToneStyle` has a `key: String` property and `ToneStyle.fromKey(key)` for deserialization.
Add `kotlinx.serialization` JSON dependency if not already present — check `app/build.gradle.kts` for `kotlinx-serialization-json`.

### Step 1: Check if kotlinx.serialization is already available

```bash
grep -r "serialization" app/build.gradle.kts
```

If missing, add to `app/build.gradle.kts`:
```kotlin
// In plugins block:
id("org.jetbrains.kotlin.plugin.serialization")
// In dependencies block:
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
```
And to root `build.gradle.kts` if needed:
```kotlin
id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0" apply false
```

### Step 2: Write the failing tests

Add to `PreferencesManagerTest.kt` (find the existing test class and add tests):

```kotlin
// Tests to add inside the existing PreferencesManagerTest class:

@Test
fun `autoToneEnabled defaults to true`() = runTest {
    val prefs = createPreferencesManager()
    prefs.autoToneEnabledFlow.test {
        assertThat(awaitItem()).isTrue()
        cancelAndIgnoreRemainingEvents()
    }
}

@Test
fun `setAutoToneEnabled persists false`() = runTest {
    val prefs = createPreferencesManager()
    prefs.setAutoToneEnabled(false)
    prefs.autoToneEnabledFlow.test {
        assertThat(awaitItem()).isFalse()
        cancelAndIgnoreRemainingEvents()
    }
}

@Test
fun `customAppToneRulesFlow defaults to empty map`() = runTest {
    val prefs = createPreferencesManager()
    prefs.customAppToneRulesFlow.test {
        assertThat(awaitItem()).isEmpty()
        cancelAndIgnoreRemainingEvents()
    }
}

@Test
fun `setCustomAppToneRule persists and reads back`() = runTest {
    val prefs = createPreferencesManager()
    prefs.setCustomAppToneRule("com.myapp", ToneStyle.Professional)
    prefs.customAppToneRulesFlow.test {
        val rules = awaitItem()
        assertThat(rules["com.myapp"]).isEqualTo(ToneStyle.Professional)
        cancelAndIgnoreRemainingEvents()
    }
}

@Test
fun `removeCustomAppToneRule removes the entry`() = runTest {
    val prefs = createPreferencesManager()
    prefs.setCustomAppToneRule("com.myapp", ToneStyle.Casual)
    prefs.removeCustomAppToneRule("com.myapp")
    prefs.customAppToneRulesFlow.test {
        assertThat(awaitItem()).doesNotContainKey("com.myapp")
        cancelAndIgnoreRemainingEvents()
    }
}
```

### Step 3: Run tests to verify they fail

```bash
./gradlew :app:test --tests "com.voxpen.app.data.local.PreferencesManagerTest" -x lint
```
Expected: compile error — `autoToneEnabledFlow` not found.

### Step 4: Implement in PreferencesManager

In the `companion object`, add the constants:
```kotlin
const val DEFAULT_AUTO_TONE_ENABLED: Boolean = true
private val AUTO_TONE_ENABLED_KEY = booleanPreferencesKey("auto_tone_enabled")
private val CUSTOM_APP_TONE_RULES_KEY = stringPreferencesKey("custom_app_tone_rules")
```

In the `PreferencesManager` class body, add the flows:
```kotlin
val autoToneEnabledFlow: Flow<Boolean> =
    context.dataStore.data.map { prefs ->
        prefs[AUTO_TONE_ENABLED_KEY] ?: DEFAULT_AUTO_TONE_ENABLED
    }

val customAppToneRulesFlow: Flow<Map<String, ToneStyle>> =
    context.dataStore.data.map { prefs ->
        val json = prefs[CUSTOM_APP_TONE_RULES_KEY] ?: return@map emptyMap()
        try {
            val raw = Json.decodeFromString<Map<String, String>>(json)
            raw.mapValues { (_, key) -> ToneStyle.fromKey(key) ?: ToneStyle.DEFAULT }
        } catch (_: Exception) {
            emptyMap()
        }
    }
```

Add the setter functions:
```kotlin
suspend fun setAutoToneEnabled(enabled: Boolean) {
    context.dataStore.edit { prefs ->
        prefs[AUTO_TONE_ENABLED_KEY] = enabled
    }
}

suspend fun setCustomAppToneRule(packageName: String, tone: ToneStyle) {
    context.dataStore.edit { prefs ->
        val existing = prefs[CUSTOM_APP_TONE_RULES_KEY]
        val raw: MutableMap<String, String> = if (existing != null) {
            try {
                Json.decodeFromString<Map<String, String>>(existing).toMutableMap()
            } catch (_: Exception) {
                mutableMapOf()
            }
        } else {
            mutableMapOf()
        }
        raw[packageName] = tone.key
        prefs[CUSTOM_APP_TONE_RULES_KEY] = Json.encodeToString(raw)
    }
}

suspend fun removeCustomAppToneRule(packageName: String) {
    context.dataStore.edit { prefs ->
        val existing = prefs[CUSTOM_APP_TONE_RULES_KEY] ?: return@edit
        val raw: MutableMap<String, String> = try {
            Json.decodeFromString<Map<String, String>>(existing).toMutableMap()
        } catch (_: Exception) {
            return@edit
        }
        raw.remove(packageName)
        prefs[CUSTOM_APP_TONE_RULES_KEY] = Json.encodeToString(raw)
    }
}
```

Add import at top of file: `import kotlinx.serialization.json.Json` and `import kotlinx.serialization.encodeToString` and `import kotlinx.serialization.decodeFromString`.

### Step 5: Check ToneStyle.fromKey exists

Open `app/src/main/java/com/voxpen/app/data/model/ToneStyle.kt` and verify `fromKey(key: String): ToneStyle?` exists. If not, add it inside the companion object:
```kotlin
fun fromKey(key: String): ToneStyle? = all.find { it.key == key }
```

### Step 6: Run tests to verify they pass

```bash
./gradlew :app:test --tests "com.voxpen.app.data.local.PreferencesManagerTest" -x lint
```
Expected: `BUILD SUCCESSFUL`.

### Step 7: Commit

```bash
git add app/src/main/java/com/voxpen/app/data/local/PreferencesManager.kt \
        app/src/test/java/com/voxpen/app/data/local/PreferencesManagerTest.kt \
        app/src/main/java/com/voxpen/app/data/model/ToneStyle.kt
git commit -m "feat: add autoToneEnabled and customAppToneRules to PreferencesManager"
```

---

## Task 4: RecordingController — toneOverride parameter

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/ime/RecordingController.kt`
- Modify: `app/src/test/java/com/voxpen/app/ime/RecordingControllerTest.kt`

### Step 1: Write the failing test

Add to `RecordingControllerTest.kt`:

```kotlin
@Test
fun `toneOverride is used in refinement instead of flow toneStyle`() = runTest {
    // flow says Casual, override says Professional
    toneStyleFlow.value = ToneStyle.Casual
    val capturedPrompt = mutableListOf<String>()
    // Use coEvery to capture the system prompt sent to LLM
    coEvery { chatCompletionApi.chatCompletion(any(), capture(mutableListOf())) } answers {
        val req = secondArg<com.voxpen.app.data.remote.ChatCompletionRequest>()
        capturedPrompt.add(req.messages.first { it.role == "system" }.content)
        ChatCompletionResponse(choices = listOf(ChatChoice(ChatMessage("assistant", "refined"))))
    }

    controller.uiState.test {
        assertThat(awaitItem()).isEqualTo(ImeUiState.Idle)
        controller.onStartRecording(startRecording)
        assertThat(awaitItem()).isEqualTo(ImeUiState.Recording)
        controller.onStopRecording(stopRecording, SttLanguage.English, toneOverride = ToneStyle.Professional)
        awaitItem() // Processing
        awaitItem() // Refining
        val result = awaitItem()
        assertThat(result).isInstanceOf(ImeUiState.Refined::class.java)
        // Verify the prompt used "professional" tone, not "casual"
        assertThat(capturedPrompt.last()).contains("professional")
        cancelAndIgnoreRemainingEvents()
    }
}
```

Note: This test may need adjustment based on how the LLM mock is currently set up in the test file. The key assertion is that `ToneStyle.Professional`'s prompt characteristics appear, not `Casual`'s.

A simpler alternative — verify via `coVerify` on `refineTextUseCase`:
```kotlin
@Test
fun `toneOverride passed to refineTextUseCase instead of flow value`() = runTest {
    toneStyleFlow.value = ToneStyle.Casual
    // ... setup stubs for transcription success ...
    controller.onStopRecording(stopRecording, SttLanguage.English, toneOverride = ToneStyle.Professional)
    // advance until refine is called
    coVerify {
        refineTextUseCase(
            any(), any(), any(), any(), any(), any(),
            tone = ToneStyle.Professional,  // not Casual
            any(), any(), any(), any(),
        )
    }
}
```

### Step 2: Run test to verify it fails

```bash
./gradlew :app:test --tests "com.voxpen.app.ime.RecordingControllerTest" -x lint
```
Expected: compile error — `toneOverride` parameter not in `onStopRecording`.

### Step 3: Implement toneOverride in RecordingController

In `RecordingController`:

1. Add a `private var capturedTone: ToneStyle? = null` field.

2. Change `onStopRecording` signature to add `toneOverride: ToneStyle? = null`:
```kotlin
fun onStopRecording(
    stopRecording: () -> ByteArray,
    language: SttLanguage,
    editMode: Boolean = false,
    toneOverride: ToneStyle? = null,
) {
```

3. Inside `onStopRecording`, before the coroutine launch, capture the tone:
```kotlin
val effectiveTone = toneOverride ?: toneStyle
```

4. In the `refineTextUseCase(...)` call, replace `toneStyle` with `effectiveTone`:
```kotlin
val refinedResult = refineTextUseCase(
    originalText, language, apiKey, resolvedModel, allVocabulary,
    customPrompt, effectiveTone, llmProvider, customBaseUrl,
    translationEnabled, translationTargetLanguage,
)
```

### Step 4: Run tests to verify they pass

```bash
./gradlew :app:test --tests "com.voxpen.app.ime.RecordingControllerTest" -x lint
```
Expected: `BUILD SUCCESSFUL`.

### Step 5: Commit

```bash
git add app/src/main/java/com/voxpen/app/ime/RecordingController.kt \
        app/src/test/java/com/voxpen/app/ime/RecordingControllerTest.kt
git commit -m "feat: add toneOverride parameter to RecordingController.onStopRecording"
```

---

## Task 5: SettingsViewModel + SettingsUiState — auto tone fields

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/ui/settings/SettingsUiState.kt`
- Modify: `app/src/main/java/com/voxpen/app/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/test/java/com/voxpen/app/ui/settings/SettingsViewModelTest.kt`

### Step 1: Write the failing tests

Add to `SettingsViewModelTest.kt`:

```kotlin
// Add to setUp() mocking section:
// every { preferencesManager.autoToneEnabledFlow } returns flowOf(true)
// every { preferencesManager.customAppToneRulesFlow } returns flowOf(emptyMap())

@Test
fun `autoToneEnabled defaults to true in uiState`() = runTest {
    val viewModel = createViewModel()
    assertThat(viewModel.uiState.value.autoToneEnabled).isTrue()
}

@Test
fun `setAutoToneEnabled delegates to preferencesManager`() = runTest {
    val viewModel = createViewModel()
    viewModel.setAutoToneEnabled(false)
    coVerify { preferencesManager.setAutoToneEnabled(false) }
}

@Test
fun `setCustomAppToneRule delegates to preferencesManager`() = runTest {
    val viewModel = createViewModel()
    viewModel.setCustomAppToneRule("com.myapp", ToneStyle.Professional)
    coVerify { preferencesManager.setCustomAppToneRule("com.myapp", ToneStyle.Professional) }
}

@Test
fun `removeCustomAppToneRule delegates to preferencesManager`() = runTest {
    val viewModel = createViewModel()
    viewModel.removeCustomAppToneRule("com.myapp")
    coVerify { preferencesManager.removeCustomAppToneRule("com.myapp") }
}
```

### Step 2: Run tests to verify they fail

```bash
./gradlew :app:test --tests "com.voxpen.app.ui.settings.SettingsViewModelTest" -x lint
```
Expected: compile error — `autoToneEnabled` not in `SettingsUiState`.

### Step 3: Add fields to SettingsUiState

```kotlin
// Add to data class SettingsUiState:
val autoToneEnabled: Boolean = true,
val customAppToneRules: Map<String, ToneStyle> = emptyMap(),
```

### Step 4: Add to SettingsViewModel

In `init {}`, add collectors alongside the existing ones:
```kotlin
scope.launch {
    preferencesManager.autoToneEnabledFlow.collect { v ->
        _uiState.update { it.copy(autoToneEnabled = v) }
    }
}
scope.launch {
    preferencesManager.customAppToneRulesFlow.collect { v ->
        _uiState.update { it.copy(customAppToneRules = v) }
    }
}
```

Add setter functions:
```kotlin
fun setAutoToneEnabled(enabled: Boolean) {
    viewModelScope.launch { preferencesManager.setAutoToneEnabled(enabled) }
}

fun setCustomAppToneRule(packageName: String, tone: ToneStyle) {
    viewModelScope.launch { preferencesManager.setCustomAppToneRule(packageName, tone) }
}

fun removeCustomAppToneRule(packageName: String) {
    viewModelScope.launch { preferencesManager.removeCustomAppToneRule(packageName) }
}
```

Also add to the `setUp()` mock setup in `SettingsViewModelTest`:
```kotlin
every { preferencesManager.autoToneEnabledFlow } returns flowOf(true)
every { preferencesManager.customAppToneRulesFlow } returns flowOf(emptyMap())
```

### Step 5: Run tests to verify they pass

```bash
./gradlew :app:test --tests "com.voxpen.app.ui.settings.SettingsViewModelTest" -x lint
```
Expected: `BUILD SUCCESSFUL`.

### Step 6: Commit

```bash
git add app/src/main/java/com/voxpen/app/ui/settings/SettingsUiState.kt \
        app/src/main/java/com/voxpen/app/ui/settings/SettingsViewModel.kt \
        app/src/test/java/com/voxpen/app/ui/settings/SettingsViewModelTest.kt
git commit -m "feat: add autoToneEnabled and customAppToneRules to SettingsViewModel and UiState"
```

---

## Task 6: String Resources (en + zh-TW)

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`

### Step 1: Add English strings

Add after the `<!-- Tone styles -->` section in `values/strings.xml`:

```xml
<!-- Auto Tone -->
<string name="settings_auto_tone_section">Auto Tone</string>
<string name="settings_auto_tone_toggle">Auto-detect tone by app</string>
<string name="settings_auto_tone_desc">Automatically adjusts tone based on the active app when you start typing.</string>
<string name="settings_auto_tone_custom_rules">Custom App Rules</string>
<string name="settings_auto_tone_add_rule">Add Rule</string>
<string name="settings_auto_tone_package_hint">Package name (e.g. com.myapp)</string>
<string name="settings_auto_tone_select_tone">Select tone</string>
<string name="settings_auto_tone_rule_added">Rule added</string>
<string name="settings_auto_tone_rule_removed">Rule removed</string>
```

### Step 2: Add Traditional Chinese strings

Add after the corresponding tone section in `values-zh-rTW/strings.xml`:

```xml
<!-- Auto Tone -->
<string name="settings_auto_tone_section">自動語氣</string>
<string name="settings_auto_tone_toggle">依 App 自動偵測語氣</string>
<string name="settings_auto_tone_desc">開始輸入時，自動根據目前的 App 調整語氣風格。</string>
<string name="settings_auto_tone_custom_rules">自訂 App 規則</string>
<string name="settings_auto_tone_add_rule">新增規則</string>
<string name="settings_auto_tone_package_hint">套件名稱（例如 com.myapp）</string>
<string name="settings_auto_tone_select_tone">選擇語氣</string>
<string name="settings_auto_tone_rule_added">規則已新增</string>
<string name="settings_auto_tone_rule_removed">規則已移除</string>
```

### Step 3: Commit

```bash
git add app/src/main/res/values/strings.xml \
        app/src/main/res/values-zh-rTW/strings.xml
git commit -m "feat: add Auto Tone string resources (en + zh-TW)"
```

---

## Task 7: SettingsScreen — Auto Tone UI section

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/ui/settings/SettingsScreen.kt`

This task has no unit tests (Compose UI is not unit-tested in this project — test manually on device or emulator).

### Step 1: Add AutoToneSection composable

Add after the `ToneStyleSection` composable function (around line 770 in the current file):

```kotlin
@Composable
private fun AutoToneSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val ruleAdded = stringResource(R.string.settings_auto_tone_rule_added)
    val ruleRemoved = stringResource(R.string.settings_auto_tone_rule_removed)

    SectionHeader(stringResource(R.string.settings_auto_tone_section))
    Text(
        stringResource(R.string.settings_auto_tone_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.settings_auto_tone_toggle),
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = state.autoToneEnabled,
            onCheckedChange = { viewModel.setAutoToneEnabled(it) },
        )
    }

    if (state.autoToneEnabled) {
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.settings_auto_tone_custom_rules),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showAddDialog = true }) {
                Text(stringResource(R.string.settings_auto_tone_add_rule))
            }
        }
        state.customAppToneRules.forEach { (pkg, tone) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = pkg,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = tone.emoji,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                IconButton(onClick = {
                    viewModel.removeCustomAppToneRule(pkg)
                    scope.launch { snackbarHostState?.showSnackbar(ruleRemoved) }
                }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddAutoToneRuleDialog(
            onConfirm = { pkg, tone ->
                viewModel.setCustomAppToneRule(pkg, tone)
                showAddDialog = false
                scope.launch { snackbarHostState?.showSnackbar(ruleAdded) }
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

@Composable
private fun AddAutoToneRuleDialog(
    onConfirm: (String, ToneStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    var packageName by remember { mutableStateOf("") }
    var selectedTone by remember { mutableStateOf(ToneStyle.Casual) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_auto_tone_add_rule)) },
        text = {
            Column {
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text(stringResource(R.string.settings_auto_tone_package_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.settings_auto_tone_select_tone),
                    style = MaterialTheme.typography.labelMedium,
                )
                val toneLabels = mapOf(
                    ToneStyle.Casual to stringResource(R.string.tone_casual),
                    ToneStyle.Professional to stringResource(R.string.tone_professional),
                    ToneStyle.Email to stringResource(R.string.tone_email),
                    ToneStyle.Note to stringResource(R.string.tone_note),
                    ToneStyle.Social to stringResource(R.string.tone_social),
                    ToneStyle.Custom to stringResource(R.string.tone_custom),
                )
                ToneStyle.all.forEach { tone ->
                    RadioRow(
                        label = "${tone.emoji} ${toneLabels[tone] ?: tone.key}",
                        selected = tone == selectedTone,
                        onClick = { selectedTone = tone },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (packageName.isNotBlank()) onConfirm(packageName.trim(), selectedTone) },
                enabled = packageName.isNotBlank(),
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.transcription_cancel))
            }
        },
    )
}
```

Note: `ToneStyle.emoji` — check whether `ToneStyle` exposes an `emoji` property. If not, create a helper:
```kotlin
val ToneStyle.emoji: String get() = when (this) {
    ToneStyle.Casual -> "💬"
    ToneStyle.Professional -> "💼"
    ToneStyle.Email -> "📧"
    ToneStyle.Note -> "📝"
    ToneStyle.Social -> "📱"
    ToneStyle.Custom -> "⚙"
}
```

Note: Check if `LocalSnackbarHostState` is defined in the project. If the snackbar pattern differs, follow the existing pattern used in other sections (e.g., `CustomPromptSection` for how snackbars are shown).

### Step 2: Wire AutoToneSection into SettingsScreen

In the main `SettingsScreen` composable body, find the `ToneStyleSection` call (around line 131) and add `AutoToneSection` right after it:

```kotlin
ToneStyleSection(
    selectedTone = state.toneStyle,
    onToneSelected = { viewModel.setToneStyle(it) },
)
HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
AutoToneSection(state, viewModel)  // ← add this
HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
```

### Step 3: Build to verify compilation

```bash
./gradlew :app:assembleDebug -x lint
```
Expected: `BUILD SUCCESSFUL`.

### Step 4: Commit

```bash
git add app/src/main/java/com/voxpen/app/ui/settings/SettingsScreen.kt
git commit -m "feat: add Auto Tone section to SettingsScreen with toggle and custom rules"
```

---

## Task 8: VoxPenIME — effectiveTone + onStartInput integration

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/ime/VoxPenIME.kt`

No unit tests for VoxPenIME (IME integration is manual-tested). Verify manually on device.

### Step 1: Read VoxPenIME.kt to find the right insertion points

Before editing, read the file and note:
- Where class fields are declared (add `effectiveTone`, `autoToneEnabled`, `customAppToneRules`)
- Where `onCreateInputView()` or `onCreate()` initializes things (add preference collectors)
- Whether `onStartInput()` exists (add it if not)
- Where `stopRecording()` calls `recordingController.onStopRecording()` (add `toneOverride`)
- Where the tone button emoji is updated (find `toneButton?.text = ...`)

### Step 2: Add fields and collectors

Add fields alongside other private fields:
```kotlin
private var effectiveTone: ToneStyle = ToneStyle.DEFAULT
private var autoToneEnabled: Boolean = PreferencesManager.DEFAULT_AUTO_TONE_ENABLED
private var customAppToneRules: Map<String, ToneStyle> = emptyMap()
```

In `onCreateInputView()` or wherever other preference flows are collected, add:
```kotlin
scope.launch {
    preferencesManager.autoToneEnabledFlow.collect { autoToneEnabled = it }
}
scope.launch {
    preferencesManager.customAppToneRulesFlow.collect { customAppToneRules = it }
}
```

### Step 3: Override onStartInput

Add (or modify) `onStartInput()`:
```kotlin
override fun onStartInput(info: EditorInfo, restarting: Boolean) {
    super.onStartInput(info, restarting)
    if (autoToneEnabled) {
        val detected = AppToneDetector.detect(
            packageName = info.packageName ?: "",
            inputType = info.inputType,
            customRules = customAppToneRules,
        )
        effectiveTone = detected ?: preferencesManager.toneStyleFlow.value
    } else {
        effectiveTone = preferencesManager.toneStyleFlow.value
    }
    updateToneButton()
}
```

Where `updateToneButton()` is the existing function (or inline code) that sets the tone button emoji:
```kotlin
toneButton?.text = effectiveTone.emoji
```
(Check the existing tone button update code and call `effectiveTone.emoji` or the equivalent for the actual field type.)

### Step 4: Connect effectiveTone to toneStyle flow

The IME already collects `toneStyleFlow` to update the tone button. Now that `effectiveTone` is the source of truth when auto-tone is enabled, we need to make sure the tone button also reflects manual changes:

When the user taps the tone button and picks a tone manually, update `effectiveTone`:
```kotlin
// In the tone picker selection callback:
effectiveTone = selectedTone
updateToneButton()
// Do NOT call preferencesManager.setToneStyle() — that remains the user's saved preference
```

Find the existing tone picker callback and replace `preferencesManager.setToneStyle(tone)` with just `effectiveTone = tone; updateToneButton()`. The user's persisted preference is only updated when they change it from the Settings screen (SettingsViewModel), NOT from the keyboard tone picker. This way the auto-detected or manually overridden tone is session-only.

**Wait — read the current code before making this change.** The tone picker in the IME may already call `preferencesManager.setToneStyle()`. If it does, keep it as-is (so the user's preference is updated when they manually pick), but also set `effectiveTone = tone` so the current session uses the manually-chosen tone. The design doc says "when user manually taps tone button, update effectiveTone for this recording only" — but since the user also wants to persist their preference when they manually pick, keep the `preferencesManager.setToneStyle()` call. The key is that `effectiveTone` is used for the recording, not `toneStyle`.

### Step 5: Pass toneOverride to onStopRecording

Find all call sites of `recordingController.onStopRecording(...)` and add `toneOverride = effectiveTone`:
```kotlin
recordingController.onStopRecording(
    stopRecording = { audioRecorder.stopRecording() },
    language = currentLanguage,
    editMode = isEditMode,
    toneOverride = effectiveTone,
)
```

### Step 6: Build and verify

```bash
./gradlew :app:assembleDebug -x lint
```
Expected: `BUILD SUCCESSFUL`.

### Step 7: Commit

```bash
git add app/src/main/java/com/voxpen/app/ime/VoxPenIME.kt
git commit -m "feat: integrate AppToneDetector into VoxPenIME.onStartInput with effectiveTone"
```

---

## Task 9: Run full test suite and verify

### Step 1: Run all tests

```bash
./gradlew :app:test -x lint
```
Expected: `BUILD SUCCESSFUL` — all tests pass.

### Step 2: Fix any failures

If tests fail, read the error output carefully and fix. Common issues:
- Missing mock setup in existing tests for new `autoToneEnabledFlow` / `customAppToneRulesFlow` — add `every { preferencesManager.autoToneEnabledFlow } returns flowOf(true)` to `setUp()` in `RecordingControllerTest` and `SettingsViewModelTest`.
- Missing import for `ToneStyle` in test files.

### Step 3: Commit any fixes

```bash
git add -p  # stage only test fixup changes
git commit -m "test: fix mock setup for autoToneEnabled/customAppToneRules flows"
```

---

## Task 10: Manual verification checklist (on device)

Test these scenarios on a real Android device or emulator with VoxPen keyboard active:

1. **Auto Tone ON (default)** — open WhatsApp, tap a chat input, verify tone button shows 💬
2. **Auto Tone ON** — open Gmail, tap compose, verify tone button shows 📧
3. **Auto Tone ON** — open Slack, tap a message field, verify tone button shows 💼
4. **Auto Tone OFF** — open WhatsApp, verify tone button shows the user's saved preference (not 💬)
5. **Custom rule wins** — add custom rule `com.whatsapp → Professional`, open WhatsApp, verify 💼
6. **Remove custom rule** — remove the rule, open WhatsApp, verify 💬 returns
7. **Add rule dialog** — open Settings → Auto Tone → Add Rule, enter a package name, select a tone, confirm
8. **Manual override** — in WhatsApp (auto-detected Casual), tap tone button and select Professional, record — verify recording uses Professional tone; open another app and come back to WhatsApp — auto-detects Casual again

---

## Summary of New Files

| File | Purpose |
|------|---------|
| `data/model/BuiltinAppToneTable.kt` | Hardcoded app→tone map (21 apps) |
| `ime/AppToneDetector.kt` | Priority-cascade detection: custom → builtin → SHORT_MESSAGE → null |
| `test/.../BuiltinAppToneTableTest.kt` | Smoke tests for all entries |
| `test/.../AppToneDetectorTest.kt` | Unit tests for detection priority |

## Summary of Modified Files

| File | Change |
|------|--------|
| `PreferencesManager.kt` | `autoToneEnabledFlow`, `customAppToneRulesFlow`, 3 setters |
| `SettingsUiState.kt` | `autoToneEnabled`, `customAppToneRules` fields |
| `SettingsViewModel.kt` | collectors + `setAutoToneEnabled`, `setCustomAppToneRule`, `removeCustomAppToneRule` |
| `SettingsScreen.kt` | `AutoToneSection` + `AddAutoToneRuleDialog` composables |
| `RecordingController.kt` | `toneOverride: ToneStyle?` param in `onStopRecording` |
| `VoxPenIME.kt` | `effectiveTone`, `autoToneEnabled`, `customAppToneRules` fields; `onStartInput()`; pass `toneOverride` to controller |
| `strings.xml` (en + zh-TW) | 9 new Auto Tone strings |
