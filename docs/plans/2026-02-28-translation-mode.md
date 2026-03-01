# Translation Mode Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Let users speak in one language and receive output translated into another (e.g., speak Chinese → get English text).

**Architecture:** Translation reuses the existing LLM refinement pipeline. When translation mode is ON, `LlmRepository.refine()` swaps its system prompt from `RefinementPrompt` to a new `TranslationPrompt`. Two new DataStore fields (`translationEnabled`, `translationTargetLanguage`) flow from `PreferencesManager` → `RecordingController` → `RefineTextUseCase` → `LlmRepository`. Settings UI gains a toggle + target language picker.

**Tech Stack:** Kotlin, DataStore Preferences, JUnit 5, MockK, Truth assertions

---

## Key Files

| File | Role |
|------|------|
| `app/src/main/java/com/voxpen/app/data/model/TranslationPrompt.kt` | NEW — translation system prompts |
| `app/src/main/java/com/voxpen/app/data/local/PreferencesManager.kt` | Add 2 new DataStore fields |
| `app/src/main/java/com/voxpen/app/data/repository/LlmRepository.kt` | Add `translationEnabled` + `targetLanguage` params |
| `app/src/main/java/com/voxpen/app/domain/usecase/RefineTextUseCase.kt` | Thread new params through |
| `app/src/main/java/com/voxpen/app/ime/RecordingController.kt` | Read translation prefs, pass to use case |
| `app/src/main/java/com/voxpen/app/ui/settings/SettingsUiState.kt` | Add translation fields |
| `app/src/main/java/com/voxpen/app/ui/settings/SettingsViewModel.kt` | Collect + expose translation prefs |
| `app/src/main/java/com/voxpen/app/ui/settings/SettingsScreen.kt` | Toggle + target language picker UI |
| `app/src/main/res/values/strings.xml` | New UI strings |
| `app/src/main/res/values-zh-rTW/strings.xml` | Traditional Chinese strings |

---

## Task 1: Create `TranslationPrompt`

**Files:**
- Create: `app/src/main/java/com/voxpen/app/data/model/TranslationPrompt.kt`
- Create: `app/src/test/java/com/voxpen/app/data/model/TranslationPromptTest.kt`

### Step 1: Write the failing tests

```kotlin
// app/src/test/java/com/voxpen/app/data/model/TranslationPromptTest.kt
package com.voxpen.app.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TranslationPromptTest {
    @Test
    fun `should contain translation instruction for zh to en`() {
        val prompt = TranslationPrompt.build(SttLanguage.Chinese, SttLanguage.English)
        assertThat(prompt).contains("translat")
        assertThat(prompt).contains("English")
    }

    @Test
    fun `should contain translation instruction for en to zh`() {
        val prompt = TranslationPrompt.build(SttLanguage.English, SttLanguage.Chinese)
        assertThat(prompt).contains("繁體中文")
    }

    @Test
    fun `should contain translation instruction for auto to en`() {
        val prompt = TranslationPrompt.build(SttLanguage.Auto, SttLanguage.English)
        assertThat(prompt).contains("English")
    }

    @Test
    fun `should not output explanations instruction`() {
        val prompt = TranslationPrompt.build(SttLanguage.Chinese, SttLanguage.English)
        assertThat(prompt).contains("no explanation")
    }

    @Test
    fun `should handle zh to ja translation`() {
        val prompt = TranslationPrompt.build(SttLanguage.Chinese, SttLanguage.Japanese)
        assertThat(prompt).contains("日本語")
    }
}
```

### Step 2: Run tests to verify they fail

```bash
./gradlew :app:test --tests "com.voxpen.app.data.model.TranslationPromptTest" 2>&1 | tail -20
```
Expected: FAIL — `TranslationPrompt` not found

### Step 3: Create `TranslationPrompt`

