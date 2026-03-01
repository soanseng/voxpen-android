# Copy-to-Clipboard Button + Expanded Voice Commands — Design Doc

Date: 2026-03-01

---

## Goal

Two small, high-impact features:

1. **Copy-to-clipboard button** — a copy icon on the refined row in the candidate bar, letting the user copy text without inserting it into the field.
2. **Expanded voice commands** — add editing actions (undo, select all, copy, paste, cut) and a "clear all" command to the existing voice command system.

---

## Feature 1: Copy-to-Clipboard Button

### Placement

Copy icon appears at the right end of the **refined row only** (`candidate_refined_row`). Also appears in the single-row `Result` state (`candidate_status_row`).

### Behavior

- Tap copy icon → `ClipboardManager.setPrimaryClip(text)` + short Toast ("Copied!" / "已複製")
- Tap the text area itself → inserts into the field (existing behavior unchanged)
- Copy icon only visible when text is present (`Refined` and `Result` states)

### Layout Changes

`candidate_refined_row` currently:
```
[ProgressBar (16dp)] [TextView weight=1]
```

After:
```
[ProgressBar (16dp)] [TextView weight=1] [ImageButton copy (24dp)]
```

`candidate_status_row` currently:
```
[ProgressBar (20dp)] [TextView weight=1]
```

After (only in `Result` state):
```
[ProgressBar (20dp)] [TextView weight=1] [ImageButton copy (24dp)]
```

### Files to Touch

| File | Change |
|------|--------|
| `res/layout/keyboard_view.xml` | Add `ImageButton` (`ic_content_copy`) to `candidate_refined_row` and `candidate_status_row` |
| `res/drawable/ic_content_copy.xml` | New vector drawable (Material content_copy icon) |
| `VoxPenIME.kt` | Bind copy buttons; set click listeners in `Refined` and `Result` states; hide in other states |
| `res/values/strings.xml` | Add `copied_to_clipboard` = "Copied!" |
| `res/values-zh-rTW/strings.xml` | Add `copied_to_clipboard` = "已複製" |

---

## Feature 2: Expanded Voice Commands

### New Commands

| Command | Trigger Words (zh / en / ja) | Action |
|---------|------------------------------|--------|
| Undo | 復原 / undo / 元に戻す | `performContextMenuAction(android.R.id.undo)` |
| Select All | 全選 / select all / 全て選択 / すべて選択 | `performContextMenuAction(android.R.id.selectAll)` |
| Copy | 複製 / copy / コピー | `performContextMenuAction(android.R.id.copy)` |
| Paste | 貼上 / paste / 貼り付け | `performContextMenuAction(android.R.id.paste)` |
| Cut | 剪下 / cut / 切り取り | `performContextMenuAction(android.R.id.cut)` |
| Clear All | 全部刪除 / clear all / clear / 全削除 | Select all → delete |

### Architecture Change

Current `VoiceCommand` sealed class carries an unused `keyCode: Int` constructor parameter. The `executeVoiceCommand()` method in `VoxPenIME` dispatches via `when` branches, not via `keyCode`. The new commands (`performContextMenuAction`) have no key code equivalent.

**Change**: Drop the `keyCode` parameter from `VoiceCommand` — it's dead code. Add new sealed objects for each command:

```kotlin
sealed class VoiceCommand {
    data object Enter : VoiceCommand()
    data object Backspace : VoiceCommand()
    data object Newline : VoiceCommand()
    data object Space : VoiceCommand()
    data object Undo : VoiceCommand()
    data object SelectAll : VoiceCommand()
    data object Copy : VoiceCommand()
    data object Paste : VoiceCommand()
    data object Cut : VoiceCommand()
    data object ClearAll : VoiceCommand()
}
```

### Execution Logic

In `VoxPenIME.executeVoiceCommand()`:

```kotlin
private fun executeVoiceCommand(command: VoiceCommand) {
    when (command) {
        VoiceCommand.Enter -> sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        VoiceCommand.Backspace -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        VoiceCommand.Newline -> currentInputConnection?.commitText("\n", 1)
        VoiceCommand.Space -> currentInputConnection?.commitText(" ", 1)
        VoiceCommand.Undo -> currentInputConnection?.performContextMenuAction(android.R.id.undo)
        VoiceCommand.SelectAll -> currentInputConnection?.performContextMenuAction(android.R.id.selectAll)
        VoiceCommand.Copy -> currentInputConnection?.performContextMenuAction(android.R.id.copy)
        VoiceCommand.Paste -> currentInputConnection?.performContextMenuAction(android.R.id.paste)
        VoiceCommand.Cut -> currentInputConnection?.performContextMenuAction(android.R.id.cut)
        VoiceCommand.ClearAll -> {
            currentInputConnection?.performContextMenuAction(android.R.id.selectAll)
            currentInputConnection?.commitText("", 1)
        }
    }
}
```

### Files to Touch

| File | Change |
|------|--------|
| `data/model/VoiceCommand.kt` | Drop `keyCode` param; add 6 new sealed objects |
| `ime/VoiceCommandRecognizer.kt` | Add trigger words for all new commands |
| `test/.../VoiceCommandRecognizerTest.kt` | Tests for each new trigger word |
| `VoxPenIME.kt` | Add `when` branches in `executeVoiceCommand()` |
| `README.md` | Update voice commands table |

---

## Testing

| Test | What |
|------|------|
| `VoiceCommandRecognizerTest` | Each new trigger word maps to correct command; case insensitive; whitespace trimmed |
| Manual device test | Copy button copies to clipboard; each voice command executes correctly |

---

## Out of Scope

- Copy button on Original row (user said refined only)
- Long-press behavior changes on candidate rows
- Redo command (Android `R.id.redo` support is inconsistent)
