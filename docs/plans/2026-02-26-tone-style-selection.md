# Tone/Style Selection Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a tone/style selector (Casual, Professional, Email, Note, Social, Custom) that switches the LLM refinement system prompt, accessible from both Settings and the IME quick-settings popup.

**Architecture:** A new `ToneStyle` sealed class models the 6 tone options. Each tone has per-language prompt overrides stored in `RefinementPrompt`. The selected tone is persisted in `PreferencesManager` and read by `RecordingController` at refinement time. The existing `customPrompt` system becomes the "Custom" tone. When a non-Custom tone is selected, its built-in prompt overrides the default; when Custom is selected, the existing custom prompt editor is used. The IME quick-settings popup gets a tone picker via long-press on a new tone button.

**Tech Stack:** Kotlin, Jetpack Compose (Settings), Android Views (IME popup), DataStore Preferences, JUnit 5 + MockK + Truth

---

## Task 1: Create ToneStyle model

**Files:**
- Create: `app/src/main/java/com/voxink/app/data/model/ToneStyle.kt`
- Create: `app/src/test/java/com/voxink/app/data/model/ToneStyleTest.kt`

**Step 1: Write the failing test**

Create `app/src/test/java/com/voxink/app/data/model/ToneStyleTest.kt`:

```kotlin
package com.voxink.app.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ToneStyleTest {
    @Test
    fun `all tone styles should have unique keys`() {
        val keys = ToneStyle.all.map { it.key }
        assertThat(keys).containsNoDuplicates()
    }

    @Test
    fun `fromKey should return correct tone`() {
        assertThat(ToneStyle.fromKey("casual")).isEqualTo(ToneStyle.Casual)
        assertThat(ToneStyle.fromKey("professional")).isEqualTo(ToneStyle.Professional)
        assertThat(ToneStyle.fromKey("email")).isEqualTo(ToneStyle.Email)
        assertThat(ToneStyle.fromKey("note")).isEqualTo(ToneStyle.Note)
        assertThat(ToneStyle.fromKey("social")).isEqualTo(ToneStyle.Social)
        assertThat(ToneStyle.fromKey("custom")).isEqualTo(ToneStyle.Custom)
    }

    @Test
    fun `fromKey should default to Casual for unknown key`() {
        assertThat(ToneStyle.fromKey("unknown")).isEqualTo(ToneStyle.Casual)
    }

    @Test
    fun `default tone should be Casual`() {
        assertThat(ToneStyle.DEFAULT).isEqualTo(ToneStyle.Casual)
    }

    @Test
    fun `all should contain exactly 6 tones`() {
        assertThat(ToneStyle.all).hasSize(6)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.voxink.app.data.model.ToneStyleTest" --no-daemon`
Expected: FAIL — `ToneStyle` doesn't exist.

**Step 3: Create ToneStyle.kt**

Create `app/src/main/java/com/voxink/app/data/model/ToneStyle.kt`:

```kotlin
package com.voxink.app.data.model

sealed class ToneStyle(
    val key: String,
) {
    data object Casual : ToneStyle("casual")
    data object Professional : ToneStyle("professional")
    data object Email : ToneStyle("email")
    data object Note : ToneStyle("note")
    data object Social : ToneStyle("social")
    data object Custom : ToneStyle("custom")

    companion object {
        val DEFAULT: ToneStyle = Casual

        val all: List<ToneStyle> = listOf(Casual, Professional, Email, Note, Social, Custom)

        fun fromKey(key: String): ToneStyle =
            all.find { it.key == key } ?: DEFAULT
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.voxink.app.data.model.ToneStyleTest" --no-daemon`
Expected: All 5 tests PASS.

**Step 5: Commit**

```bash
git add app/src/main/java/com/voxink/app/data/model/ToneStyle.kt \
       app/src/test/java/com/voxink/app/data/model/ToneStyleTest.kt
git commit -m "feat: add ToneStyle sealed class with 6 tone options"
```

---

## Task 2: Add tone-specific prompts to RefinementPrompt

**Files:**
- Modify: `app/src/main/java/com/voxink/app/data/model/RefinementPrompt.kt`
- Modify: `app/src/test/java/com/voxink/app/data/model/RefinementPromptTest.kt`

**Step 1: Write failing tests for tone prompts**

Add to `app/src/test/java/com/voxink/app/data/model/RefinementPromptTest.kt`:

```kotlin
@Test
fun `forLanguageAndTone should return casual prompt for Chinese`() {
    val prompt = RefinementPrompt.forLanguageAndTone(SttLanguage.Chinese, ToneStyle.Casual)
    assertThat(prompt).contains("輕鬆自然")
}

@Test
fun `forLanguageAndTone should return professional prompt for Chinese`() {
    val prompt = RefinementPrompt.forLanguageAndTone(SttLanguage.Chinese, ToneStyle.Professional)
    assertThat(prompt).contains("正式書面")
}

@Test
fun `forLanguageAndTone should return email prompt for Chinese`() {
    val prompt = RefinementPrompt.forLanguageAndTone(SttLanguage.Chinese, ToneStyle.Email)
    assertThat(prompt).contains("電子郵件")
}

@Test
fun `forLanguageAndTone should return note prompt for Chinese`() {
    val prompt = RefinementPrompt.forLanguageAndTone(SttLanguage.Chinese, ToneStyle.Note)
    assertThat(prompt).contains("條列式")
}

@Test
fun `forLanguageAndTone should return social prompt for Chinese`() {
    val prompt = RefinementPrompt.forLanguageAndTone(SttLanguage.Chinese, ToneStyle.Social)
    assertThat(prompt).contains("社群")
}

@Test
fun `forLanguageAndTone with Custom should return default prompt`() {
    val prompt = RefinementPrompt.forLanguageAndTone(SttLanguage.Chinese, ToneStyle.Custom)
    assertThat(prompt).isEqualTo(RefinementPrompt.defaultForLanguage(SttLanguage.Chinese))
}

@Test
fun `forLanguageAndTone should return casual prompt for English`() {
    val prompt = RefinementPrompt.forLanguageAndTone(SttLanguage.English, ToneStyle.Casual)
    assertThat(prompt).contains("casual")
}

@Test
fun `forLanguageAndTone should return professional prompt for English`() {
    val prompt = RefinementPrompt.forLanguageAndTone(SttLanguage.English, ToneStyle.Professional)
    assertThat(prompt).contains("formal")
}

@Test
fun `forLanguageAndTone should return professional prompt for Japanese`() {
    val prompt = RefinementPrompt.forLanguageAndTone(SttLanguage.Japanese, ToneStyle.Professional)
    assertThat(prompt).contains("敬語")
}

@Test
fun `forLanguageAndTone should return casual prompt for Japanese`() {
    val prompt = RefinementPrompt.forLanguageAndTone(SttLanguage.Japanese, ToneStyle.Casual)
    assertThat(prompt).contains("カジュアル")
}

@Test
fun `forLanguage with tone should thread tone into prompt resolution`() {
    val prompt = RefinementPrompt.forLanguage(
        language = SttLanguage.English,
        tone = ToneStyle.Email,
    )
    assertThat(prompt).contains("email")
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.voxink.app.data.model.RefinementPromptTest" --no-daemon`
Expected: FAIL — `forLanguageAndTone` doesn't exist.

**Step 3: Add tone prompts to RefinementPrompt.kt**

Update `app/src/main/java/com/voxink/app/data/model/RefinementPrompt.kt`. Add a `tone` parameter to `forLanguage` and a new `forLanguageAndTone` function.

Update the main `forLanguage` function signature (keep backward-compatible with default):

```kotlin
fun forLanguage(
    language: SttLanguage,
    vocabulary: List<String> = emptyList(),
    customPrompt: String? = null,
    tone: ToneStyle = ToneStyle.Casual,
): String {
    val base = when {
        customPrompt?.isNotBlank() == true -> customPrompt
        tone == ToneStyle.Custom -> defaultForLanguage(language)
        else -> forLanguageAndTone(language, tone)
    }
    return base + VocabularyPromptBuilder.buildLlmSuffix(language, vocabulary)
}
```

Add the `forLanguageAndTone` function:

```kotlin
fun forLanguageAndTone(language: SttLanguage, tone: ToneStyle): String =
    when (tone) {
        ToneStyle.Custom -> defaultForLanguage(language)
        ToneStyle.Casual -> casualForLanguage(language)
        ToneStyle.Professional -> professionalForLanguage(language)
        ToneStyle.Email -> emailForLanguage(language)
        ToneStyle.Note -> noteForLanguage(language)
        ToneStyle.Social -> socialForLanguage(language)
    }
```

Add per-tone prompt selection functions. Each function maps language → the tone-specific prompt, with English/Chinese/Japanese having dedicated prompts and other languages using a generic template appended to the base language prompt:

```kotlin
private fun casualForLanguage(language: SttLanguage): String =
    when (language) {
        SttLanguage.Chinese -> CASUAL_ZH
        SttLanguage.English -> CASUAL_EN
        SttLanguage.Japanese -> CASUAL_JA
        else -> CASUAL_EN // Fallback to English casual for other languages
    }

private fun professionalForLanguage(language: SttLanguage): String =
    when (language) {
        SttLanguage.Chinese -> PROFESSIONAL_ZH
        SttLanguage.English -> PROFESSIONAL_EN
        SttLanguage.Japanese -> PROFESSIONAL_JA
        else -> PROFESSIONAL_EN
    }

private fun emailForLanguage(language: SttLanguage): String =
    when (language) {
        SttLanguage.Chinese -> EMAIL_ZH
        SttLanguage.English -> EMAIL_EN
        SttLanguage.Japanese -> EMAIL_JA
        else -> EMAIL_EN
    }

private fun noteForLanguage(language: SttLanguage): String =
    when (language) {
        SttLanguage.Chinese -> NOTE_ZH
        SttLanguage.English -> NOTE_EN
        SttLanguage.Japanese -> NOTE_JA
        else -> NOTE_EN
    }

private fun socialForLanguage(language: SttLanguage): String =
    when (language) {
        SttLanguage.Chinese -> SOCIAL_ZH
        SttLanguage.English -> SOCIAL_EN
        SttLanguage.Japanese -> SOCIAL_JA
        else -> SOCIAL_EN
    }
```

Add the tone prompt constants:

