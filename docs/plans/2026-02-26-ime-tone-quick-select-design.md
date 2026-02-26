# IME Tone Quick-Select Button Design

**Date**: 2026-02-26
**Status**: Approved

## Problem

Switching tone requires navigating to Settings or long-pressing the settings button to open Quick Settings. This is too many steps for a frequently-changed preference during IME use.

## Solution

Add a dedicated tone button to the IME key row that displays the current tone as an emoji and opens a tone selection popup on tap.

## Layout

```
┌──────────────────────────────────┐
│  Candidate bar (unchanged)       │
├──────────────────────────────────┤
│ 🌐 │ ⌫ │   🎤   │ 💬 │ ⏎ │ ⚙ │
└──────────────────────────────────┘
```

Key row weights: 🌐(1) ⌫(1) 🎤(2) 💬(1) ⏎(1) ⚙(1) — total 7 (was 6).

## Tone Button Spec

- **Widget**: `TextView` (displays emoji text, not an icon)
- **Display**: Pure emoji reflecting current tone
- **Interaction**: Tap opens vertical PopupWindow
- **Reactivity**: Observes `preferencesManager.toneStyleFlow`

## Emoji Mapping

| ToneStyle    | Emoji | Popup (zh-TW) | Popup (en)       |
|--------------|-------|---------------|------------------|
| Casual       | 💬    | 💬 隨意       | 💬 Casual        |
| Professional | 💼    | 💼 專業       | 💼 Professional  |
| Email        | 📧    | 📧 信件       | 💼 Email         |
| Note         | 📝    | 📝 筆記       | 📝 Note          |
| Social       | 📱    | 📱 社交       | 📱 Social        |
| Custom       | ⚙     | ⚙ 自訂        | ⚙ Custom         |

## Popup

Vertical PopupWindow (same style as existing Quick Settings). Each row shows emoji + full name. Current selection highlighted with `mic_idle` color.

## File Changes

| File | Change |
|------|--------|
| `ToneStyle.kt` | Add `emoji` property to sealed class |
| `keyboard_view.xml` | Add `btn_tone` TextView between mic and enter |
| `VoxInkIME.kt` | Tone button binding, popup, Flow observation; remove tone from quick settings |
| `strings.xml` | Add tone popup labels and tooltip |
| `values-zh-rTW/strings.xml` | Chinese translations for tone popup labels |

## What stays the same

- Quick Settings keeps language + refinement toggle (tone section removed)
- RecordingController, RefinementPrompt, PreferencesManager unchanged
- Keyboard tooltip overlay gains one entry for the new button
