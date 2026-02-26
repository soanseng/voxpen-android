# IME Tone Quick-Select Button Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a dedicated tone button to the IME key row that shows current tone as emoji and opens a selection popup on tap.

**Architecture:** New `btn_tone` TextView added to `keyboard_view.xml` between mic and enter. `ToneStyle` gains an `emoji` property. `VoxInkIME` observes `toneStyleFlow` to update the button label, tap opens a vertical PopupWindow, and the tone section is removed from quick settings.

**Tech Stack:** Android XML layout, PopupWindow, Kotlin Flow, PreferencesManager DataStore

---

### Task 1: Add `emoji` property to ToneStyle

**Files:**
- Modify: `app/src/main/java/com/voxink/app/data/model/ToneStyle.kt`
- Modify: `app/src/test/java/com/voxink/app/data/model/ToneStyleTest.kt`

**Step 1: Write the failing test**

In `ToneStyleTest.kt`, add:

```kotlin
@Test
fun `each tone style has a unique emoji`() {
    val emojis = ToneStyle.all.map { it.emoji }
    assertEquals(6, emojis.size)
    assertEquals(emojis.distinct().size, emojis.size)
}

@Test
fun `emoji values match expected`() {
    assertEquals("\uD83D\uDCAC", ToneStyle.Casual.emoji)      // 💬
    assertEquals("\uD83D\uDCBC", ToneStyle.Professional.emoji) // 💼
    assertEquals("\uD83D\uDCE7", ToneStyle.Email.emoji)        // 📧
    assertEquals("\uD83D\uDCDD", ToneStyle.Note.emoji)         // 📝
    assertEquals("\uD83D\uDCF1", ToneStyle.Social.emoji)       // 📱
    assertEquals("⚙", ToneStyle.Custom.emoji)
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.voxink.app.data.model.ToneStyleTest"`
Expected: FAIL — `emoji` property doesn't exist.

**Step 3: Write minimal implementation**

In `ToneStyle.kt`, add `emoji` parameter to the sealed class constructor and each subclass:

```kotlin
sealed class ToneStyle(
    val key: String,
    val emoji: String,
) {
    data object Casual : ToneStyle("casual", "\uD83D\uDCAC")           // 💬
    data object Professional : ToneStyle("professional", "\uD83D\uDCBC") // 💼
    data object Email : ToneStyle("email", "\uD83D\uDCE7")              // 📧
    data object Note : ToneStyle("note", "\uD83D\uDCDD")                // 📝
    data object Social : ToneStyle("social", "\uD83D\uDCF1")            // 📱
    data object Custom : ToneStyle("custom", "⚙")

    // companion object unchanged
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.voxink.app.data.model.ToneStyleTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/voxink/app/data/model/ToneStyle.kt \
       app/src/test/java/com/voxink/app/data/model/ToneStyleTest.kt
git commit -m "feat: add emoji property to ToneStyle"
```

---

### Task 2: Add tone button to keyboard XML layout

**Files:**
- Modify: `app/src/main/res/layout/keyboard_view.xml`

**Step 1: Add `btn_tone` TextView between `btn_mic` and `btn_enter`**

After the `btn_mic` ImageButton (line 131-139) and before `btn_enter` (line 141-149), insert:

```xml
<TextView
    android:id="@+id/btn_tone"
    android:layout_width="0dp"
    android:layout_height="match_parent"
    android:layout_weight="1"
    android:background="@color/key_background"
    android:gravity="center"
    android:textSize="20sp"
    android:text="\uD83D\uDCAC"
    android:layout_margin="2dp" />
```

**Step 2: Verify layout compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/res/layout/keyboard_view.xml
git commit -m "feat: add tone button to keyboard layout"
```

---

### Task 3: Add string resources for tone popup labels and tooltip

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`

**Step 1: Add strings to `values/strings.xml`**

After the existing tone style strings (line 192), add:

```xml
<!-- Tone quick-select (IME) -->
<string name="keyboard_tone">Tone</string>
<string name="tone_popup_casual">\uD83D\uDCAC Casual</string>
<string name="tone_popup_professional">\uD83D\uDCBC Professional</string>
<string name="tone_popup_email">\uD83D\uDCE7 Email</string>
<string name="tone_popup_note">\uD83D\uDCDD Note</string>
<string name="tone_popup_social">\uD83D\uDCF1 Social</string>
<string name="tone_popup_custom">⚙ Custom</string>
```

