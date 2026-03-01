# Translation Quick-Switch Indicator Row

## Problem

Translation mode requires navigating to Settings to change the target language. Users need a faster way to toggle translation on/off and switch target languages directly from the keyboard.

## Solution

Add a tappable **translation indicator row** at the top of the candidate bar. One tap cycles through target languages; an × button turns translation off.

## UI Layout

```
┌─────────────────────────────────────┐
│ 🔄 說中文 → English            [×] │  ← translation_indicator_row (28dp)
├─────────────────────────────────────┤
│  🔵 Original / Status row          │  ← existing candidate rows
│  ✨ Refined / Translated            │
├─────────────────────────────────────┤
│ 🌐 │  ⌫  │   🎤   │ 💬 │ ⏎ │ ⚙️  │
└─────────────────────────────────────┘
```

- `translation_indicator_row`: horizontal LinearLayout, 28dp height
  - `TextView` (`translation_label`): shows `🔄 說中文 → English`, tappable to cycle
  - `ImageButton` (`btn_translation_close`): × icon, tappable to disable translation
- When translation is disabled: entire row `visibility = GONE`

## Smart Language Filtering

Available targets are filtered based on the current STT language to avoid nonsensical "translate Chinese to Chinese" options:

```
STT = Chinese  → cycle: EN → Japanese → Off
STT = English  → cycle: Chinese → Japanese → Off
STT = Japanese → cycle: EN → Chinese → Off
STT = Auto     → cycle: EN → Chinese → Japanese → Off
```

Logic: `listOf(EN, Chinese, Japanese).filter { it != sttLanguage }` (Auto keeps all three).

## Indicator Text Format

Dynamic text based on STT and target language:

| STT Language | Target | Display Text |
|---|---|---|
| Chinese | English | 🔄 說中文 → English |
| Chinese | Japanese | 🔄 說中文 → 日本語 |
| English | Chinese | 🔄 Speak EN → 中文 |
| English | Japanese | 🔄 Speak EN → 日本語 |
| Japanese | English | 🔄 日本語 → English |
| Japanese | Chinese | 🔄 日本語 → 中文 |
| Auto | English | 🔄 翻譯 → English |
| Auto | Chinese | 🔄 翻譯 → 中文 |
| Auto | Japanese | 🔄 翻譯 → 日本語 |

## State Management

- Visibility driven by `PreferencesManager.translationEnabledFlow`
- Target language driven by `PreferencesManager.translationTargetLanguageFlow`
- Tap label → compute next target from filtered list → `setTranslationTargetLanguage(next)`
  - If current target is last in list → `setTranslationEnabled(false)` (cycle to Off)
  - If translation was Off → `setTranslationEnabled(true)` + set first target
- Tap × → `setTranslationEnabled(false)`
- STT language changes → update indicator text, recompute filtered targets
- Quick Settings popup translation toggle remains; toggling on uses last target language

## Files to Modify

| File | Change |
|---|---|
| `keyboard_view.xml` | Add `translation_indicator_row` at top of `candidate_bar` |
| `VoxPenIME.kt` | Bind views, add Flow collectors, click handlers, update logic |
| `strings.xml` (en) | Add translation indicator format strings |
| `strings.xml` (zh-TW) | Add translation indicator format strings |
| `ic_close.xml` | Close icon drawable (12dp, key_text color) |

## Files NOT Changed

- `PreferencesManager.kt` — already has `translationEnabled` + `translationTargetLanguage` flows
- `RecordingController.kt` — already listens via Flow; next recording auto-uses new settings
- `ImeUiState.kt` — indicator is preference-driven, not recording-state-driven
- `TranslationPrompt.kt` — no changes needed
- `LlmRepository.kt` — no changes needed
