# Translation Quick-Switch Indicator Row — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a tappable translation indicator row at the top of the candidate bar so users can cycle translation target languages and toggle translation on/off without leaving the keyboard.

**Architecture:** A new XML row in `keyboard_view.xml` driven by two existing `PreferencesManager` flows (`translationEnabledFlow`, `translationTargetLanguageFlow`). `VoxPenIME` collects both flows and updates the indicator row visibility/text. Smart filtering removes the current STT language from target options.

**Tech Stack:** Android XML layout, Kotlin, DataStore Flows, Material vector drawable

---

### Task 1: Add string resources

**Files:**
- Modify: `app/src/main/res/values/strings.xml` (after line 148)
- Modify: `app/src/main/res/values-zh-rTW/strings.xml` (after line 148)

**Step 1: Add English strings**

Add these strings after `quick_translation_off` in `values/strings.xml`:

```xml
    <!-- Translation Indicator (keyboard) -->
    <string name="translation_indicator_speak_zh">🔄 說中文 → %1$s</string>
    <string name="translation_indicator_speak_en">🔄 Speak EN → %1$s</string>
    <string name="translation_indicator_speak_ja">🔄 日本語 → %1$s</string>
    <string name="translation_indicator_speak_auto">🔄 Translate → %1$s</string>
    <string name="translation_close">Close translation</string>
```

**Step 2: Add Traditional Chinese strings**

Add these strings after `quick_translation_off` in `values-zh-rTW/strings.xml`:

```xml
    <!-- Translation Indicator (keyboard) -->
    <string name="translation_indicator_speak_zh">🔄 說中文 → %1$s</string>
    <string name="translation_indicator_speak_en">🔄 Speak EN → %1$s</string>
    <string name="translation_indicator_speak_ja">🔄 日本語 → %1$s</string>
    <string name="translation_indicator_speak_auto">🔄 翻譯 → %1$s</string>
    <string name="translation_close">關閉翻譯</string>
```

**Step 3: Build to verify resources compile**

Run: `cd /home/scipio/projects/voxpen-android && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```
git add app/src/main/res/values/strings.xml app/src/main/res/values-zh-rTW/strings.xml
git commit -m "feat(ime): add translation indicator string resources"
```

---

### Task 2: Add close icon drawable

**Files:**
- Create: `app/src/main/res/drawable/ic_close.xml`

**Step 1: Create the close icon vector drawable**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="16dp"
    android:height="16dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="@color/key_text">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z" />
</vector>
```

This is the standard Material Design close/clear icon at 16dp (smaller than the 24dp key icons since it sits in the indicator row).

**Step 2: Build to verify drawable compiles**

Run: `cd /home/scipio/projects/voxpen-android && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```
git add app/src/main/res/drawable/ic_close.xml
git commit -m "feat(ime): add close icon drawable for translation indicator"
```

---

### Task 3: Add translation indicator row to keyboard layout

**Files:**
- Modify: `app/src/main/res/layout/keyboard_view.xml`

**Step 1: Add the translation indicator row**

Insert as the **first child** of `candidate_bar` (the `LinearLayout` with `android:id="@+id/candidate_bar"`), before the `candidate_status_row`:

```xml
        <!-- Translation indicator (tappable to cycle target language, × to close) -->
        <LinearLayout
            android:id="@+id/translation_indicator_row"
            android:layout_width="match_parent"
            android:layout_height="28dp"
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:paddingStart="4dp"
            android:paddingEnd="4dp"
            android:background="?android:attr/selectableItemBackground"
            android:visibility="gone">

            <TextView
                android:id="@+id/translation_label"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:textColor="@color/key_text"
                android:textSize="13sp"
                android:ellipsize="end"
                android:maxLines="1" />

            <ImageButton
                android:id="@+id/btn_translation_close"
                android:layout_width="28dp"
                android:layout_height="28dp"
                android:background="?android:attr/selectableItemBackgroundBorderless"
                android:contentDescription="@string/translation_close"
                android:src="@drawable/ic_close"
                android:scaleType="centerInside" />
        </LinearLayout>
```

**Step 2: Build to verify layout compiles**

Run: `cd /home/scipio/projects/voxpen-android && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```
git add app/src/main/res/layout/keyboard_view.xml
git commit -m "feat(ime): add translation indicator row to keyboard layout"
```

---

### Task 4: Wire translation indicator in VoxPenIME

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/ime/VoxPenIME.kt`

This is the main wiring task. Changes are in 4 areas of VoxPenIME:

#### Step 1: Add fields for the new views and translation state

Add these fields alongside the existing view fields (after `private var toneButton: TextView? = null` around line 62):

```kotlin
    private var translationIndicatorRow: LinearLayout? = null
    private var translationLabel: TextView? = null
    private var translationCloseButton: ImageButton? = null

    // Translation state (synced from preferences)
    @Volatile private var translationEnabled: Boolean = PreferencesManager.DEFAULT_TRANSLATION_ENABLED
    @Volatile private var translationTargetLanguage: SttLanguage = PreferencesManager.DEFAULT_TRANSLATION_TARGET_LANGUAGE
    @Volatile private var currentSttLanguage: SttLanguage = SttLanguage.Auto