```kotlin
// --- Casual ---
private const val CASUAL_ZH =
    "你是一個語音轉文字的編輯助手。請將以下口語內容整理為輕鬆自然的文字：\n" +
        "1. 移除贅字（嗯、那個、就是、然後、對、呃）\n" +
        "2. 保持口語化、輕鬆的語氣\n" +
        "3. 可以使用縮寫和口語表達\n" +
        "4. 適當加入標點符號\n" +
        "5. 不要添加原文沒有的內容\n" +
        "6. 保持繁體中文\n" +
        "只輸出整理後的文字，不要加任何解釋。"

private const val CASUAL_EN =
    "You are a voice-to-text editor. Clean up the following speech into casual, natural written text:\n" +
        "1. Remove filler words (um, uh, like, you know)\n" +
        "2. Keep a casual, relaxed tone\n" +
        "3. Contractions and informal expressions are fine\n" +
        "4. Add proper punctuation\n" +
        "5. Do not add content not in the original speech\n" +
        "Output only the cleaned text, no explanations."

private const val CASUAL_JA =
    "あなたは音声テキスト変換の編集アシスタントです。以下の口語内容をカジュアルな書き言葉に整理してください：\n" +
        "1. フィラー（えーと、あの、まあ）を除去\n" +
        "2. 敬語は不要、くだけた口調で\n" +
        "3. 文法を軽く修正し、原意を保持\n" +
        "4. 適切に句読点を追加\n" +
        "5. 原文にない内容を追加しない\n" +
        "整理後のテキストのみ出力し、説明は不要です。"

// --- Professional ---
private const val PROFESSIONAL_ZH =
    "你是一個語音轉文字的編輯助手。請將以下口語內容整理為正式書面文字：\n" +
        "1. 移除贅字\n" +
        "2. 使用完整句型，語氣專業得體\n" +
        "3. 修正語法，確保書面語規範\n" +
        "4. 適當加入標點符號\n" +
        "5. 不要添加原文沒有的內容\n" +
        "6. 保持繁體中文\n" +
        "只輸出整理後的文字，不要加任何解釋。"

private const val PROFESSIONAL_EN =
    "You are a voice-to-text editor. Clean up the following speech into formal, professional written text:\n" +
        "1. Remove filler words\n" +
        "2. Use complete sentences with a professional, polished tone\n" +
        "3. Fix grammar and ensure formal register\n" +
        "4. Add proper punctuation\n" +
        "5. Do not add content not in the original speech\n" +
        "Output only the cleaned text, no explanations."

private const val PROFESSIONAL_JA =
    "あなたは音声テキスト変換の編集アシスタントです。以下の口語内容をビジネスメールにふさわしい丁寧語・敬語で整理してください：\n" +
        "1. フィラーを除去\n" +
        "2. 丁寧語・敬語を使用\n" +
        "3. 文法を修正し、フォーマルな文体に\n" +
        "4. 適切に句読点を追加\n" +
        "5. 原文にない内容を追加しない\n" +
        "整理後のテキストのみ出力し、説明は不要です。"

// --- Email ---
private const val EMAIL_ZH =
    "你是一個語音轉文字的編輯助手。請將以下口語內容整理為電子郵件格式：\n" +
        "1. 移除贅字\n" +
        "2. 開頭加問候語，結尾加敬語\n" +
        "3. 段落分明，語氣正式有禮\n" +
        "4. 適當加入標點符號\n" +
        "5. 不要添加原文沒有的核心內容\n" +
        "6. 保持繁體中文\n" +
        "只輸出整理後的文字，不要加任何解釋。"

private const val EMAIL_EN =
    "You are a voice-to-text editor. Format the following speech as a professional email:\n" +
        "1. Remove filler words\n" +
        "2. Add a greeting at the start and a sign-off at the end\n" +
        "3. Organize into clear paragraphs with polite, formal tone\n" +
        "4. Add proper punctuation\n" +
        "5. Do not add core content not in the original speech\n" +
        "Output only the formatted email text, no explanations."

private const val EMAIL_JA =
    "あなたは音声テキスト変換の編集アシスタントです。以下の口語内容をメール形式で整理してください：\n" +
        "1. フィラーを除去\n" +
        "2. 冒頭に挨拶、末尾に敬具を追加\n" +
        "3. 段落を分け、丁寧語で\n" +
        "4. 適切に句読点を追加\n" +
        "5. 原文にない核心内容を追加しない\n" +
        "整理後のテキストのみ出力し、説明は不要です。"

// --- Note ---
private const val NOTE_ZH =
    "你是一個語音轉文字的編輯助手。請將以下口語內容整理為條列式筆記：\n" +
        "1. 移除贅字\n" +
        "2. 使用條列式（bullet points）呈現\n" +
        "3. 精簡為關鍵字和短句\n" +
        "4. 保持原意，不添加額外內容\n" +
        "5. 保持繁體中文\n" +
        "只輸出整理後的文字，不要加任何解釋。"

private const val NOTE_EN =
    "You are a voice-to-text editor. Convert the following speech into concise bullet-point notes:\n" +
        "1. Remove filler words\n" +
        "2. Use bullet points\n" +
        "3. Distill to keywords and short phrases\n" +
        "4. Preserve original meaning, do not add content\n" +
        "Output only the notes, no explanations."

private const val NOTE_JA =
    "あなたは音声テキスト変換の編集アシスタントです。以下の口語内容を箇条書きのメモに整理してください：\n" +
        "1. フィラーを除去\n" +
        "2. 箇条書きで整理\n" +
        "3. キーワードと短文に凝縮\n" +
        "4. 原意を保持し、内容を追加しない\n" +
        "整理後のテキストのみ出力し、説明は不要です。"

// --- Social ---
private const val SOCIAL_ZH =
    "你是一個語音轉文字的編輯助手。請將以下口語內容整理為適合社群媒體發文的文字：\n" +
        "1. 移除贅字\n" +
        "2. 語氣輕鬆活潑，適合社群貼文\n" +
        "3. 可適當使用短句\n" +
        "4. 不要添加原文沒有的內容\n" +
        "5. 保持繁體中文\n" +
        "只輸出整理後的文字，不要加任何解釋。"

private const val SOCIAL_EN =
    "You are a voice-to-text editor. Clean up the following speech for a social media post:\n" +
        "1. Remove filler words\n" +
        "2. Keep it casual, engaging, and concise\n" +
        "3. Use short sentences\n" +
        "4. Do not add content not in the original speech\n" +
        "Output only the cleaned text, no explanations."

private const val SOCIAL_JA =
    "あなたは音声テキスト変換の編集アシスタントです。以下の口語内容をSNS投稿向けに整理してください：\n" +
        "1. フィラーを除去\n" +
        "2. カジュアルで親しみやすいトーン\n" +
        "3. 短文で簡潔に\n" +
        "4. 原文にない内容を追加しない\n" +
        "整理後のテキストのみ出力し、説明は不要です。"
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.voxink.app.data.model.RefinementPromptTest" --no-daemon`
Expected: All tests PASS.