**Step 2: Add strings to `values-zh-rTW/strings.xml`**

After the existing tone style strings (line 192), add:

```xml
<!-- Tone quick-select (IME) -->
<string name="keyboard_tone">語氣</string>
<string name="tone_popup_casual">\uD83D\uDCAC 日常聊天</string>
<string name="tone_popup_professional">\uD83D\uDCBC 正式書面</string>
<string name="tone_popup_email">\uD83D\uDCE7 電子郵件</string>
<string name="tone_popup_note">\uD83D\uDCDD 筆記</string>
<string name="tone_popup_social">\uD83D\uDCF1 社群貼文</string>
<string name="tone_popup_custom">⚙ 自訂</string>
```

**Step 3: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml \
       app/src/main/res/values-zh-rTW/strings.xml
git commit -m "feat: add tone quick-select string resources"
```

---

### Task 4: Wire tone button in VoxInkIME — binding, popup, and Flow observation

**Files:**
- Modify: `app/src/main/java/com/voxink/app/ime/VoxInkIME.kt`

**Step 1: Add tone button field**

After `private var micButton: ImageButton? = null` (line 49), add:

```kotlin
private var toneButton: TextView? = null
```

**Step 2: Bind tone button in `bindViews()`**

After `micButton = view.findViewById(R.id.btn_mic)` (line 127), add:

```kotlin
toneButton = view.findViewById(R.id.btn_tone)
```

**Step 3: Wire tone button in `bindButtons()`**

After the `setupMicButton(...)` call (line 147), add:

```kotlin
view.findViewById<TextView>(R.id.btn_tone)?.setOnClickListener {
    showTonePopup(it)
}
```

**Step 4: Add `showTonePopup()` method**

Add new private method (after `showQuickSettings()`):

```kotlin
private fun showTonePopup(anchor: View) {
    serviceScope.launch {
        val currentTone = preferencesManager.toneStyleFlow.first()
        val dp = resources.displayMetrics.density

        val container = LinearLayout(this@VoxInkIME).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(resources.getColor(R.color.key_background, null))
            val pad = (12 * dp).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val popup = PopupWindow(
            container,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        )

        val tones = listOf(
            ToneStyle.Casual to getString(R.string.tone_popup_casual),
            ToneStyle.Professional to getString(R.string.tone_popup_professional),
            ToneStyle.Email to getString(R.string.tone_popup_email),
            ToneStyle.Note to getString(R.string.tone_popup_note),
            ToneStyle.Social to getString(R.string.tone_popup_social),
            ToneStyle.Custom to getString(R.string.tone_popup_custom),
        )

        tones.forEach { (tone, label) ->
            val tv = TextView(this@VoxInkIME).apply {
                text = label
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

        popup.showAtLocation(anchor, Gravity.BOTTOM or Gravity.END, (8 * dp).toInt(), (64 * dp).toInt())
    }
}
```

**Step 5: Observe toneStyleFlow to update button emoji**

In `onCreateInputView()`, after the tooltip block (line 113), add:

```kotlin
serviceScope.launch {
    preferencesManager.toneStyleFlow.collect { tone ->
        toneButton?.text = tone.emoji
    }
}
```

**Step 6: Remove tone section from quick settings**

In `showQuickSettings()`, remove these two lines:
```kotlin
addToneOptions(container, popup, currentTone, dp)
addQuickSettingsDivider(container, dp)
```

Remove the `val currentTone = ...` line since it's no longer used.

Delete the entire `addToneOptions()` method (lines 462-506).

**Step 7: Add tone button to tooltip overlay**

In `showKeyboardTooltips()`, add to the `tooltips` map:
```kotlin
R.id.btn_tone to getString(R.string.keyboard_tone),
```

**Step 8: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 9: Commit**

```bash
git add app/src/main/java/com/voxink/app/ime/VoxInkIME.kt
git commit -m "feat: wire tone quick-select button with popup and Flow observation"
```

---

### Task 5: Verify all tests pass

**Step 1: Run full test suite**

Run: `./gradlew testDebugUnitTest`
Expected: All tests PASS

**Step 2: Build check**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL
