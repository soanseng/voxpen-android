# Phase 2: LLM Refinement + Dual Display

## Overview
Add LLM-based text refinement to the voice keyboard. After STT returns raw text, simultaneously send it to Groq LLM for polishing (filler removal, punctuation, grammar). Display both "Original" and "Refined" versions in a dual candidate bar. User taps to select which to commit.

## Architecture

```
STT Result → ImeUiState.Result(text)
    ├─ Display "Original" in candidate bar immediately
    └─ Send to LlmRepository.refine(text, language)
         └─ ImeUiState.Refining(original, refined?)
              └─ ImeUiState.Refined(original, refined)
                   └─ User taps Original or Refined → commitText()
```

## Tasks

### Part A: LLM Data Layer

#### Task 1: Add LLM Chat API Models
**TDD RED**: `app/src/test/java/com/voxink/app/data/remote/ChatCompletionTest.kt`
- Test ChatMessage serialization (role, content)
- Test ChatCompletionRequest serialization (model, messages, temperature, max_tokens)
- Test ChatCompletionResponse deserialization (id, choices[].message.content)

**GREEN**:
- `app/src/main/java/com/voxink/app/data/remote/ChatCompletion.kt` — @Serializable data classes
- Add `chatCompletion` endpoint to `GroqApi.kt`

**Verify**: `./gradlew test` — new tests pass

#### Task 2: Add Refinement Prompts
**TDD RED**: `app/src/test/java/com/voxink/app/data/model/RefinementPromptTest.kt`
- Test prompt lookup by SttLanguage (zh, en, ja, auto/mixed)
- Test each prompt is non-empty and contains expected keywords

**GREEN**:
- `app/src/main/java/com/voxink/app/data/model/RefinementPrompt.kt` — object with `forLanguage(SttLanguage): String`
- Contains the 4 system prompts from CLAUDE.md (zh-TW, en, ja, mixed)

**Verify**: `./gradlew test`

#### Task 3: TDD — LlmRepository
**TDD RED**: `app/src/test/java/com/voxink/app/data/repository/LlmRepositoryTest.kt`
- Test successful refinement returns refined text
- Test request includes correct model, temperature, system prompt
- Test IOException returns failure
- Test empty text returns failure

**GREEN**:
- `app/src/main/java/com/voxink/app/data/repository/LlmRepository.kt`
  - `suspend fun refine(text: String, language: SttLanguage, apiKey: String): Result<String>`
  - Uses GroqApi.chatCompletion with model=llama-3.3-70b-versatile, temp=0.3

**Verify**: `./gradlew test`

### Part B: Domain Layer

#### Task 4: TDD — RefineTextUseCase
**TDD RED**: `app/src/test/java/com/voxink/app/domain/usecase/RefineTextUseCaseTest.kt`
- Test successful refinement returns cleaned text
- Test failure propagates error
- Test with refinement disabled returns null/skip

**GREEN**:
- `app/src/main/java/com/voxink/app/domain/usecase/RefineTextUseCase.kt`
  - `suspend operator fun invoke(text: String, language: SttLanguage, apiKey: String): Result<String>`
  - Delegates to LlmRepository

**Verify**: `./gradlew test`

### Part C: IME Integration

#### Task 5: Extend ImeUiState for Refinement
**TDD RED**: Update `app/src/test/java/com/voxink/app/ime/ImeUiStateTest.kt`
- Test Refining state holds original text
- Test Refined state holds both original and refined text

**GREEN**: Update `app/src/main/java/com/voxink/app/ime/ImeUiState.kt`
- Add `Refining(val original: String)` — LLM processing
- Add `Refined(val original: String, val refined: String)` — both available

**Verify**: `./gradlew test`

#### Task 6: Update RecordingController for Refinement Pipeline
**TDD RED**: Update `app/src/test/java/com/voxink/app/ime/RecordingControllerTest.kt`
- Test after STT success, state transitions to Refining then Refined
- Test refinement failure still shows Result with original text
- Test refinement disabled goes straight to Result

**GREEN**: Update `app/src/main/java/com/voxink/app/ime/RecordingController.kt`
- After STT Result → if refinement enabled, emit Refining → call RefineTextUseCase → emit Refined
- If refinement disabled or fails, emit Result(original) as before

Constructor needs: `TranscribeAudioUseCase`, `RefineTextUseCase`, `ApiKeyManager`, `PreferencesManager`

**Verify**: `./gradlew test`

#### Task 7: Add Refinement Settings to PreferencesManager
**TDD RED**: Update `app/src/test/java/com/voxink/app/data/local/PreferencesManagerTest.kt`
- Test refinementEnabledFlow default is true
- Test setRefinementEnabled persists

**GREEN**: Update `app/src/main/java/com/voxink/app/data/local/PreferencesManager.kt`
- Add `refinementEnabledFlow: Flow<Boolean>` (default: true)
- Add `suspend fun setRefinementEnabled(enabled: Boolean)`

**Verify**: `./gradlew test`

### Part D: UI Layer

#### Task 8: Update Keyboard Layout for Dual Candidate Bar
**GREEN**: Update `app/src/main/res/layout/keyboard_view.xml`
- Replace single candidate row with dual layout:
  - `candidate_original` (TextView, clickable) — shows "Original: ..."
  - `candidate_refined` (LinearLayout with ProgressBar + TextView, clickable) — shows "Refined: ..."
- Both rows hidden by default, shown when result available
- Add string resources for "Original" / "Refined" labels (en + zh-TW)

**Verify**: Build succeeds

#### Task 9: Update VoxInkIME for Dual Display
**GREEN**: Update `app/src/main/java/com/voxink/app/ime/VoxInkIME.kt`
- Handle new states: Refining (show original + spinner on refined line), Refined (show both, both tappable)
- Click on original row → commitText(original)
- Click on refined row → commitText(refined)
- Inject RefineTextUseCase via EntryPoint
- Read refinementEnabled from PreferencesManager

**Verify**: Build succeeds

#### Task 10: Update Settings UI
**TDD RED**: Update `app/src/test/java/com/voxink/app/ui/settings/SettingsViewModelTest.kt`
- Test refinement toggle state reflects PreferencesManager
- Test setRefinementEnabled updates state

**GREEN**:
- Update `SettingsUiState.kt` — add `refinementEnabled: Boolean`
- Update `SettingsViewModel.kt` — collect refinementEnabledFlow, add setRefinementEnabled()
- Update `SettingsScreen.kt` — add refinement toggle switch in new section

**Verify**: `./gradlew test`

#### Task 11: Add Phase 2 String Resources
**GREEN**: Update both `values/strings.xml` and `values-zh-rTW/strings.xml`
- `candidate_original` / "Original" / "原文"
- `candidate_refined` / "Refined" / "潤稿"
- `settings_refinement_section` / "Text Refinement" / "文字潤稿"
- `settings_refinement_toggle` / "Enable refinement" / "啟用智慧潤稿"
- `refining` / "Refining..." / "潤稿中…"

**Verify**: Build succeeds

### Part E: Verification

#### Task 12: Full Build Verification
- `./gradlew ktlintCheck detekt test assembleDebug`
- All tests pass, all lint clean
- Merge to dev, then to main
- Tag `v0.2.0`

## Dependencies
- Task 1-2: Independent (parallel OK)
- Task 3: Depends on Task 1, 2
- Task 4: Depends on Task 3
- Task 5: Independent
- Task 6: Depends on Task 4, 5, 7
- Task 7: Independent
- Task 8: Independent
- Task 9: Depends on Task 5, 6, 8
- Task 10: Depends on Task 7
- Task 11: Independent
- Task 12: Depends on all above
