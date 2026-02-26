# VoxPen Rename + Language UX Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Rename app from VoxInk to VoxPen, add emoji flags to STT language selector, add IME language quick-switch via long-press globe, and add onboarding tips step.

**Architecture:** Four independent workstreams executed sequentially. Workstream 1 (rename) must go first since it changes every file path. Workstreams 2-4 can be done in any order after the rename.

**Tech Stack:** Kotlin, Android XML resources, Gradle Kotlin DSL, GitHub CLI

---

### Task 1: Rename — Move directory structure

**Files:**
- Move: `app/src/main/java/com/voxink/` → `app/src/main/java/com/voxpen/`
- Move: `app/src/test/java/com/voxink/` → `app/src/test/java/com/voxpen/`

**Step 1: Move main source directory**

```bash
mkdir -p app/src/main/java/com/voxpen
mv app/src/main/java/com/voxink/app app/src/main/java/com/voxpen/app
rmdir app/src/main/java/com/voxink
```

**Step 2: Move test source directory**

```bash
mkdir -p app/src/test/java/com/voxpen
mv app/src/test/java/com/voxink/app app/src/test/java/com/voxpen/app
rmdir app/src/test/java/com/voxink
```

**Step 3: Update all package declarations**

Find-and-replace across all `.kt` files:
- `package com.voxink.app` → `package com.voxpen.app`
- `import com.voxink.app` → `import com.voxpen.app`

Run:
```bash
find app/src -name "*.kt" -exec sed -i 's/com\.voxink\.app/com.voxpen.app/g' {} +
```

**Step 4: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add -A
git commit -m "refactor: move package com.voxink.app to com.voxpen.app"
```

---

### Task 2: Rename — Update Gradle and resource identifiers

**Files:**
- Modify: `settings.gradle.kts` — line 23: `rootProject.name = "VoxInk"` → `"VoxPen"`
- Modify: `app/build.gradle.kts` — line 21: `namespace = "com.voxink.app"` → `"com.voxpen.app"`, line 25: `applicationId = "com.voxink.app"` → `"com.voxpen.app"`
- Modify: `app/src/main/res/values/themes.xml` — `Theme.VoxInk` → `Theme.VoxPen`, `voxink_primary` → `voxpen_primary`
- Modify: `app/src/main/res/values/colors.xml` — `voxink_primary` → `voxpen_primary`
- Modify: `app/src/main/AndroidManifest.xml` — `.VoxInkApplication` → `.VoxPenApplication`, `Theme.VoxInk` → `Theme.VoxPen`
- Modify: `app/src/main/res/xml/method.xml` — `com.voxink.app` → `com.voxpen.app`

**Step 1: Update settings.gradle.kts**

```kotlin
// line 23
rootProject.name = "VoxPen"
```

**Step 2: Update app/build.gradle.kts**

```kotlin
// line 21
namespace = "com.voxpen.app"
// line 25
applicationId = "com.voxpen.app"
```

**Step 3: Update themes.xml**

```xml
<style name="Theme.VoxPen" parent="Theme.Material3.DayNight.NoActionBar">
    <item name="android:statusBarColor">@android:color/transparent</item>
    <item name="android:navigationBarColor">@android:color/transparent</item>
    <item name="colorPrimary">@color/voxpen_primary</item>
</style>
```

**Step 4: Update colors.xml**

```xml
<color name="voxpen_primary">#FF4F46E5</color>
```

**Step 5: Update AndroidManifest.xml**

```xml
android:name=".VoxPenApplication"
android:theme="@style/Theme.VoxPen"
```
Both `<activity>` and `<application>` theme refs: `Theme.VoxInk` → `Theme.VoxPen`.

The service line changes: `.VoxInkIME` → `.VoxPenIME`.

**Step 6: Update method.xml**

Replace `com.voxink.app` → `com.voxpen.app` in `settingsActivity`.

**Step 7: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 8: Commit**

```bash
git add -A
git commit -m "refactor: update gradle, manifest, and resources for VoxPen rename"
```

---

### Task 3: Rename — Update class names and code references

**Files:**
- Rename: `VoxInkApplication.kt` → `VoxPenApplication.kt`, class `VoxInkApplication` → `VoxPenApplication`
- Rename: `VoxInkIME.kt` → `VoxPenIME.kt`, class `VoxInkIME` → `VoxPenIME`
- Rename: `VoxInkIMEEntryPoint.kt` → `VoxPenIMEEntryPoint.kt`, interface `VoxInkIMEEntryPoint` → `VoxPenIMEEntryPoint`
- Modify: `app/src/main/java/com/voxpen/app/ui/theme/Color.kt` — `VoxInkPurple` → `VoxPenPurple`, `VoxInkPurpleLight` → `VoxPenPurpleLight`, `VoxInkPurpleDark` → `VoxPenPurpleDark`
- Modify: `app/src/main/java/com/voxpen/app/ui/theme/Type.kt` — `VoxInkTypography` → `VoxPenTypography`
- Modify: `app/src/main/java/com/voxpen/app/ui/theme/Theme.kt` — `VoxInkTheme` → `VoxPenTheme`, `VoxInkTypography` → `VoxPenTypography`
- Modify: `app/src/main/java/com/voxpen/app/ui/MainActivity.kt` — `VoxInkTheme` → `VoxPenTheme`, `VoxInkNavHost` → `VoxPenNavHost`
- Modify: `app/src/main/java/com/voxpen/app/ime/VoxPenIME.kt` — all `VoxInkIME` refs → `VoxPenIME`, `VoxInkIMEEntryPoint` → `VoxPenIMEEntryPoint`, Timber tag `VoxInkIME` → `VoxPenIME`
- Test files: rename `VoxInkApplicationTest.kt` → `VoxPenApplicationTest.kt`, update class name and assertion

**Step 1: Rename Application class**

```bash
mv app/src/main/java/com/voxpen/app/VoxInkApplication.kt app/src/main/java/com/voxpen/app/VoxPenApplication.kt
```

Edit `VoxPenApplication.kt`:
```kotlin
class VoxPenApplication : Application() {
```

**Step 2: Rename IME class**

```bash
mv app/src/main/java/com/voxpen/app/ime/VoxInkIME.kt app/src/main/java/com/voxpen/app/ime/VoxPenIME.kt
mv app/src/main/java/com/voxpen/app/ime/VoxInkIMEEntryPoint.kt app/src/main/java/com/voxpen/app/ime/VoxPenIMEEntryPoint.kt
```

Edit `VoxPenIME.kt`:
```kotlin
class VoxPenIME : InputMethodService() {
    // ...
    Timber.d("VoxPenIME input view created")
    // reference: VoxPenIMEEntryPoint::class.java
}
```

Edit `VoxPenIMEEntryPoint.kt`:
```kotlin
interface VoxPenIMEEntryPoint {
```

**Step 3: Rename theme identifiers**

In `Color.kt`:
```kotlin
val VoxPenPurple = Color(0xFF6366F1)
val VoxPenPurpleLight = Color(0xFF818CF8)
val VoxPenPurpleDark = Color(0xFF4F46E5)
```

In `Type.kt`:
```kotlin
val VoxPenTypography = Typography(
```

In `Theme.kt`:
```kotlin
fun VoxPenTheme(
    // ...
    typography = VoxPenTypography,
```

In `MainActivity.kt`:
```kotlin
VoxPenTheme {
    VoxPenNavHost()
}
// ...
private fun VoxPenNavHost() {
```

**Step 4: Update test files**

```bash
mv app/src/test/java/com/voxpen/app/VoxInkApplicationTest.kt app/src/test/java/com/voxpen/app/VoxPenApplicationTest.kt
```

Edit `VoxPenApplicationTest.kt`:
```kotlin
class VoxPenApplicationTest {
    @Test
    fun `application class should be properly named`() {
        val name = VoxPenApplication::class.qualifiedName
        assertThat(name).isEqualTo("com.voxpen.app.VoxPenApplication")
    }
}
```

Edit `ThemeTest.kt` — rename all `VoxInkPurple` → `VoxPenPurple`, `VoxInkPurpleLight` → `VoxPenPurpleLight`, `VoxInkPurpleDark` → `VoxPenPurpleDark`, `VoxInkTypography` → `VoxPenTypography`.

**Step 5: Global sweep for remaining VoxInk references in code**

```bash
grep -r "VoxInk" app/src --include="*.kt" -l
```

Fix any remaining references. Be careful NOT to change the product name "VoxInk Bundle" in LemonSqueezy test fixtures — that's the product name in the remote API, update it to "VoxPen Bundle" only if desired.

**Step 6: Verify build and tests**

```bash
./gradlew assembleDebug
./gradlew test
```
Expected: BUILD SUCCESSFUL, all tests pass

**Step 7: Commit**

```bash
git add -A
git commit -m "refactor: rename VoxInk classes and identifiers to VoxPen"
```

---

### Task 4: Rename — Update user-facing strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`

**Step 1: Update English strings**

Replace all user-facing "VoxInk" with "VoxPen" in `values/strings.xml`:

| String key | Old value | New value |
|-----------|-----------|-----------|
| `app_name` | `VoxInk` | `VoxPen` |
| `ime_name` | `VoxInk Voice` | `VoxPen Voice` |
| `welcome_message` | `Welcome to VoxInk` | `Welcome to VoxPen` |
| `enable_keyboard_prompt` | `Enable VoxInk keyboard…` | `Enable VoxPen keyboard…` |
| `onboarding_welcome_title` | `Welcome to VoxInk` | `Welcome to VoxPen` |
| `onboarding_welcome_description` | `VoxInk is an AI-powered…` | `VoxPen is an AI-powered…` |
| `onboarding_api_key_description` | `VoxInk uses Groq's…` | `VoxPen uses Groq's…` |
| `onboarding_keyboard_title` | `Enable VoxInk Keyboard` | `Enable VoxPen Keyboard` |
| `onboarding_keyboard_description` | `…enable VoxInk Voice…` | `…enable VoxPen Voice…` |
| `onboarding_permission_description` | `VoxInk needs microphone…` | `VoxPen needs microphone…` |
| `onboarding_done_description` | `…use VoxInk. Switch to VoxInk Voice…` | `…use VoxPen. Switch to VoxPen Voice…` |
| `onboarding_practice_description` | `…See how VoxInk transforms…` | `…See how VoxPen transforms…` |
| `pro_upgrade_title` | `Upgrade to VoxInk Pro` | `Upgrade to VoxPen Pro` |
| `license_activate_hint` | `Enter your VoxInk Bundle key` | `Enter your VoxPen Bundle key` |

**Step 2: Update Traditional Chinese strings**

zh-TW strings use "語墨" which stays unchanged. Only update where "VoxInk" appears literally:

| String key | Old value | New value |
|-----------|-----------|-----------|
| `pro_upgrade_title` | `升級至 VoxInk Pro` | `升級至 VoxPen Pro` |
| `license_activate_hint` | `輸入你的 VoxInk Bundle 授權碼` | `輸入你的 VoxPen Bundle 授權碼` |

**Step 3: Update LemonSqueezy test fixtures**

In `app/src/test/java/com/voxpen/app/billing/LicenseManagerTest.kt` and `app/src/test/java/com/voxpen/app/data/remote/LemonSqueezyApiTest.kt`:
- Replace `"VoxInk Bundle"` → `"VoxPen Bundle"` in test data and assertions

**Step 4: Verify build**

Run: `./gradlew assembleDebug && ./gradlew test`
Expected: BUILD SUCCESSFUL, all tests pass

**Step 5: Commit**

```bash
git add -A
git commit -m "refactor: update user-facing strings from VoxInk to VoxPen"
```

---

### Task 5: Rename — Update CLAUDE.md and rename GitHub repo

**Files:**
- Modify: `CLAUDE.md`

**Step 1: Update CLAUDE.md**

Replace all occurrences of:
- `VoxInk` → `VoxPen` (in English context)
- `com.voxink.app` → `com.voxpen.app`
- `VoxInkApplication` → `VoxPenApplication`
- `VoxInkIME` → `VoxPenIME`
- `VoxInkTheme` → `VoxPenTheme`
- Keep `語墨` unchanged

**Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: update CLAUDE.md for VoxPen rename"
```

**Step 3: Rename GitHub repository**

```bash
gh repo rename voxpen-android
```

**Step 4: Update local git remote**

```bash
git remote set-url origin git@github.com:soanseng/voxpen-android.git
```

**Step 5: Push all rename commits**

```bash
git push
```

---

### Task 6: Add emoji property to SttLanguage

**Files:**
- Test: `app/src/test/java/com/voxpen/app/data/model/SttLanguageTest.kt`
- Modify: `app/src/main/java/com/voxpen/app/data/model/SttLanguage.kt`

**Step 1: Write the failing test**

Create `app/src/test/java/com/voxpen/app/data/model/SttLanguageTest.kt`:

```kotlin
package com.voxpen.app.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SttLanguageTest {
    @Test
    fun `Auto should have globe emoji`() {
        assertThat(SttLanguage.Auto.emoji).isEqualTo("\uD83C\uDF10") // 🌐
    }

    @Test
    fun `Chinese should have Taiwan flag emoji`() {
        assertThat(SttLanguage.Chinese.emoji).isEqualTo("\uD83C\uDDF9\uD83C\uDDFC") // 🇹🇼
    }

    @Test
    fun `English should have US flag emoji`() {
        assertThat(SttLanguage.English.emoji).isEqualTo("\uD83C\uDDFA\uD83C\uDDF8") // 🇺🇸
    }

    @Test
    fun `Japanese should have Japan flag emoji`() {
        assertThat(SttLanguage.Japanese.emoji).isEqualTo("\uD83C\uDDEF\uD83C\uDDF5") // 🇯🇵
    }

    @Test
    fun `Korean should have Korea flag emoji`() {
        assertThat(SttLanguage.Korean.emoji).isEqualTo("\uD83C\uDDF0\uD83C\uDDF7") // 🇰🇷
    }

    @Test
    fun `French should have France flag emoji`() {
        assertThat(SttLanguage.French.emoji).isEqualTo("\uD83C\uDDEB\uD83C\uDDF7") // 🇫🇷
    }

    @Test
    fun `German should have Germany flag emoji`() {
        assertThat(SttLanguage.German.emoji).isEqualTo("\uD83C\uDDE9\uD83C\uDDEA") // 🇩🇪
    }

    @Test
    fun `Spanish should have Spain flag emoji`() {
        assertThat(SttLanguage.Spanish.emoji).isEqualTo("\uD83C\uDDEA\uD83C\uDDF8") // 🇪🇸
    }

    @Test
    fun `Vietnamese should have Vietnam flag emoji`() {
        assertThat(SttLanguage.Vietnamese.emoji).isEqualTo("\uD83C\uDDFB\uD83C\uDDF3") // 🇻🇳
    }

    @Test
    fun `Indonesian should have Indonesia flag emoji`() {
        assertThat(SttLanguage.Indonesian.emoji).isEqualTo("\uD83C\uDDEE\uD83C\uDDE9") // 🇮🇩
    }

    @Test
    fun `Thai should have Thailand flag emoji`() {
        assertThat(SttLanguage.Thai.emoji).isEqualTo("\uD83C\uDDF9\uD83C\uDDED") // 🇹🇭
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.voxpen.app.data.model.SttLanguageTest"`
Expected: FAIL — `emoji` property doesn't exist

**Step 3: Add emoji property to SttLanguage**

Modify `app/src/main/java/com/voxpen/app/data/model/SttLanguage.kt`:

```kotlin
package com.voxpen.app.data.model

sealed class SttLanguage(
    val code: String?,
    val prompt: String,
    val emoji: String,
) {
    data object Auto : SttLanguage(
        code = null,
        prompt = "繁體中文，可能夾雜英文。",
        emoji = "\uD83C\uDF10", // 🌐
    )

    data object Chinese : SttLanguage(
        code = "zh",
        prompt = "繁體中文轉錄。",
        emoji = "\uD83C\uDDF9\uD83C\uDDFC", // 🇹🇼
    )

    data object English : SttLanguage(
        code = "en",
        prompt = "Transcribe the following English speech.",
        emoji = "\uD83C\uDDFA\uD83C\uDDF8", // 🇺🇸
    )

    data object Japanese : SttLanguage(
        code = "ja",
        prompt = "以下の日本語音声を文字起こししてください。",
        emoji = "\uD83C\uDDEF\uD83C\uDDF5", // 🇯🇵
    )

    data object Korean : SttLanguage(
        code = "ko",
        prompt = "한국어 음성을 전사합니다.",
        emoji = "\uD83C\uDDF0\uD83C\uDDF7", // 🇰🇷
    )

    data object French : SttLanguage(
        code = "fr",
        prompt = "Transcription de la parole française.",
        emoji = "\uD83C\uDDEB\uD83C\uDDF7", // 🇫🇷
    )

    data object German : SttLanguage(
        code = "de",
        prompt = "Transkription der deutschen Sprache.",
        emoji = "\uD83C\uDDE9\uD83C\uDDEA", // 🇩🇪
    )

    data object Spanish : SttLanguage(
        code = "es",
        prompt = "Transcripción del habla en español.",
        emoji = "\uD83C\uDDEA\uD83C\uDDF8", // 🇪🇸
    )

    data object Vietnamese : SttLanguage(
        code = "vi",
        prompt = "Phiên âm giọng nói tiếng Việt.",
        emoji = "\uD83C\uDDFB\uD83C\uDDF3", // 🇻🇳
    )

    data object Indonesian : SttLanguage(
        code = "id",
        prompt = "Transkripsi ucapan bahasa Indonesia.",
        emoji = "\uD83C\uDDEE\uD83C\uDDE9", // 🇮🇩
    )

    data object Thai : SttLanguage(
        code = "th",
        prompt = "ถอดเสียงภาษาไทย",
        emoji = "\uD83C\uDDF9\uD83C\uDDED", // 🇹🇭
    )
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.voxpen.app.data.model.SttLanguageTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/test/java/com/voxpen/app/data/model/SttLanguageTest.kt app/src/main/java/com/voxpen/app/data/model/SttLanguage.kt
git commit -m "feat: add emoji flag property to SttLanguage"
```

---

### Task 7: Prepend emoji flags in Settings language selector

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/ui/settings/SettingsScreen.kt`

**Step 1: Update LanguageSection to prepend emoji**

In `SettingsScreen.kt`, modify the `LanguageSection` composable. Change the primary language list (around line 342-347):

```kotlin
listOf(
    SttLanguage.Auto to "${SttLanguage.Auto.emoji} ${stringResource(R.string.lang_auto)}",
    SttLanguage.Chinese to "${SttLanguage.Chinese.emoji} ${stringResource(R.string.lang_zh)}",
    SttLanguage.English to "${SttLanguage.English.emoji} ${stringResource(R.string.lang_en)}",
    SttLanguage.Japanese to "${SttLanguage.Japanese.emoji} ${stringResource(R.string.lang_ja)}",
).forEach { (lang, label) ->
```

And the extra languages list (around line 376-383):

```kotlin
listOf(
    SttLanguage.Korean to "${SttLanguage.Korean.emoji} ${stringResource(R.string.lang_ko)}",
    SttLanguage.French to "${SttLanguage.French.emoji} ${stringResource(R.string.lang_fr)}",
    SttLanguage.German to "${SttLanguage.German.emoji} ${stringResource(R.string.lang_de)}",
    SttLanguage.Spanish to "${SttLanguage.Spanish.emoji} ${stringResource(R.string.lang_es)}",
    SttLanguage.Vietnamese to "${SttLanguage.Vietnamese.emoji} ${stringResource(R.string.lang_vi)}",
    SttLanguage.Indonesian to "${SttLanguage.Indonesian.emoji} ${stringResource(R.string.lang_id)}",
    SttLanguage.Thai to "${SttLanguage.Thai.emoji} ${stringResource(R.string.lang_th)}",
).forEach { (lang, label) ->
```

**Step 2: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/voxpen/app/ui/settings/SettingsScreen.kt
git commit -m "feat: prepend emoji flags in Settings language selector"
```

---

### Task 8: Add IME language quick-switch popup

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/ime/VoxPenIME.kt`

**Step 1: Add long-press listener on btn_switch**

In `bindButtons()` method, replace the single `setOnClickListener` for `btn_switch` with both click and long-click:

```kotlin
view.findViewById<ImageButton>(R.id.btn_switch)?.let { switchBtn ->
    switchBtn.setOnClickListener {
        actionHandler.handle(KeyboardAction.SwitchKeyboard)
    }
    switchBtn.setOnLongClickListener {
        showLanguagePopup(it)
        true
    }
}
```

**Step 2: Add showLanguagePopup method**

Add this method to VoxPenIME (follow the showTonePopup pattern):

```kotlin
private fun showLanguagePopup(anchor: View) {
    serviceScope.launch {
        val currentLang = preferencesManager.languageFlow.first()
        val dp = resources.displayMetrics.density

        val container = createQuickSettingsContainer(dp)

        val popup = PopupWindow(
            container,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        )

        val languages = listOf(
            SttLanguage.Auto to "${SttLanguage.Auto.emoji} ${getString(R.string.lang_auto)}",
            SttLanguage.Chinese to "${SttLanguage.Chinese.emoji} ${getString(R.string.lang_zh)}",
            SttLanguage.English to "${SttLanguage.English.emoji} ${getString(R.string.lang_en)}",
            SttLanguage.Japanese to "${SttLanguage.Japanese.emoji} ${getString(R.string.lang_ja)}",
        )

        languages.forEach { (lang, label) ->
            val tv = TextView(this@VoxPenIME).apply {
                text = label
                textSize = 14f
                setTextColor(
                    if (lang == currentLang) {
                        resources.getColor(R.color.mic_idle, null)
                    } else {
                        resources.getColor(R.color.key_text, null)
                    },
                )
                val pad = (8 * dp).toInt()
                setPadding(pad, pad, pad, pad)
                setOnClickListener {
                    serviceScope.launch { preferencesManager.setLanguage(lang) }
                    popup.dismiss()
                }
            }
            container.addView(tv)
        }

        popup.showAtLocation(anchor, Gravity.BOTTOM or Gravity.START, (8 * dp).toInt(), (64 * dp).toInt())
    }
}
```

Note: Use `Gravity.BOTTOM or Gravity.START` since the globe button is on the left side.

**Step 3: Remove language options from showQuickSettings**

In `showQuickSettings()`, remove the call to `addLanguageOptions(container, popup, currentLang, dp)` and `addQuickSettingsDivider(container, dp)`. The quick-settings popup should only show the refinement toggle:

```kotlin
private fun showQuickSettings(anchor: View) {
    serviceScope.launch {
        val refinementOn = preferencesManager.refinementEnabledFlow.first()
        val dp = resources.displayMetrics.density

        val container = createQuickSettingsContainer(dp)
        val popup = PopupWindow(
            container,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        )

        addRefinementToggle(container, popup, refinementOn, dp)

        popup.showAtLocation(anchor, Gravity.BOTTOM or Gravity.END, (8 * dp).toInt(), (64 * dp).toInt())
    }
}
```

**Step 4: Clean up — remove addLanguageOptions method**

Delete the `addLanguageOptions` method and `addQuickSettingsDivider` method since they're no longer used. Also remove the `currentLang` variable from `showQuickSettings`.

**Step 5: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add app/src/main/java/com/voxpen/app/ime/VoxPenIME.kt
git commit -m "feat: add language quick-switch popup via long-press globe button"
```

---

### Task 9: Add string resources for language popup (IME quick-switch)

No new strings needed — we reuse `lang_auto`, `lang_zh`, `lang_en`, `lang_ja` which already exist. The emoji comes from `SttLanguage.emoji`. But we do need a tooltip string for the long-press hint.

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`

**Step 1: Update keyboard tooltip string**

In `values/strings.xml`, the existing `keyboard_switch` is "Switch keyboard". We leave this as-is since it describes the tap action. No new strings needed for the popup itself.

This task is a no-op — skip and proceed to Task 10.

---

### Task 10: Add TIPS onboarding step — string resources

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`

**Step 1: Add English strings**

Add after the existing onboarding strings section in `values/strings.xml`:

```xml
<!-- Onboarding — Tips -->
<string name="onboarding_tips_title">Keyboard Tips</string>
<string name="onboarding_tip_language">Long-press 🌐 to quickly switch dictation language</string>
<string name="onboarding_tip_refinement">Long-press ⚙ to toggle refinement on/off</string>
<string name="onboarding_tip_tone">Tap the tone button (💬) to change writing style</string>
```

**Step 2: Add Traditional Chinese strings**

Add in `values-zh-rTW/strings.xml`:

```xml
<!-- Onboarding — Tips -->
<string name="onboarding_tips_title">鍵盤使用提示</string>
<string name="onboarding_tip_language">長按 🌐 快速切換聽寫語言</string>
<string name="onboarding_tip_refinement">長按 ⚙ 開關文字潤飾</string>
<string name="onboarding_tip_tone">點擊語氣按鈕（💬）切換寫作風格</string>
```

**Step 3: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-zh-rTW/strings.xml
git commit -m "feat: add onboarding tips string resources"
```

---

### Task 11: Add TIPS onboarding step — enum and UI state

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/ui/onboarding/OnboardingUiState.kt`

**Step 1: Add TIPS to OnboardingStep enum**

```kotlin
enum class OnboardingStep {
    WELCOME,
    API_KEY,
    ENABLE_KEYBOARD,
    GRANT_PERMISSION,
    PRACTICE,
    TIPS,
    DONE,
}
```

**Step 2: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (the `when` in OnboardingScreen.kt should still compile since we haven't added a new branch yet — but it may warn or fail if it's an exhaustive when. If it fails, proceed to Step 3 immediately.)

**Step 3: Commit**

```bash
git add app/src/main/java/com/voxpen/app/ui/onboarding/OnboardingUiState.kt
git commit -m "feat: add TIPS step to onboarding enum"
```

---

### Task 12: Add TIPS onboarding step — composable and wiring

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/ui/onboarding/OnboardingScreen.kt`

**Step 1: Add TipsStep composable**

Add after the `DoneStep` composable:

```kotlin
@Composable
private fun TipsStep() {
    Text(
        stringResource(R.string.onboarding_tips_title),
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(Modifier.height(24.dp))
    val tips = listOf(
        R.string.onboarding_tip_language,
        R.string.onboarding_tip_refinement,
        R.string.onboarding_tip_tone,
    )
    tips.forEach { tipRes ->
        Text(
            "• ${stringResource(tipRes)}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
}
```

**Step 2: Wire TipsStep into the when block**

In `OnboardingScreenContent`, add the TIPS branch in the `when(state.currentStep)` block:

```kotlin
OnboardingStep.PRACTICE -> PracticeStep(state, viewModel)
OnboardingStep.TIPS -> TipsStep()
OnboardingStep.DONE -> DoneStep()
```

**Step 3: Update StepProgress text**

In the `StepProgress` composable, add TIPS to the when block:

```kotlin
OnboardingStep.PRACTICE -> stringResource(R.string.onboarding_progress_almost)
OnboardingStep.TIPS -> stringResource(R.string.onboarding_progress_last)
OnboardingStep.DONE -> null
```

And update GRANT_PERMISSION's text:
```kotlin
OnboardingStep.GRANT_PERMISSION -> stringResource(R.string.onboarding_progress_few_more)
```

**Step 4: Update NavigationButtons canProceed**

In the `canProceed` when block, add:
```kotlin
OnboardingStep.TIPS -> true
```

**Step 5: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 6: Run all tests**

Run: `./gradlew test`
Expected: All tests pass

**Step 7: Commit**

```bash
git add app/src/main/java/com/voxpen/app/ui/onboarding/OnboardingScreen.kt
git commit -m "feat: add TIPS step to onboarding flow"
```

---

### Task 13: Final verification

**Step 1: Run full test suite**

```bash
./gradlew test
```
Expected: All tests pass

**Step 2: Build release variant**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

**Step 3: Verify no VoxInk references remain in source code**

```bash
grep -r "VoxInk" app/src --include="*.kt" --include="*.xml" --include="*.kts" | grep -v "VoxInk Bundle"
```
Expected: No results (except possibly "VoxInk Bundle" in LemonSqueezy test fixtures if we kept that product name).

Also check gradle files:
```bash
grep -r "voxink" *.kts app/build.gradle.kts settings.gradle.kts
```
Expected: No results
