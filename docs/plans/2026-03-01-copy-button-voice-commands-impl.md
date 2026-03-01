# Copy-to-Clipboard Button + Expanded Voice Commands — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a copy-to-clipboard button on the refined candidate row and expand voice commands with editing actions (undo, select all, copy, paste, cut, clear all).

**Architecture:** Two independent features sharing a commit history. Feature 1 adds an `ImageButton` to `keyboard_view.xml` and wires it in `VoxPenIME`. Feature 2 refactors `VoiceCommand` to drop the unused `keyCode` parameter, adds 6 new sealed objects, maps trigger words in `VoiceCommandRecognizer`, and handles execution via `InputConnection.performContextMenuAction()`.

**Tech Stack:** Android XML layout, Kotlin sealed classes, `ClipboardManager`, `InputConnection`, JUnit 5, Truth assertions.

---

### Task 1: Refactor VoiceCommand — drop unused `keyCode` parameter

The existing `VoiceCommand` sealed class carries a `keyCode: Int` constructor parameter that is never used in `executeVoiceCommand()`. Remove it before adding new commands that have no key code equivalent.

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/data/model/VoiceCommand.kt`
- Test: `app/src/test/java/com/voxpen/app/ime/VoiceCommandRecognizerTest.kt` (existing — must still pass)

**Step 1: Modify VoiceCommand.kt — remove `keyCode` parameter**

Replace the entire file with:

```kotlin
package com.voxpen.app.data.model

sealed class VoiceCommand {
    /** Sends Enter / submits the text field */
    data object Enter : VoiceCommand()

    /** Deletes the character before cursor */
    data object Backspace : VoiceCommand()

    /** Inserts a newline character (for multi-line fields) */
    data object Newline : VoiceCommand()

    /** Inserts a space character */
    data object Space : VoiceCommand()
}
```

**Step 2: Run existing tests to verify nothing breaks**

Run: `./gradlew :app:testReleaseUnitTest --tests "com.voxpen.app.ime.VoiceCommandRecognizerTest" --no-daemon -q`
Expected: All 15 tests PASS (no code references `keyCode`)

**Step 3: Run full build to catch any other references**

Run: `./gradlew :app:assembleRelease --no-daemon -q 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/voxpen/app/data/model/VoiceCommand.kt
git commit -m "refactor: drop unused keyCode param from VoiceCommand sealed class"
```

---

### Task 2: Add new VoiceCommand types + trigger words (TDD)

Add 6 new voice commands: Undo, SelectAll, Copy, Paste, Cut, ClearAll.

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/data/model/VoiceCommand.kt`
- Modify: `app/src/main/java/com/voxpen/app/ime/VoiceCommandRecognizer.kt`
- Modify: `app/src/test/java/com/voxpen/app/ime/VoiceCommandRecognizerTest.kt`

**Step 1: Write failing tests**

Add these tests to `VoiceCommandRecognizerTest.kt`:

