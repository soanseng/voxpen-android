# BYOK Custom STT & LLM Provider Parity — Implementation Plan

> **Date:** 2026-07-14
>
> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make Android's Custom STT endpoint and Custom LLM refinement providers robust and usable, matching (or exceeding) desktop's BYOK experience. Specifically:

- Support self-hosted / local OpenAI-compatible servers **without requiring an API key**.
- Fix incomplete wiring for custom LLM model + base URL (especially Speak-to-Edit).
- Allow overriding the model name even for built-in providers (Groq / OpenAI / OpenRouter).
- Improve UI/UX for custom providers (hide unnecessary key fields, better hints).
- Ensure all code paths (live dictation, file transcription, speak-to-edit) consistently respect custom settings.

**Current State (Inventory Summary):**

Desktop has working custom support with keyless handling for `custom` (and URL-as-provider) cases, shared `custom_base_url`, and free-text model override.

Android declares `SttProvider.Custom` + `LlmProvider.Custom` with dedicated storage (`customSttBaseUrl`, `customBaseUrl`, `customLlmModel`), but has critical gaps:
- Blank-key early returns block custom usage.
- Always sends `Bearer <key>` even when empty.
- `VoxPenIME.performEditWithLlm()` ignores `customLlmModel`.
- No model override for non-Custom LLM providers.
- Key input fields always shown for Custom.
- STT key check happens before knowing whether the provider is keyless.

See previous analysis for full file-by-file comparison.

**Architecture Decisions:**

- Keep **separate** custom URLs for STT vs LLM (Android's current approach is more flexible than desktop's single shared field).
- Treat Custom providers as **key-optional**. When key is blank for Custom, proceed with no (or empty) Authorization header.
- Add a lightweight "model override" concept for LLM (non-Custom providers can still accept a free-text model name).
- Centralize keyless / custom detection in one place (`ApiKeyManager` or a small helper) to avoid duplication.
- UI follows desktop pattern: hide key input for Custom; show prominent Base URL + Model fields.
- Reuse existing `PreferencesManager` flows + `ApiKeyManager` methods (already exist for custom fields).

**Tech Stack:** Kotlin, Jetpack Compose, DataStore, EncryptedSharedPreferences, Retrofit + OkHttp, Hilt, JUnit 5 + MockK + Truth, MockWebServer for API tests.

## Execution Status (as of 2026-07-14)

**Automated verification completed successfully:**
- `assembleDebug` + all affected unit tests: **BUILD SUCCESSFUL**
- Key test classes (0 failures):
  - ApiKeyManagerTest (33 tests)
  - RecordingControllerTest (20 tests)
  - SttRepositoryTest / LlmRepositoryTest
  - TranscriptionViewModelTest
- All new keyless helpers (`isKeyRequiredFor*`, `getEffective*ApiKey`) implemented and tested.
- Custom providers now support blank keys (keyless local servers).
- Speak-to-Edit fixed to use `customLlmModel`.
- Model override support added for built-in LLM providers.
- UI: key fields hidden for Custom + improved hints.
- Updated mocks in tests, added new keyless test cases.
- Checklist for manual device verification prepared (see `docs/verification/custom-byok-manual-checklist.md`).

**Next recommended steps:**
- Run manual verification on real device using the checklist.
- Full `./gradlew build` (release signing may need local keystore).
- Update CLAUDE.md / README if desired after manual sign-off.

---

## Task 1: Centralize keyless provider logic + relax blank-key checks

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/data/local/ApiKeyManager.kt`
- Modify: `app/src/main/java/com/voxpen/app/data/repository/SttRepository.kt`
- Modify: `app/src/main/java/com/voxpen/app/data/repository/LlmRepository.kt`
- Modify: `app/src/main/java/com/voxpen/app/ime/RecordingController.kt`
- Modify: `app/src/main/java/com/voxpen/app/ime/VoxPenIME.kt`
- Modify: `app/src/main/java/com/voxpen/app/ui/transcription/TranscriptionScreen.kt`
- Test: `app/src/test/java/com/voxpen/app/data/local/ApiKeyManagerTest.kt` (or create if missing)
- Test: Update relevant Repository tests

**Step 1: Add helper in ApiKeyManager**

Add a public method after the existing custom URL methods:

```kotlin
fun isKeyRequiredForStt(provider: SttProvider): Boolean =
    provider != SttProvider.Custom

fun isKeyRequiredForLlm(provider: LlmProvider): Boolean =
    provider != LlmProvider.Custom
```

Also add a helper that returns the effective key (empty string allowed for custom):

```kotlin
fun getEffectiveSttApiKey(provider: SttProvider): String? =
    getSttApiKey(provider)

fun getEffectiveLlmApiKey(provider: LlmProvider): String? =
    getApiKey(provider)