```kotlin
// app/src/main/java/com/voxpen/app/data/model/TranslationPrompt.kt
package com.voxpen.app.data.model

object TranslationPrompt {
    fun build(source: SttLanguage, target: SttLanguage): String =
        when (target) {
            SttLanguage.English -> toEnglish(source)
            SttLanguage.Chinese -> toChinese(source)
            SttLanguage.Japanese -> toJapanese(source)
            else -> toEnglish(source) // fallback
        }

    private fun toEnglish(source: SttLanguage): String =
        "You are a translator. Translate the following ${sourceName(source)} speech transcription " +
            "into natural written English. Remove filler words and self-corrections in the process. " +
            "Output only the translated English text, no explanations."

    private fun toChinese(source: SttLanguage): String =
        "你是翻譯助手。請將以下${sourceName(source)}口語逐字稿翻譯成自然流暢的繁體中文書面語。" +
            "同時移除口語贅字和停頓。只輸出翻譯後的繁體中文文字，不要加任何解釋。"

    private fun toJapanese(source: SttLanguage): String =
        "あなたは翻訳アシスタントです。以下の${sourceName(source)}の音声書き起こしを、" +
            "自然な日本語書き言葉に翻訳してください。フィラーや言い直しも除去してください。" +
            "翻訳後の日本語テキストのみ出力し、説明は不要です。"

    private fun sourceName(source: SttLanguage): String =
        when (source) {
            SttLanguage.Chinese -> "Traditional Chinese"
            SttLanguage.English -> "English"
            SttLanguage.Japanese -> "Japanese"
            SttLanguage.Korean -> "Korean"
            SttLanguage.French -> "French"
            SttLanguage.German -> "German"
            SttLanguage.Spanish -> "Spanish"
            SttLanguage.Vietnamese -> "Vietnamese"
            SttLanguage.Indonesian -> "Indonesian"
            SttLanguage.Thai -> "Thai"
            SttLanguage.Auto -> "spoken"
        }
}
```

### Step 4: Run tests to verify they pass

```bash
./gradlew :app:test --tests "com.voxpen.app.data.model.TranslationPromptTest" 2>&1 | tail -20
```
Expected: 5 tests PASS

### Step 5: Commit

```bash
git add app/src/main/java/com/voxpen/app/data/model/TranslationPrompt.kt \
        app/src/test/java/com/voxpen/app/data/model/TranslationPromptTest.kt
git commit -m "feat: add TranslationPrompt for translation mode system prompts"
```

---

## Task 2: Add Translation Preferences to `PreferencesManager`

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/data/local/PreferencesManager.kt`
- Modify: `app/src/test/java/com/voxpen/app/data/local/PreferencesManagerTest.kt`

### Step 1: Write failing tests

Open `PreferencesManagerTest.kt` and add at the end of the test class:

```kotlin
@Test
fun `translationEnabledFlow defaults to false`() = runTest {
    val result = preferencesManager.translationEnabledFlow.first()
    assertThat(result).isFalse()
}

@Test
fun `setTranslationEnabled persists value`() = runTest {
    preferencesManager.setTranslationEnabled(true)
    val result = preferencesManager.translationEnabledFlow.first()
    assertThat(result).isTrue()
}

@Test
fun `translationTargetLanguageFlow defaults to English`() = runTest {
    val result = preferencesManager.translationTargetLanguageFlow.first()
    assertThat(result).isEqualTo(SttLanguage.English)
}

@Test
fun `setTranslationTargetLanguage persists value`() = runTest {
    preferencesManager.setTranslationTargetLanguage(SttLanguage.Japanese)
    val result = preferencesManager.translationTargetLanguageFlow.first()
    assertThat(result).isEqualTo(SttLanguage.Japanese)
}
```

### Step 2: Run tests to verify they fail

```bash
./gradlew :app:test --tests "com.voxpen.app.data.local.PreferencesManagerTest" 2>&1 | tail -20
```
Expected: FAIL — unresolved reference

### Step 3: Add fields to `PreferencesManager`

In `PreferencesManager.kt`, add after `keyboardTooltipsShownFlow`:

```kotlin
val translationEnabledFlow: Flow<Boolean> =
    context.dataStore.data.map { prefs ->
        prefs[TRANSLATION_ENABLED_KEY] ?: false
    }

val translationTargetLanguageFlow: Flow<SttLanguage> =
    context.dataStore.data.map { prefs ->
        languageFromKey(prefs[TRANSLATION_TARGET_LANGUAGE_KEY] ?: "en")
    }
```

Add after `setKeyboardTooltipsShown()`:

```kotlin
suspend fun setTranslationEnabled(enabled: Boolean) {
    context.dataStore.edit { prefs ->
        prefs[TRANSLATION_ENABLED_KEY] = enabled
    }
}