**Step 5: Commit**

```bash
git add app/src/main/java/com/voxink/app/data/model/RefinementPrompt.kt \
       app/src/test/java/com/voxink/app/data/model/RefinementPromptTest.kt
git commit -m "feat: add per-language tone-specific refinement prompts

6 tones: Casual, Professional, Email, Note, Social, Custom.
Dedicated prompts for zh-TW, en, ja. Other languages fall back to English."
```

---

## Task 3: Persist tone selection in PreferencesManager

**Files:**
- Modify: `app/src/main/java/com/voxink/app/data/local/PreferencesManager.kt`
- Modify: `app/src/test/java/com/voxink/app/data/local/PreferencesManagerTest.kt` (if exists, else note this is tested via integration)

**Step 1: Add tone flow and setter to PreferencesManager**

In `app/src/main/java/com/voxink/app/data/local/PreferencesManager.kt`:

Add a `TONE_STYLE_KEY` in companion object:
```kotlin
private val TONE_STYLE_KEY = stringPreferencesKey("tone_style")
```

Add flow (after `llmModelFlow`):
```kotlin
val toneStyleFlow: Flow<ToneStyle> =
    context.dataStore.data.map { prefs ->
        ToneStyle.fromKey(prefs[TONE_STYLE_KEY] ?: ToneStyle.DEFAULT.key)
    }
```

Add setter:
```kotlin
suspend fun setToneStyle(tone: ToneStyle) {
    context.dataStore.edit { prefs ->
        prefs[TONE_STYLE_KEY] = tone.key
    }
}
```

Add import for `ToneStyle`.

**Step 2: Build to verify**

Run: `./gradlew assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL.

**Step 3: Commit**

```bash
git add app/src/main/java/com/voxink/app/data/local/PreferencesManager.kt
git commit -m "feat: add tone style persistence in PreferencesManager"
```

---

## Task 4: Thread tone through the refinement call chain

**Files:**
- Modify: `app/src/main/java/com/voxink/app/ime/RecordingController.kt`
- Modify: `app/src/test/java/com/voxink/app/ime/RecordingControllerTest.kt`

**Step 1: Update RecordingController to read tone and pass it**

In `app/src/main/java/com/voxink/app/ime/RecordingController.kt`:

Add a `toneStyle` field (similar to `refinementEnabled`):
```kotlin
private var toneStyle: ToneStyle = ToneStyle.DEFAULT
```

Add to `init` block:
```kotlin
scope.launch { preferencesManager.toneStyleFlow.collect { toneStyle = it } }
```

In `onStopRecording`, where `refineTextUseCase` is called (around line 101), pass `toneStyle`:

Change:
```kotlin
val refinedResult = refineTextUseCase(originalText, language, apiKey, llmModel, allVocabulary, customPrompt)
```
To:
```kotlin
val refinedResult = refineTextUseCase(originalText, language, apiKey, llmModel, allVocabulary, customPrompt, toneStyle)
```

**Step 2: Update RefineTextUseCase to accept tone**

In `app/src/main/java/com/voxink/app/domain/usecase/RefineTextUseCase.kt`:

```kotlin
suspend operator fun invoke(
    text: String,
    language: SttLanguage,
    apiKey: String,
    model: String = "llama-3.3-70b-versatile",
    vocabulary: List<String> = emptyList(),
    customPrompt: String? = null,
    tone: ToneStyle = ToneStyle.Casual,
): Result<String> = llmRepository.refine(text, language, apiKey, model, vocabulary, customPrompt, tone)
```

Add import for `ToneStyle`.

**Step 3: Update LlmRepository to accept tone**

In `app/src/main/java/com/voxink/app/data/repository/LlmRepository.kt`:

```kotlin
suspend fun refine(
    text: String,
    language: SttLanguage,
    apiKey: String,
    model: String = LLM_MODEL,
    vocabulary: List<String> = emptyList(),
    customPrompt: String? = null,
    tone: ToneStyle = ToneStyle.Casual,
): Result<String> {
    // ...
    val systemPrompt = RefinementPrompt.forLanguage(language, vocabulary, customPrompt, tone)
    // ...
}
```

Add import for `ToneStyle`.

**Step 4: Update RecordingControllerTest**

In `app/src/test/java/com/voxink/app/ime/RecordingControllerTest.kt`:

Add mock for `toneStyleFlow`:
```kotlin
private val toneStyleFlow = MutableStateFlow(ToneStyle.Casual)
```

In `setUp()`:
```kotlin
every { preferencesManager.toneStyleFlow } returns toneStyleFlow
```

**Step 5: Run all tests**

Run: `./gradlew testDebugUnitTest --no-daemon`
Expected: All tests PASS.

**Step 6: Commit**

```bash
git add app/src/main/java/com/voxink/app/ime/RecordingController.kt \
       app/src/main/java/com/voxink/app/domain/usecase/RefineTextUseCase.kt \
       app/src/main/java/com/voxink/app/data/repository/LlmRepository.kt \
       app/src/test/java/com/voxink/app/ime/RecordingControllerTest.kt
git commit -m "feat: thread tone style through refinement call chain