```

#### Step 2: Bind views in `bindViews()`

Add to the end of `bindViews()`:

```kotlin
        translationIndicatorRow = view.findViewById(R.id.translation_indicator_row)
        translationLabel = view.findViewById(R.id.translation_label)
        translationCloseButton = view.findViewById(R.id.btn_translation_close)
```

#### Step 3: Add Flow collectors in `onCreateInputView()`

Add these after the existing `customAppToneRulesFlow` collector (around line 149), before the Timber.d log:

```kotlin
        serviceScope.launch {
            preferencesManager.translationEnabledFlow.collect { enabled ->
                translationEnabled = enabled
                updateTranslationIndicator()
            }
        }
        serviceScope.launch {
            preferencesManager.translationTargetLanguageFlow.collect { lang ->
                translationTargetLanguage = lang
                updateTranslationIndicator()
            }
        }
        serviceScope.launch {
            preferencesManager.languageFlow.collect { lang ->
                currentSttLanguage = lang
                updateTranslationIndicator()
            }
        }
```

#### Step 4: Set up click handlers in `bindButtons()`

Add at the end of `bindButtons()`:

```kotlin
        view.findViewById<TextView>(R.id.translation_label)?.setOnClickListener {
            cycleTranslationTarget()
        }
        view.findViewById<ImageButton>(R.id.btn_translation_close)?.setOnClickListener {
            serviceScope.launch { preferencesManager.setTranslationEnabled(false) }
        }
```

#### Step 5: Add helper methods

Add these methods in VoxPenIME (before the `companion object`):

```kotlin
    private fun getTranslationTargets(): List<SttLanguage> {
        val all = listOf(SttLanguage.English, SttLanguage.Chinese, SttLanguage.Japanese)
        return when (currentSttLanguage) {
            SttLanguage.Auto -> all
            else -> all.filter { it != currentSttLanguage }
        }
    }

    private fun cycleTranslationTarget() {
        val targets = getTranslationTargets()
        if (!translationEnabled) {
            // Off → first target
            serviceScope.launch {
                preferencesManager.setTranslationTargetLanguage(targets.first())
                preferencesManager.setTranslationEnabled(true)
            }
            return
        }
        val currentIndex = targets.indexOf(translationTargetLanguage)
        val nextIndex = currentIndex + 1
        if (nextIndex >= targets.size) {
            // Last target → Off
            serviceScope.launch { preferencesManager.setTranslationEnabled(false) }
        } else {
            serviceScope.launch { preferencesManager.setTranslationTargetLanguage(targets[nextIndex]) }
        }
    }

    private fun updateTranslationIndicator() {
        if (!translationEnabled) {
            translationIndicatorRow?.visibility = View.GONE
            return
        }
        translationIndicatorRow?.visibility = View.VISIBLE
        candidateBar?.visibility = View.VISIBLE

        val targetName = when (translationTargetLanguage) {
            SttLanguage.English -> getString(R.string.lang_en)
            SttLanguage.Chinese -> getString(R.string.lang_zh)
            SttLanguage.Japanese -> getString(R.string.lang_ja)
            else -> translationTargetLanguage.code ?: "?"
        }

        val formatRes = when (currentSttLanguage) {
            SttLanguage.Chinese -> R.string.translation_indicator_speak_zh
            SttLanguage.English -> R.string.translation_indicator_speak_en
            SttLanguage.Japanese -> R.string.translation_indicator_speak_ja
            else -> R.string.translation_indicator_speak_auto
        }
        translationLabel?.text = getString(formatRes, targetName)
    }
```

#### Step 6: Update `updateCandidateBar()` Idle case

In the `ImeUiState.Idle` branch of `updateCandidateBar()`, change:

```kotlin
            ImeUiState.Idle -> {
                timerHandler.removeCallbacks(timerRunnable)
                candidateBar?.visibility = View.GONE
            }
```

to:

```kotlin
            ImeUiState.Idle -> {
                timerHandler.removeCallbacks(timerRunnable)
                if (translationEnabled) {
                    candidateBar?.visibility = View.VISIBLE
                    candidateStatusRow?.visibility = View.GONE
                    candidateOriginal?.visibility = View.GONE
                    candidateRefinedRow?.visibility = View.GONE
                } else if (!isEditMode) {
                    candidateBar?.visibility = View.GONE
                }
            }
```

This ensures the candidate bar stays visible (showing the translation indicator) when translation is active even in Idle state.

**Step 7: Build**

Run: `cd /home/scipio/projects/voxpen-android && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 8: Run tests**

Run: `cd /home/scipio/projects/voxpen-android && ./gradlew test 2>&1 | tail -10`
Expected: All tests pass (no existing tests break)

**Step 9: Commit**

```
git add app/src/main/java/com/voxpen/app/ime/VoxPenIME.kt
git commit -m "feat(ime): wire translation indicator row with smart language cycling"
```

---

### Task 5: Update README and ROADMAP

**Files:**
- Modify: `README.md`
- Modify: `ROADMAP.md`

**Step 1: Add translation quick-switch to README**

In the keyboard features section, mention the translation indicator row:
- Translation mode indicator visible on keyboard when active
- Tap to cycle target language, × to close
- Smart filtering: excludes current STT language from targets

**Step 2: Update ROADMAP if applicable**

Mark translation quick-switch as shipped if there's a relevant roadmap item.

**Step 3: Commit**

```
git add README.md ROADMAP.md
git commit -m "docs: document translation quick-switch indicator feature"
```