suspend fun setTranslationTargetLanguage(language: SttLanguage) {
    context.dataStore.edit { prefs ->
        prefs[TRANSLATION_TARGET_LANGUAGE_KEY] = languageToKey(language)
    }
}
```

Add in the `companion object` keys block:

```kotlin
private val TRANSLATION_ENABLED_KEY = booleanPreferencesKey("translation_enabled")
private val TRANSLATION_TARGET_LANGUAGE_KEY = stringPreferencesKey("translation_target_language")
```

### Step 4: Run tests to verify they pass

```bash
./gradlew :app:test --tests "com.voxpen.app.data.local.PreferencesManagerTest" 2>&1 | tail -20
```
Expected: All tests PASS

### Step 5: Commit

```bash
git add app/src/main/java/com/voxpen/app/data/local/PreferencesManager.kt \
        app/src/test/java/com/voxpen/app/data/local/PreferencesManagerTest.kt
git commit -m "feat: add translationEnabled and translationTargetLanguage preferences"
```

---

## Task 3: Thread Translation Through `RefineTextUseCase` and `LlmRepository`

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/domain/usecase/RefineTextUseCase.kt`
- Modify: `app/src/main/java/com/voxpen/app/data/repository/LlmRepository.kt`
- Modify: `app/src/test/java/com/voxpen/app/domain/usecase/RefineTextUseCaseTest.kt`
- Modify: `app/src/test/java/com/voxpen/app/data/repository/LlmRepositoryTest.kt`

### Step 1: Write failing tests for `LlmRepository`

Open `LlmRepositoryTest.kt` and add:

```kotlin
@Test
fun `should use translation prompt when translationEnabled is true`() = runTest {
    // Arrange: set up MockWebServer to return a response
    server.enqueue(MockResponse().setBody("""
        {"choices":[{"message":{"role":"assistant","content":"Hello world"}}]}
    """.trimIndent()))

    // Act
    val result = repository.refine(
        text = "你好世界",
        language = SttLanguage.Chinese,
        apiKey = "test-key",
        translationEnabled = true,
        targetLanguage = SttLanguage.English,
    )

    // Assert: request was made with translation prompt
    val request = server.takeRequest()
    val body = request.body.readUtf8()
    assertThat(body).contains("translat")
    assertThat(result.isSuccess).isTrue()
}

@Test
fun `should use refinement prompt when translationEnabled is false`() = runTest {
    server.enqueue(MockResponse().setBody("""
        {"choices":[{"message":{"role":"assistant","content":"cleaned text"}}]}
    """.trimIndent()))

    val result = repository.refine(
        text = "嗯，你好",
        language = SttLanguage.Chinese,
        apiKey = "test-key",
        translationEnabled = false,
        targetLanguage = SttLanguage.English,
    )

    val request = server.takeRequest()
    val body = request.body.readUtf8()
    assertThat(body).contains("移除贅字")   // Chinese refinement prompt
    assertThat(result.isSuccess).isTrue()
}
```

### Step 2: Run tests to verify they fail

```bash
./gradlew :app:test --tests "com.voxpen.app.data.repository.LlmRepositoryTest" 2>&1 | tail -20
```
Expected: FAIL — new params not yet added

### Step 3: Update `LlmRepository.refine()`

In `LlmRepository.kt`, add two new optional params:

```kotlin
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
    translationEnabled: Boolean = false,        // NEW
    targetLanguage: SttLanguage = SttLanguage.English,  // NEW
): Result<String> {
    // ... existing null/blank checks ...

    return try {
        val api = if (provider == LlmProvider.Custom && !customBaseUrl.isNullOrBlank()) {
            apiFactory.createForCustom(customBaseUrl)
        } else {
            apiFactory.create(provider)
        }
        // NEW: route to translation or refinement prompt
        val systemPrompt = if (translationEnabled) {
            TranslationPrompt.build(language, targetLanguage)
        } else {
            RefinementPrompt.forLanguage(language, vocabulary, customPrompt, tone)
        }
        // ... rest unchanged ...
    }
}
```

Add import at top of `LlmRepository.kt`:
```kotlin
import com.voxpen.app.data.model.TranslationPrompt
```

### Step 4: Update `RefineTextUseCase`

```kotlin
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
    translationEnabled: Boolean = false,               // NEW
    targetLanguage: SttLanguage = SttLanguage.English, // NEW
): Result<String> = llmRepository.refine(
    text, language, apiKey, model, vocabulary, customPrompt, tone, provider, customBaseUrl,
    translationEnabled, targetLanguage,                // NEW
)
```

### Step 5: Run tests to verify they pass