```

**Step 2: Update SttRepository**

In `transcribe(...)`:
- Remove or relax the `if (apiKey.isBlank())` early return **when `provider == Custom`**.
- When sending the header for Custom + blank key, send an empty or minimal header that many local servers accept (e.g. still send `Bearer ` or make the header conditional).

Update `executeTranscription` to accept optional auth or handle empty key gracefully for custom.

**Step 3: Update LlmRepository (refine + editText)**

Same relaxation for `provider == LlmProvider.Custom && apiKey.isBlank()`.

In the actual Retrofit call, only add the Authorization header when the key is non-blank.

**Step 4: Update call sites (IME + Transcription)**

- `RecordingController`: Move the "apiKey blank" error **after** determining provider, and only error if `isKeyRequired` and blank.
- `VoxPenIME.performEditWithLlm`: Same logic.
- File transcription paths: Allow blank for custom (they already use `.orEmpty()` in places).

**Step 5: Write / update tests**

Add tests that verify:
- Custom STT with blank key does not throw "API key not configured".
- Custom LLM with blank key proceeds.
- Non-custom still requires key.

Run: `./gradlew testDebugUnitTest --tests "*ApiKeyManagerTest*" --tests "*SttRepositoryTest*" --tests "*LlmRepositoryTest*"`

---

## Task 2: Fix Speak-to-Edit to respect custom LLM model + base URL

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/ime/VoxPenIME.kt` (the `performEditWithLlm` function)
- Modify: `app/src/main/java/com/voxpen/app/domain/usecase/EditTextUseCase.kt` (if needed for clarity)
- Test: Add or update IME-related controller test if one covers edit path

**Step 1: Collect customLlmModel in the edit path**

Inside `performEditWithLlm`:

```kotlin
val llmProvider = ...
val llmApiKey = ...
val resolvedModel = if (llmProvider == LlmProvider.Custom) {
    preferencesManager.customLlmModelFlow.first().ifBlank {
        preferencesManager.llmModelFlow.first()
    }
} else {
    preferencesManager.llmModelFlow.first()
}
val customBaseUrl = if (llmProvider == LlmProvider.Custom) {
    apiKeyManager.getCustomBaseUrl()
} else null
```

Pass `resolvedModel` instead of raw `llmModel`.

**Step 2: Ensure EditTextUseCase accepts and forwards the model correctly**

Current signature already takes `model`. Just make the caller pass the resolved value.

**Step 3: Test manually later** (see verification section).

---

## Task 3: Allow model name override for built-in LLM providers

**Goal:** Match desktop behavior where you can type a custom model string even when provider = Groq / OpenAI / OpenRouter.

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/data/model/LlmProvider.kt` (optional small helper)
- Modify: `app/src/main/java/com/voxpen/app/ui/settings/SettingsUiState.kt`
- Modify: `app/src/main/java/com/voxpen/app/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/voxpen/app/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/voxpen/app/ime/RecordingController.kt` (and file tx paths if they don't already)
- Modify: `app/src/main/res/values/strings.xml` + zh-TW
- Test: `app/src/test/java/com/voxpen/app/ui/settings/SettingsViewModelTest.kt` (update)

**Step 1: Add override field to state**

Add to `SettingsUiState`:

```kotlin
val llmModelOverride: String = "",
```

Flow it from a new (or reuse) preference if we want persistence across sessions. For simplicity and parity with desktop, we can persist the last free-text value in `llmModel` when it doesn't match the curated list (current behavior for Custom already does something similar).

Preferred approach (simple):
- When provider is **not** Custom, still allow the text field to set a free-form `llmModel`.
- Do **not** force `setLlmModel(provider.defaultModelId)` on every provider change.
- In places that resolve the final model, prefer an override if present and non-empty.

For cleanest UX, introduce a separate persisted `llmModelOverride` (or just document that typing in the model field works).

**Step 2: Update Settings UI for LLM section**

In the non-Custom branch (currently `ProviderModelList`):
- Keep the radio list.
- Below it (or as replacement when user types), add an `OutlinedTextField` labeled "Custom model name (optional override)" with hint like desktop.

When the override field has text, treat it as the effective model (even if a radio is selected).

Update `setLlmModel` calls accordingly.

**Step 3: Update model resolution sites**

Ensure `RecordingController`, file transcription, and edit path use the effective model (the one stored in `llmModel` after user input, plus any override logic).

**Step 4: Prevent destructive reset on provider switch (for non-Custom)**

In `setLlmProvider`, only reset to default when the new provider's curated list does not contain the current model.

---

## Task 4: Improve Custom provider UI (hide keys, better hints)

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`

**Step 1: Conditionally show key fields**

Wrap the key input sections:

```kotlin
if (state.sttProvider != SttProvider.Custom) {
    SttProviderApiKeyField(...)
}
```

Same for LLM:

```kotlin
if (state.llmProvider != LlmProvider.Custom && state.llmProvider != LlmProvider.Groq /* if Groq is primary */) {
    ProviderApiKeyField(...)
}
```

