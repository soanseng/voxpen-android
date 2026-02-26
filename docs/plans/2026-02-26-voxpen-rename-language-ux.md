# VoxPen Rename + Language UX Improvements

**Date:** 2026-02-26
**Status:** Approved

## Overview

Four workstreams: rename app to VoxPen, add emoji flags to STT language selector, add IME language quick-switch via long-press, and add onboarding tips step.

---

## 1. App Rename (VoxInk -> VoxPen)

### Scope

| Area | From | To |
|------|------|----|
| User-facing strings (en) | VoxInk, VoxInk Voice | VoxPen, VoxPen Voice |
| User-facing strings (zh-TW) | 語墨, 語墨語音 | 語墨, 語墨語音 (unchanged) |
| Package name | `com.voxink.app` | `com.voxpen.app` |
| Directory structure | `java/com/voxink/app/` | `java/com/voxpen/app/` |
| Class names | `VoxInkApplication`, `VoxInkIME`, `VoxInkTheme`, etc. | `VoxPenApplication`, `VoxPenIME`, `VoxPenTheme`, etc. |
| Resource names | `Theme.VoxInk`, `voxink_primary`, `VoxInkPurple` | `Theme.VoxPen`, `voxpen_primary`, `VoxPenPurple` |
| Gradle | `rootProject.name = "VoxInk"`, `namespace/applicationId = "com.voxink.app"` | `"VoxPen"`, `"com.voxpen.app"` |
| GitHub repo | `voxink-android` | `voxpen-android` |
| CLAUDE.md | All VoxInk references | VoxPen references |

### Approach

Mechanical find-and-replace + directory rename. GH repo rename last via `gh repo rename`.

### Risk

`applicationId` change = new app on Play Store. Not a concern since app isn't published yet.

---

## 2. STT Language Selector with Emoji Flags

### Change

Add `emoji` property to `SttLanguage` sealed class. Prepend emoji flag in Settings language selector labels.

### Mapping

| Language | Emoji | Display (en) | Display (zh-TW) |
|----------|-------|-------------|-----------------|
| Auto | 🌐 | 🌐 Auto-detect | 🌐 自動偵測 |
| Chinese | 🇹🇼 | 🇹🇼 Chinese (Traditional) | 🇹🇼 中文（繁體） |
| English | 🇺🇸 | 🇺🇸 English | 🇺🇸 英文 |
| Japanese | 🇯🇵 | 🇯🇵 Japanese | 🇯🇵 日文 |
| Korean | 🇰🇷 | 🇰🇷 Korean | 🇰🇷 韓文 |
| French | 🇫🇷 | 🇫🇷 French | 🇫🇷 法文 |
| German | 🇩🇪 | 🇩🇪 German | 🇩🇪 德文 |
| Spanish | 🇪🇸 | 🇪🇸 Spanish | 🇪🇸 西班牙文 |
| Vietnamese | 🇻🇳 | 🇻🇳 Vietnamese | 🇻🇳 越南文 |
| Indonesian | 🇮🇩 | 🇮🇩 Indonesian | 🇮🇩 印尼文 |
| Thai | 🇹🇭 | 🇹🇭 Thai | 🇹🇭 泰文 |

### Implementation

- Add `val emoji: String` to `SttLanguage` sealed class
- Update `LanguageSection` composable to prepend `"${language.emoji} "` to labels

---

## 3. IME Language Quick-Switch (Long-press Globe)

### Behavior

| Gesture | Action |
|---------|--------|
| Tap 🌐 | `switchToPreviousInputMethod()` (unchanged) |
| Long-press 🌐 | Opens language selection popup |

### Popup Design

```
+------------------+
| 🌐 Auto-detect   |
| 🇹🇼 中文         |
| 🇺🇸 English      |
| 🇯🇵 日本語       |
+------------------+
```

- 4 primary languages only (full list in Settings)
- Current selection highlighted with checkmark
- Tapping an option calls `preferencesManager.setLanguage()` and dismisses popup
- 🌐 button keeps `ic_language` drawable (does not change to flag)

### Wire-up

- Add `setOnLongClickListener` on `btn_switch` in `VoxInkIME.bindButtons()`
- Create `showLanguagePopup()` method following `showTonePopup()` / `createQuickSettingsContainer()` pattern
- Remove language options from `showQuickSettings()` popup (keep only refinement toggle)

---

## 4. Onboarding TIPS Step

### Flow Change

```
Before: WELCOME -> API_KEY -> ENABLE_KEYBOARD -> GRANT_PERMISSION -> PRACTICE -> DONE
After:  WELCOME -> API_KEY -> ENABLE_KEYBOARD -> GRANT_PERMISSION -> PRACTICE -> TIPS -> DONE
```

### TIPS Step Content

**English:**
> **Keyboard Tips**
> - Long-press 🌐 to quickly switch dictation language
> - Long-press ⚙ to toggle refinement on/off
> - Tap the tone button (💬) to change writing style

**繁體中文:**
> **鍵盤使用提示**
> - 長按 🌐 快速切換聽寫語言
> - 長按 ⚙ 開關文字潤飾
> - 點擊語氣按鈕（💬）切換寫作風格

### Implementation

- Add `TIPS` to `OnboardingStep` enum between `PRACTICE` and `DONE`
- Add `TipsStep` composable in `OnboardingScreen.kt` — static content, Next/Back only
- Update progress indicator (6 -> 7 steps)
- Add string resources for tips content (both locales)