```bash
./gradlew :app:test --tests "com.voxpen.app.data.repository.LlmRepositoryTest" \
                    --tests "com.voxpen.app.domain.usecase.RefineTextUseCaseTest" 2>&1 | tail -20
```
Expected: All PASS

### Step 6: Run full test suite to check for regressions

```bash
./gradlew :app:test 2>&1 | tail -30
```
Expected: All tests PASS (new params have defaults, so no call sites break)

### Step 7: Commit

```bash
git add app/src/main/java/com/voxpen/app/data/repository/LlmRepository.kt \
        app/src/main/java/com/voxpen/app/domain/usecase/RefineTextUseCase.kt \
        app/src/test/java/com/voxpen/app/data/repository/LlmRepositoryTest.kt \
        app/src/test/java/com/voxpen/app/domain/usecase/RefineTextUseCaseTest.kt
git commit -m "feat: thread translation mode through RefineTextUseCase and LlmRepository"
```

---

## Task 4: Wire Translation Prefs into `RecordingController`

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/ime/RecordingController.kt`
- Modify: `app/src/test/java/com/voxpen/app/ime/RecordingControllerTest.kt`

### Step 1: Write failing tests

Open `RecordingControllerTest.kt` and add:

```kotlin
@Test
fun `should call refineTextUseCase with translationEnabled when translation is on`() = runTest {
    // Arrange
    preferencesManager.setTranslationEnabled(true)
    preferencesManager.setTranslationTargetLanguage(SttLanguage.English)
    coEvery { transcribeUseCase(any(), any(), any(), any(), any(), any()) } returns Result.success("你好")
    coEvery { refineTextUseCase(any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(true), eq(SttLanguage.English)) } returns Result.success("Hello")

    // Act
    controller.onStopRecording(stopRecording = { ByteArray(0) }, language = SttLanguage.Chinese)
    testDispatcher.scheduler.advanceUntilIdle()

    // Assert
    coVerify {
        refineTextUseCase(
            any(), any(), any(), any(), any(), any(), any(), any(), any(),
            translationEnabled = true,
            targetLanguage = SttLanguage.English,
        )
    }
}
```

> **Note:** Check existing `RecordingControllerTest` to match the mock setup pattern used there for `preferencesManager`. The test likely uses `mockk<PreferencesManager>()` with `every { translationEnabledFlow } returns flowOf(false)` style. Match that pattern for the new flows.

### Step 2: Run tests to verify they fail

```bash
./gradlew :app:test --tests "com.voxpen.app.ime.RecordingControllerTest" 2>&1 | tail -20
```
Expected: FAIL

### Step 3: Update `RecordingController`

Add two new private fields after `customSttBaseUrl`:

```kotlin
private var translationEnabled: Boolean = false
private var translationTargetLanguage: SttLanguage = SttLanguage.English
```

Add two new `scope.launch` collectors in `init {}`:

```kotlin
scope.launch { preferencesManager.translationEnabledFlow.collect { translationEnabled = it } }
scope.launch { preferencesManager.translationTargetLanguageFlow.collect { translationTargetLanguage = it } }
```

In `onStopRecording()`, find the `refineTextUseCase(...)` call and add the two new args:

```kotlin
val refinedResult = refineTextUseCase(
    originalText, language, apiKey, resolvedModel, allVocabulary,
    customPrompt, toneStyle, llmProvider, customBaseUrl,
    translationEnabled, translationTargetLanguage,  // NEW
)
```

### Step 4: Run tests

```bash
./gradlew :app:test --tests "com.voxpen.app.ime.RecordingControllerTest" 2>&1 | tail -20
```
Expected: All PASS

### Step 5: Run full suite

```bash
./gradlew :app:test 2>&1 | tail -10
```
Expected: All PASS

### Step 6: Commit

```bash
git add app/src/main/java/com/voxpen/app/ime/RecordingController.kt \
        app/src/test/java/com/voxpen/app/ime/RecordingControllerTest.kt
git commit -m "feat: wire translation preferences into RecordingController"
```

---

## Task 5: Add Settings UI (Toggle + Target Language Picker)

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/ui/settings/SettingsUiState.kt`
- Modify: `app/src/main/java/com/voxpen/app/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/voxpen/app/ui/settings/SettingsScreen.kt`
- Modify: `app/src/test/java/com/voxpen/app/ui/settings/SettingsViewModelTest.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`

### Step 1: Add strings