```kotlin
// --- Undo ---
@Test
fun `should recognize 復原 as Undo`() {
    assertThat(VoiceCommandRecognizer.recognize("復原")).isEqualTo(VoiceCommand.Undo)
}

@Test
fun `should recognize undo as Undo`() {
    assertThat(VoiceCommandRecognizer.recognize("undo")).isEqualTo(VoiceCommand.Undo)
}

@Test
fun `should recognize 元に戻す as Undo`() {
    assertThat(VoiceCommandRecognizer.recognize("元に戻す")).isEqualTo(VoiceCommand.Undo)
}

// --- Select All ---
@Test
fun `should recognize 全選 as SelectAll`() {
    assertThat(VoiceCommandRecognizer.recognize("全選")).isEqualTo(VoiceCommand.SelectAll)
}

@Test
fun `should recognize select all as SelectAll`() {
    assertThat(VoiceCommandRecognizer.recognize("select all")).isEqualTo(VoiceCommand.SelectAll)
}

@Test
fun `should recognize 全て選択 as SelectAll`() {
    assertThat(VoiceCommandRecognizer.recognize("全て選択")).isEqualTo(VoiceCommand.SelectAll)
}

@Test
fun `should recognize すべて選択 as SelectAll`() {
    assertThat(VoiceCommandRecognizer.recognize("すべて選択")).isEqualTo(VoiceCommand.SelectAll)
}

// --- Copy ---
@Test
fun `should recognize 複製 as Copy`() {
    assertThat(VoiceCommandRecognizer.recognize("複製")).isEqualTo(VoiceCommand.Copy)
}

@Test
fun `should recognize copy as Copy`() {
    assertThat(VoiceCommandRecognizer.recognize("copy")).isEqualTo(VoiceCommand.Copy)
}

@Test
fun `should recognize コピー as Copy`() {
    assertThat(VoiceCommandRecognizer.recognize("コピー")).isEqualTo(VoiceCommand.Copy)
}

// --- Paste ---
@Test
fun `should recognize 貼上 as Paste`() {
    assertThat(VoiceCommandRecognizer.recognize("貼上")).isEqualTo(VoiceCommand.Paste)
}

@Test
fun `should recognize paste as Paste`() {
    assertThat(VoiceCommandRecognizer.recognize("paste")).isEqualTo(VoiceCommand.Paste)
}

@Test
fun `should recognize 貼り付け as Paste`() {
    assertThat(VoiceCommandRecognizer.recognize("貼り付け")).isEqualTo(VoiceCommand.Paste)
}

// --- Cut ---
@Test
fun `should recognize 剪下 as Cut`() {
    assertThat(VoiceCommandRecognizer.recognize("剪下")).isEqualTo(VoiceCommand.Cut)
}

@Test
fun `should recognize cut as Cut`() {
    assertThat(VoiceCommandRecognizer.recognize("cut")).isEqualTo(VoiceCommand.Cut)
}

@Test
fun `should recognize 切り取り as Cut`() {
    assertThat(VoiceCommandRecognizer.recognize("切り取り")).isEqualTo(VoiceCommand.Cut)
}

// --- Clear All ---
@Test
fun `should recognize 全部刪除 as ClearAll`() {
    assertThat(VoiceCommandRecognizer.recognize("全部刪除")).isEqualTo(VoiceCommand.ClearAll)
}

@Test
fun `should recognize clear all as ClearAll`() {
    assertThat(VoiceCommandRecognizer.recognize("clear all")).isEqualTo(VoiceCommand.ClearAll)
}

@Test
fun `should recognize clear as ClearAll`() {
    assertThat(VoiceCommandRecognizer.recognize("clear")).isEqualTo(VoiceCommand.ClearAll)
}

@Test
fun `should recognize 全削除 as ClearAll`() {
    assertThat(VoiceCommandRecognizer.recognize("全削除")).isEqualTo(VoiceCommand.ClearAll)
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testReleaseUnitTest --tests "com.voxpen.app.ime.VoiceCommandRecognizerTest" --no-daemon -q 2>&1 | tail -5`
Expected: FAIL — `VoiceCommand.Undo` does not exist

**Step 3: Add new sealed objects to VoiceCommand.kt**

Add after the existing 4 objects:

```kotlin
    /** Undoes the last action */
    data object Undo : VoiceCommand()

    /** Selects all text in the field */
    data object SelectAll : VoiceCommand()

    /** Copies selected text to clipboard */
    data object Copy : VoiceCommand()

    /** Pastes clipboard content */
    data object Paste : VoiceCommand()

    /** Cuts selected text to clipboard */
    data object Cut : VoiceCommand()

    /** Clears all text in the field */
    data object ClearAll : VoiceCommand()
```

**Step 4: Add trigger words to VoiceCommandRecognizer.kt**

Add these blocks inside the `buildMap { }` after the Space block:

```kotlin
        // Undo
        listOf("復原", "undo", "元に戻す").forEach {
            put(it, VoiceCommand.Undo)
        }
        // Select All
        listOf("全選", "select all", "全て選択", "すべて選択").forEach {
            put(it, VoiceCommand.SelectAll)
        }
        // Copy
        listOf("複製", "copy", "コピー").forEach {
            put(it, VoiceCommand.Copy)
        }
        // Paste
        listOf("貼上", "paste", "貼り付け").forEach {
            put(it, VoiceCommand.Paste)
        }
        // Cut
        listOf("剪下", "cut", "切り取り").forEach {
            put(it, VoiceCommand.Cut)
        }
        // Clear All
        listOf("全部刪除", "clear all", "clear", "全削除").forEach {
            put(it, VoiceCommand.ClearAll)
        }
```

**Step 5: Run tests to verify they pass**