RecordingController reads toneStyleFlow, passes through
RefineTextUseCase → LlmRepository → RefinementPrompt."
```

---

## Task 5: Add tone selector to Settings UI

**Files:**
- Modify: `app/src/main/java/com/voxink/app/ui/settings/SettingsUiState.kt`
- Modify: `app/src/main/java/com/voxink/app/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/voxink/app/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`

**Step 1: Add tone to SettingsUiState**

In `app/src/main/java/com/voxink/app/ui/settings/SettingsUiState.kt`, add field:
```kotlin
val toneStyle: ToneStyle = ToneStyle.DEFAULT,
```

Add import for `ToneStyle`.

**Step 2: Add tone collector and setter to SettingsViewModel**

In `app/src/main/java/com/voxink/app/ui/settings/SettingsViewModel.kt`:

Add to `init` block (collect toneStyleFlow):
```kotlin
viewModelScope.launch {
    preferencesManager.toneStyleFlow.collect { tone ->
        _uiState.update { it.copy(toneStyle = tone) }
    }
}
```

Add setter:
```kotlin
fun setToneStyle(tone: ToneStyle) {
    viewModelScope.launch { preferencesManager.setToneStyle(tone) }
}
```

**Step 3: Add tone section to SettingsScreen**

In `app/src/main/java/com/voxink/app/ui/settings/SettingsScreen.kt`, add a `ToneStyleSection` composable. Place it after the language section and before the custom prompt section. When `ToneStyle.Custom` is selected, the existing custom prompt editor should be shown; otherwise, it should be hidden (since the tone's built-in prompt is used).

```kotlin
@Composable
private fun ToneStyleSection(
    selectedTone: ToneStyle,
    onToneSelected: (ToneStyle) -> Unit,
) {
    Text(
        text = stringResource(R.string.settings_tone_section),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    val tones = ToneStyle.all
    val toneLabels = mapOf(
        ToneStyle.Casual to stringResource(R.string.tone_casual),
        ToneStyle.Professional to stringResource(R.string.tone_professional),
        ToneStyle.Email to stringResource(R.string.tone_email),
        ToneStyle.Note to stringResource(R.string.tone_note),
        ToneStyle.Social to stringResource(R.string.tone_social),
        ToneStyle.Custom to stringResource(R.string.tone_custom),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tones.forEach { tone ->
            FilterChip(
                selected = tone == selectedTone,
                onClick = { onToneSelected(tone) },
                label = { Text(toneLabels[tone] ?: tone.key) },
            )
        }
    }
}
```

In the main settings body, conditionally show custom prompt section only when `Custom` tone is selected:

```kotlin
ToneStyleSection(
    selectedTone = state.toneStyle,
    onToneSelected = { viewModel.setToneStyle(it) },
)
Spacer(modifier = Modifier.height(16.dp))
if (state.toneStyle == ToneStyle.Custom) {
    CustomPromptSection(state = state, viewModel = viewModel)
}
```

**Step 4: Add string resources**

In `app/src/main/res/values/strings.xml`:
```xml
<!-- Tone styles -->
<string name="settings_tone_section">Tone Style</string>
<string name="tone_casual">Casual</string>
<string name="tone_professional">Professional</string>
<string name="tone_email">Email</string>
<string name="tone_note">Note</string>
<string name="tone_social">Social</string>
<string name="tone_custom">Custom</string>
```

In `app/src/main/res/values-zh-rTW/strings.xml`:
```xml
<!-- Tone styles -->
<string name="settings_tone_section">語氣風格</string>
<string name="tone_casual">日常聊天</string>
<string name="tone_professional">正式書面</string>
<string name="tone_email">電子郵件</string>
<string name="tone_note">筆記</string>
<string name="tone_social">社群貼文</string>
<string name="tone_custom">自訂</string>
```

**Step 5: Build and run tests**

Run: `./gradlew assembleDebug --no-daemon && ./gradlew testDebugUnitTest --no-daemon`
Expected: BUILD SUCCESSFUL, all tests PASS.

**Step 6: Commit**

```bash
git add app/src/main/java/com/voxink/app/ui/settings/SettingsUiState.kt \
       app/src/main/java/com/voxink/app/ui/settings/SettingsViewModel.kt \
       app/src/main/java/com/voxink/app/ui/settings/SettingsScreen.kt \
       app/src/main/res/values/strings.xml \
       app/src/main/res/values-zh-rTW/strings.xml
git commit -m "feat: add tone style selector to Settings screen

FilterChip-based picker with 6 tones. Custom prompt editor shown
only when Custom tone is selected."
```

---

## Task 6: Add tone picker to IME quick-settings popup

**Files:**
- Modify: `app/src/main/java/com/voxink/app/ime/VoxInkIME.kt`

**Step 1: Add tone section to quick settings popup**

In `app/src/main/java/com/voxink/app/ime/VoxInkIME.kt`, in the `showQuickSettings` method (line 390), add a tone section. Read current tone from `preferencesManager.toneStyleFlow.first()`.

Between `addLanguageOptions` and `addRefinementToggle`, add:
```kotlin
addQuickSettingsDivider(container, dp)
addToneOptions(container, popup, currentTone, dp)
```

Add the `addToneOptions` method:

```kotlin
private fun addToneOptions(
    container: LinearLayout,
    popup: PopupWindow,
    currentTone: ToneStyle,
    dp: Float,
) {
    // Section header
    val header = TextView(this).apply {
        text = getString(R.string.settings_tone_section)
        textSize = 12f
        setTextColor(0x99FFFFFF.toInt())
        val pad = (8 * dp).toInt()
        setPadding(pad, pad, pad, (4 * dp).toInt())
    }
    container.addView(header)

    val tones = listOf(
        ToneStyle.Casual to getString(R.string.tone_casual),
        ToneStyle.Professional to getString(R.string.tone_professional),
        ToneStyle.Email to getString(R.string.tone_email),
        ToneStyle.Note to getString(R.string.tone_note),
        ToneStyle.Social to getString(R.string.tone_social),
    )
    // Don't show Custom in quick picker — use Settings for that
    tones.forEach { (tone, name) ->
        val tv = TextView(this).apply {
            text = name
            textSize = 14f
            setTextColor(
                if (tone == currentTone) {
                    resources.getColor(R.color.mic_idle, null)
                } else {
                    resources.getColor(R.color.key_text, null)
                },
            )
            val pad = (8 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            setOnClickListener {
                serviceScope.launch { preferencesManager.setToneStyle(tone) }
                popup.dismiss()
            }
        }
        container.addView(tv)
    }
}
```

In `showQuickSettings`, add reading the current tone:
```kotlin
val currentTone = preferencesManager.toneStyleFlow.first()
```

**Step 2: Build and verify**

Run: `./gradlew assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL.

**Step 3: Commit**

```bash
git add app/src/main/java/com/voxink/app/ime/VoxInkIME.kt
git commit -m "feat: add tone picker to IME quick-settings popup

Shows 5 built-in tones (excludes Custom) in the long-press
settings popup for quick tone switching during input."
```

---

## Task 7: Final build + test verification

**Step 1: Clean build**

Run: `./gradlew clean assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL.

**Step 2: Run all tests**

Run: `./gradlew testDebugUnitTest --no-daemon`
Expected: All tests PASS.

**Step 3: Verify tone flow**

Grep for consistency — ensure tone is threaded everywhere:
```bash
grep -r "ToneStyle" app/src/main/ --include="*.kt" -l
```
Expected files:
- `data/model/ToneStyle.kt`
- `data/model/RefinementPrompt.kt`
- `data/local/PreferencesManager.kt`
- `data/repository/LlmRepository.kt`
- `domain/usecase/RefineTextUseCase.kt`
- `ime/RecordingController.kt`
- `ime/VoxInkIME.kt`
- `ui/settings/SettingsUiState.kt`
- `ui/settings/SettingsViewModel.kt`
- `ui/settings/SettingsScreen.kt`

**Step 4: Commit if any fixes needed**

---

## Summary

| Task | What | Files |
|------|------|-------|
| 1 | `ToneStyle` sealed class | model + test |
| 2 | Per-language tone prompts in `RefinementPrompt` | model + test |
| 3 | Persist tone in `PreferencesManager` | data/local |
| 4 | Thread tone through call chain | Controller → UseCase → Repo → Prompt |
| 5 | Tone selector in Settings UI (FilterChips) | UI + strings |
| 6 | Tone picker in IME quick-settings popup | VoxInkIME |
| 7 | Final verification | build + tests |

### Design Notes

- **Casual is default** — existing prompts effectively match casual tone, so this is backward-compatible
- **Custom tone = existing behavior** — uses the custom prompt editor (unchanged)
- **Custom excluded from IME popup** — too complex for quick switching; use Settings for full custom prompt editing
- **Fallback to English** — languages without dedicated tone prompts fall back to English tone prompts (user can override with Custom)
- **Pro gating not in scope** — the discussion mentions tone as a Pro feature, but gating can be added later by checking `proStatus.isPro` before applying non-Casual tones