In `app/src/main/res/values/strings.xml`, add inside `<resources>`:

```xml
<string name="translation_mode">Translation Mode</string>
<string name="translation_mode_desc">Speak in one language, output in another</string>
<string name="translation_target_language">Translate to</string>
```

In `app/src/main/res/values-zh-rTW/strings.xml`, add:

```xml
<string name="translation_mode">翻譯模式</string>
<string name="translation_mode_desc">用一種語言說話，輸出另一種語言</string>
<string name="translation_target_language">翻譯為</string>
```

### Step 2: Update `SettingsUiState`

Add two new fields to `SettingsUiState`:

```kotlin
data class SettingsUiState(
    // ... existing fields ...
    val translationEnabled: Boolean = false,
    val translationTargetLanguage: SttLanguage = SttLanguage.English,
)
```

### Step 3: Write failing ViewModel test

In `SettingsViewModelTest.kt`, add:

```kotlin
@Test
fun `translationEnabled reflects preference value`() = runTest {
    preferencesManager.setTranslationEnabled(true)
    testDispatcher.scheduler.advanceUntilIdle()
    assertThat(viewModel.uiState.value.translationEnabled).isTrue()
}

@Test
fun `setTranslationEnabled updates preference`() = runTest {
    viewModel.setTranslationEnabled(true)
    testDispatcher.scheduler.advanceUntilIdle()
    assertThat(preferencesManager.translationEnabledFlow.first()).isTrue()
}

@Test
fun `setTranslationTargetLanguage updates preference`() = runTest {
    viewModel.setTranslationTargetLanguage(SttLanguage.Japanese)
    testDispatcher.scheduler.advanceUntilIdle()
    assertThat(preferencesManager.translationTargetLanguageFlow.first()).isEqualTo(SttLanguage.Japanese)
}
```

### Step 4: Run tests to verify they fail

```bash
./gradlew :app:test --tests "com.voxpen.app.ui.settings.SettingsViewModelTest" 2>&1 | tail -20
```
Expected: FAIL

### Step 5: Update `SettingsViewModel`

In `init {}` block, add two new collectors:

```kotlin
viewModelScope.launch {
    preferencesManager.translationEnabledFlow.collect { enabled ->
        _uiState.update { it.copy(translationEnabled = enabled) }
    }
}
viewModelScope.launch {
    preferencesManager.translationTargetLanguageFlow.collect { lang ->
        _uiState.update { it.copy(translationTargetLanguage = lang) }
    }
}
```

Add two new public functions:

```kotlin
fun setTranslationEnabled(enabled: Boolean) {
    viewModelScope.launch { preferencesManager.setTranslationEnabled(enabled) }
}

fun setTranslationTargetLanguage(language: SttLanguage) {
    viewModelScope.launch { preferencesManager.setTranslationTargetLanguage(language) }
}
```

### Step 6: Run tests to verify they pass

```bash
./gradlew :app:test --tests "com.voxpen.app.ui.settings.SettingsViewModelTest" 2>&1 | tail -20
```
Expected: All PASS

### Step 7: Add UI to `SettingsScreen`

Find the "Refinement" section in `SettingsScreen.kt`. Add a Translation Mode section **below** the refinement toggle. Follow the same pattern used for `refinementEnabled`:

```kotlin
// Translation Mode toggle
SettingsSwitchRow(
    title = stringResource(R.string.translation_mode),
    subtitle = stringResource(R.string.translation_mode_desc),
    checked = uiState.translationEnabled,
    onCheckedChange = { viewModel.setTranslationEnabled(it) },
)

// Target language picker — only visible when translation is on
if (uiState.translationEnabled) {
    val targetLanguages = listOf(
        SttLanguage.English,
        SttLanguage.Chinese,
        SttLanguage.Japanese,
    )
    SettingsSectionLabel(stringResource(R.string.translation_target_language))
    targetLanguages.forEach { lang ->
        val label = when (lang) {
            SttLanguage.English -> "${lang.emoji} English"
            SttLanguage.Chinese -> "${lang.emoji} 繁體中文"
            SttLanguage.Japanese -> "${lang.emoji} 日本語"
            else -> lang.emoji
        }
        SettingsRadioRow(
            label = label,
            selected = uiState.translationTargetLanguage == lang,
            onClick = { viewModel.setTranslationTargetLanguage(lang) },
        )
    }
}
```