Run: `./gradlew :app:testReleaseUnitTest --tests "com.voxpen.app.ime.VoiceCommandRecognizerTest" --no-daemon -q`
Expected: All 36 tests PASS (15 existing + 21 new)

**Step 6: Commit**

```bash
git add app/src/main/java/com/voxpen/app/data/model/VoiceCommand.kt \
       app/src/main/java/com/voxpen/app/ime/VoiceCommandRecognizer.kt \
       app/src/test/java/com/voxpen/app/ime/VoiceCommandRecognizerTest.kt
git commit -m "feat(voice-commands): add undo, select all, copy, paste, cut, clear all commands"
```

---

### Task 3: Wire new voice commands in VoxPenIME

Add `when` branches in `executeVoiceCommand()` for the 6 new commands.

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/ime/VoxPenIME.kt:576-583`

**Step 1: Update `executeVoiceCommand()` method**

Replace the existing method (lines 576-583) with:

```kotlin
    private fun executeVoiceCommand(command: VoiceCommand) {
        when (command) {
            VoiceCommand.Enter -> sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_ENTER)
            VoiceCommand.Backspace -> sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DEL)
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

**Step 2: Build to verify compilation**

Run: `./gradlew :app:assembleRelease --no-daemon -q 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/voxpen/app/ime/VoxPenIME.kt
git commit -m "feat(voice-commands): wire new commands in VoxPenIME.executeVoiceCommand"
```

---

### Task 4: Add copy button to keyboard layout XML

Add `ImageButton` with copy icon to `candidate_refined_row` and `candidate_status_row`.

**Files:**
- Create: `app/src/main/res/drawable/ic_content_copy.xml`
- Modify: `app/src/main/res/layout/keyboard_view.xml`

**Step 1: Create the copy icon drawable**

Create `app/src/main/res/drawable/ic_content_copy.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="@color/key_text">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M16,1L4,1c-1.1,0 -2,0.9 -2,2v14h2L4,3h12L16,1zM19,5L8,5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2L21,7c0,-1.1 -0.9,-2 -2,-2zM19,21L8,21L8,7h11v14z" />
</vector>
```

**Step 2: Add copy button to `candidate_status_row`**

In `keyboard_view.xml`, inside `candidate_status_row` (after the `candidate_text` TextView, before the closing `</LinearLayout>`), add:

```xml
            <ImageButton
                android:id="@+id/btn_copy_status"
                android:layout_width="32dp"
                android:layout_height="32dp"
                android:layout_marginStart="4dp"
                android:background="?android:attr/selectableItemBackgroundBorderless"
                android:contentDescription="@string/copy_to_clipboard"
                android:src="@drawable/ic_content_copy"
                android:scaleType="centerInside"
                android:visibility="gone" />
```

**Step 3: Add copy button to `candidate_refined_row`**

In `keyboard_view.xml`, inside `candidate_refined_row` (after the `candidate_refined` TextView, before the closing `</LinearLayout>`), add:

```xml
            <ImageButton
                android:id="@+id/btn_copy_refined"
                android:layout_width="32dp"
                android:layout_height="32dp"
                android:layout_marginStart="4dp"
                android:background="?android:attr/selectableItemBackgroundBorderless"
                android:contentDescription="@string/copy_to_clipboard"
                android:src="@drawable/ic_content_copy"
                android:scaleType="centerInside"
                android:visibility="gone" />
```

**Step 4: Add string resource for content description**

In `app/src/main/res/values/strings.xml`, add:

```xml
    <string name="copy_to_clipboard">Copy to clipboard</string>
```

In `app/src/main/res/values-zh-rTW/strings.xml`, add:

```xml
    <string name="copy_to_clipboard">複製到剪貼簿</string>
```

**Step 5: Build to verify XML is valid**

Run: `./gradlew :app:assembleRelease --no-daemon -q 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add app/src/main/res/drawable/ic_content_copy.xml \
       app/src/main/res/layout/keyboard_view.xml \
       app/src/main/res/values/strings.xml \
       app/src/main/res/values-zh-rTW/strings.xml
git commit -m "feat(ime): add copy button to candidate bar layout"
```

---

### Task 5: Wire copy buttons in VoxPenIME