For Groq we may still want the legacy key area, but for other + Custom, hide when appropriate.

**Step 2: Add / improve hint strings**

Add:

```xml
<string name="provider_custom_key_optional">API key (optional for local servers)</string>
<string name="provider_custom_stt_hint">Enter base URL for self-hosted Whisper (OpenAI-compatible). Leave key blank if server does not require auth.</string>
<string name="provider_custom_llm_hint">Enter base URL (e.g. http://localhost:11434/v1/) and model name (e.g. llama3.1:8b).</string>
```

Update existing `settings_custom_stt_url_hint` and `provider_custom_*` labels if needed.

Show the new hints under the URL fields.

**Step 3: Rebuild and check layout**

`./gradlew assembleDebug`

---

## Task 5: Ensure file transcription and all paths are consistent

**Files:**
- Review / lightly modify: `app/src/main/java/com/voxpen/app/ui/transcription/TranscriptionScreen.kt`
- Review: `app/src/main/java/com/voxpen/app/domain/usecase/TranscribeFileUseCase.kt`
- `LlmRepository` and `SttRepository` already receive the custom values from the screen.

**Step 1:** Verify that the same resolved model + customBaseUrl logic used in RecordingController is applied (or call a shared resolver).

**Step 2:** Add a small shared utility if duplication grows:

```kotlin
// e.g. in util or a small SettingsResolver
fun resolveEffectiveLlmModel(provider: LlmProvider, llmModel: String, customLlmModel: String): String
```

Keep it minimal per Rule 2 (Simplicity First).

---

## Task 6: Update documentation and strings (i18n)

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`
- Modify: `CLAUDE.md` (under STT / LLM providers section)
- Optional: `README.md` / `README.zh-TW.md` if high-level feature list needs update
- Optional: Add note to `docs/plans/2026-02-26-multi-provider-llm.md` or ROADMAP

**Step 1:** Add the new hint strings (Task 4).

**Step 2:** In CLAUDE.md, update the "STT Providers" and "LLM Refinement" sections to document:
- Custom = key optional
- Separate custom STT URL vs LLM base URL
- Model override supported

**Step 3:** Rebuild to surface any missing string references.

---

## Task 7: Tests, build, and verification

**Build verification (after every significant change):**

```bash
cd /home/scipio/projects/voxpen-android
./gradlew build
./gradlew testDebugUnitTest
./gradlew ktlintCheck   # or whatever lint the project uses
```

**Unit test additions:**
- Keyless behavior for Custom providers.
- Model resolution logic (custom vs override).
- Factory creation for custom base URLs.

**Manual verification checklist (must run on real device or good emulator):**

1. Custom STT (no key)
   - Set STT → Custom, enter a working OpenAI-compatible Whisper base URL.
   - Leave API key blank.
   - Record → should transcribe successfully.

2. Custom STT (with key if server requires one)

3. Custom LLM Refine (no key)
   - Set LLM → Custom, enter Ollama / LiteLLM / compatible base URL + model.
   - Leave key blank.
   - Record with refinement on → refined text appears.

4. Speak-to-Edit with Custom LLM
   - Select text in another app.
   - Enable Edit Mode.
   - Speak edit instruction using Custom LLM.
   - Verify it uses the custom model (check logs or behavior).

5. Model override on built-in provider
   - Choose Groq as LLM provider.
   - Type a different valid model name in the override field.
   - Verify the request uses the typed model (via logs or provider dashboard).

6. File transcription with custom providers (both STT and LLM).

7. Switching providers does not lose previously entered custom values.

8. Error messages are reasonable when custom URL is wrong.

**Commit strategy:** Small, focused commits per task (or sub-task). Use conventional messages: `fix(ime): use customLlmModel in performEditWithLlm`, `feat(settings): hide API key for Custom providers`, etc.

---

## Success Criteria

- A user can successfully use a local/self-hosted STT server and a local LLM (Ollama etc.) with **zero or optional** API key.
- Speak-to-Edit works with Custom LLM settings.
- Power users can specify arbitrary model names on any LLM provider.
- No regressions for Groq / OpenAI / OpenRouter users.
- All three main entry points (IME live, Speak-to-Edit, File Tx) behave consistently.
- UI is clear and matches the "BYOK + self-host friendly" promise in CLAUDE.md and README.

---

## Out of Scope (for this plan)

- Adding full "local whisper.cpp" on-device STT (desktop-only for now).
- Audio ducking, mic device selection, recording time limits (desktop-specific).
- "Listen to My Command" advanced feature.
- Unifying custom_base_url storage with desktop (keep separate for now).

---

**Next step after plan approval:** Use executing-plans skill (or follow tasks sequentially) and verify with real custom endpoints.

This plan follows the project's TDD + small-step + checkpoint culture.