> **Note:** Look at how the existing language picker rows are built in `SettingsScreen.kt` and use the same composable pattern (e.g., `SettingsSwitchRow`, `SettingsRadioRow`, `SettingsSectionLabel`, or equivalent). Match the existing style exactly.

### Step 8: Build to verify no compile errors

```bash
./gradlew :app:assembleDebug 2>&1 | tail -30
```
Expected: BUILD SUCCESSFUL

### Step 9: Run full test suite

```bash
./gradlew :app:test 2>&1 | tail -10
```
Expected: All PASS

### Step 10: Manual test on device/emulator

1. Open VoxPen app → Settings
2. Find "Translation Mode" toggle → enable it
3. Verify "Translate to" options appear: English / 繁體中文 / 日本語
4. Select English
5. Switch to an app with a text field, activate VoxPen keyboard
6. Speak Chinese → verify output is English
7. Disable Translation Mode → verify output returns to Chinese refinement

### Step 11: Commit

```bash
git add app/src/main/java/com/voxpen/app/ui/settings/SettingsUiState.kt \
        app/src/main/java/com/voxpen/app/ui/settings/SettingsViewModel.kt \
        app/src/main/java/com/voxpen/app/ui/settings/SettingsScreen.kt \
        app/src/test/java/com/voxpen/app/ui/settings/SettingsViewModelTest.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-zh-rTW/strings.xml
git commit -m "feat: add translation mode toggle and target language picker to Settings"
```

---

## Task 6: Add Translation Toggle to IME Quick Settings Popup

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/ime/VoxPenIME.kt`

This task is IME-only (no unit tests possible for `InputMethodService`). Verify manually.

### Step 1: Locate `showQuickSettings()` in `VoxPenIME.kt`

It currently reads `refinementOn` from preferences and calls `addRefinementToggle(...)`.

### Step 2: Add translation toggle alongside refinement toggle

Update `showQuickSettings()` to also read translation state:

```kotlin
private fun showQuickSettings(anchor: View) {
    serviceScope.launch {
        val refinementOn = preferencesManager.refinementEnabledFlow.first()
        val translationOn = preferencesManager.translationEnabledFlow.first()  // NEW
        val dp = resources.displayMetrics.density

        val container = createQuickSettingsContainer(dp)
        val popup = PopupWindow(
            container,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        )

        addRefinementToggle(container, popup, refinementOn, dp)
        addTranslationToggle(container, popup, translationOn, dp)  // NEW

        popup.showAtLocation(anchor, Gravity.BOTTOM or Gravity.END, (8 * dp).toInt(), (64 * dp).toInt())
    }
}
```

### Step 3: Implement `addTranslationToggle()`

Follow the exact same pattern as `addRefinementToggle()`. The label is "🔄 Translation" when off, "🔄 Translation ✓" when on:

```kotlin
private fun addTranslationToggle(
    container: LinearLayout,
    popup: PopupWindow,
    translationOn: Boolean,
    dp: Float,
) {
    val label = if (translationOn) "🔄 ${getString(R.string.translation_mode)} ✓"
                else "🔄 ${getString(R.string.translation_mode)}"
    val tv = TextView(this).apply {
        text = label
        textSize = 14f
        setTextColor(resources.getColor(R.color.key_text, null))
        val pad = (8 * dp).toInt()
        setPadding(pad, pad, pad, pad)
        setOnClickListener {
            serviceScope.launch {
                preferencesManager.setTranslationEnabled(!translationOn)
            }
            popup.dismiss()
        }
    }
    container.addView(tv)
}
```

### Step 4: Build to verify

```bash
./gradlew :app:assembleDebug 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL

### Step 5: Manual test

1. Activate VoxPen keyboard in any text field
2. Long-press the ⚙️ settings button → quick settings popup appears
3. Verify "🔄 Translation Mode" appears in the popup
4. Tap it → popup closes; speak → output should be translated
5. Long-press again → verify "🔄 Translation Mode ✓" now shows (checkmark)

### Step 6: Commit

```bash
git add app/src/main/java/com/voxpen/app/ime/VoxPenIME.kt
git commit -m "feat: add translation mode toggle to IME quick settings popup"
```

---

## Final Verification

```bash
./gradlew :app:test 2>&1 | tail -10
./gradlew :app:assembleDebug 2>&1 | tail -5
```

Both should succeed. Then do a full end-to-end manual test of the translation flow as described in Task 5, Step 10.