Bind the new `ImageButton` views and set click listeners that copy text to clipboard.

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/ime/VoxPenIME.kt`

**Step 1: Add fields for the copy buttons**

After the existing `refineProgress` field (around line 58), add:

```kotlin
    private var copyStatusButton: ImageButton? = null
    private var copyRefinedButton: ImageButton? = null
```

**Step 2: Bind the new views in `bindViews()`**

Add to the end of `bindViews()` (after `toneButton` binding):

```kotlin
        copyStatusButton = view.findViewById(R.id.btn_copy_status)
        copyRefinedButton = view.findViewById(R.id.btn_copy_refined)
```

**Step 3: Add a `copyToClipboard()` helper method**

Add this private method to `VoxPenIME`:

```kotlin
    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(android.content.ClipboardManager::class.java)
        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("VoxPen", text))
        android.widget.Toast.makeText(this, R.string.transcription_copied, android.widget.Toast.LENGTH_SHORT).show()
    }
```

Note: We reuse the existing `transcription_copied` string ("Copied to clipboard" / "已複製到剪貼簿").

**Step 4: Update `resetClickListeners()` to clear copy buttons**

Add to `resetClickListeners()`:

```kotlin
        copyStatusButton?.setOnClickListener(null)
        copyStatusButton?.visibility = View.GONE
        copyRefinedButton?.setOnClickListener(null)
        copyRefinedButton?.visibility = View.GONE
```

**Step 5: Update `updateCandidateBar()` — Result state**

In the `is ImeUiState.Result` branch (around line 367), after the existing `candidateBar?.setOnClickListener` block, add:

```kotlin
                copyStatusButton?.visibility = View.VISIBLE
                copyStatusButton?.setOnClickListener { copyToClipboard(state.text) }
```

**Step 6: Update `updateCandidateBar()` — Refined state**

In the `is ImeUiState.Refined` branch (around line 379), after the `candidateRefinedRow?.setOnClickListener` block, add:

```kotlin
                copyRefinedButton?.visibility = View.VISIBLE
                copyRefinedButton?.setOnClickListener { copyToClipboard(state.refined) }
```

**Step 7: Build and run all tests**

Run: `./gradlew :app:testReleaseUnitTest --no-daemon -q 2>&1 | tail -5`
Expected: All tests PASS

Run: `./gradlew :app:assembleRelease --no-daemon -q 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 8: Commit**

```bash
git add app/src/main/java/com/voxpen/app/ime/VoxPenIME.kt
git commit -m "feat(ime): wire copy-to-clipboard buttons in candidate bar"
```

---

### Task 6: Update README with new voice commands

Update the voice commands table in README.md to include the 6 new commands.

**Files:**
- Modify: `README.md:26-31`

**Step 1: Replace the voice commands table**

Replace the existing table (lines 26-31) with:

```markdown
| Say | Action |
|-----|--------|
| 送出 / 傳送 / send / enter / submit / 送信 / 確定 | ↵ Enter (send message / submit form) |
| 刪除 / 退格 / delete / backspace / 削除 / バックスペース | ⌫ Delete last character |
| 換行 / new line / newline / 改行 | ↩ Insert newline |
| 空格 / space / スペース | ␣ Insert space |
| 復原 / undo / 元に戻す | ↶ Undo last action |
| 全選 / select all / 全て選択 | Select all text |
| 複製 / copy / コピー | Copy selected text |
| 貼上 / paste / 貼り付け | Paste from clipboard |
| 剪下 / cut / 切り取り | Cut selected text |
| 全部刪除 / clear all / clear / 全削除 | Clear all text in field |
```

**Step 2: Commit**

```bash
git add README.md
git commit -m "docs: add new voice commands to README"
```

---

## Verification Checklist

After all tasks, run full test suite:

```bash
./gradlew :app:testReleaseUnitTest --no-daemon -q
```

Expected: All tests pass (previous ~386 + 21 new = ~407).

### Manual device testing

1. Dictate text → refined row appears → tap 📋 icon → Toast "Copied to clipboard" → paste elsewhere to verify
2. Dictate text → single result row → tap 📋 icon → same behavior
3. Say "undo" → last action undone
4. Say "全選" → all text selected
5. Say "copy" → selected text copied
6. Say "paste" → clipboard pasted
7. Say "cut" → selected text cut
8. Say "clear all" → field emptied
9. Existing commands still work: "send", "delete", "new line", "space"
