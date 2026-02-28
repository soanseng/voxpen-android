# Auto Context-Aware Tone — Design Doc

Date: 2026-03-01

---

## Goal

When the user activates VoxPen in a text field, automatically select the appropriate tone style based on the active app and field type — without changing the user's saved preference.

---

## Architecture

### New components

| File | Role |
|------|------|
| `data/model/BuiltinAppToneTable.kt` | Hardcoded `Map<String, ToneStyle>` for known apps |
| `ime/AppToneDetector.kt` | `detect(packageName, inputType, customRules) → ToneStyle?` |
| `test/.../AppToneDetectorTest.kt` | Unit tests for detection logic |
| `test/.../BuiltinAppToneTableTest.kt` | Smoke test that all entries are valid |

### Modified components

| File | Change |
|------|--------|
| `PreferencesManager.kt` | Add `autoToneEnabledFlow`, `customAppToneRulesFlow`, setters |
| `VoxPenIME.kt` | Override `onStartInput()` to call `AppToneDetector`; maintain `effectiveTone`; update tone button |
| `RecordingController.kt` | Add `toneOverride: ToneStyle? = null` to `onStartRecording()`; use it in refinement |
| `SettingsScreen.kt` | New Auto Tone section: toggle + custom rules list + Add Rule dialog |
| `SettingsViewModel.kt` + `SettingsUiState.kt` | Expose new prefs; add/remove custom rule actions |
| `strings.xml` (en + zh-TW) | New strings for Auto Tone section |

---

## Detection Logic

`AppToneDetector.detect()` applies rules in this priority order:

1. **User custom rules** — `customRules[packageName]` (highest priority)
2. **Builtin package name table** — `BuiltinAppToneTable.rules[packageName]`
3. **inputType signal** — if `inputType & TYPE_TEXT_VARIATION_MASK == TYPE_TEXT_VARIATION_SHORT_MESSAGE` → `Casual`
4. **null** — caller falls back to user's saved preference

Returns `null` if no rule matches; the caller (`VoxPenIME`) then uses `preferencesManager.toneStyleFlow.value` as fallback.

---

## Builtin App Table

| Tone | Apps |
|------|------|
| 💬 Casual | WhatsApp (`com.whatsapp`), Telegram (`org.telegram.messenger`), FB Messenger (`com.facebook.orca`), LINE (`jp.naver.line.android`), Discord (`com.discord`), KakaoTalk (`com.kakao.talk`), Viber (`com.viber.voip`) |
| 📧 Email | Gmail (`com.google.android.gm`), Outlook (`com.microsoft.office.outlook`), Proton Mail (`me.proton.android.mail`) |
| 💼 Professional | Slack (`com.slack`), Microsoft Teams (`com.microsoft.teams`) |
| 📝 Note | Google Keep (`com.google.android.keep`), Notion (`com.notion.id`), Obsidian (`md.obsidian`), Evernote (`com.evernote`) |
| 📱 Social | Twitter/X (`com.twitter.android`), Instagram (`com.instagram.android`), Threads (`com.instagram.threads`), TikTok (`com.zhiliaoapp.musically`), Facebook (`com.facebook.katana`), Dcard (`com.dcard.app`) |

---

## VoxPenIME Integration

```kotlin
// New field
private var effectiveTone: ToneStyle = ToneStyle.DEFAULT

override fun onStartInput(editorInfo: EditorInfo, restarting: Boolean) {
    super.onStartInput(editorInfo, restarting)
    if (autoToneEnabled) {
        val detected = AppToneDetector.detect(
            packageName = editorInfo.packageName ?: "",
            inputType = editorInfo.inputType,
            customRules = customAppToneRules,
        )
        effectiveTone = detected ?: preferencesManager.toneStyleFlow.value
        toneButton?.text = effectiveTone.emoji
    } else {
        effectiveTone = preferencesManager.toneStyleFlow.value
    }
}
```

When user **manually taps the tone button**, update `effectiveTone` for this recording only. After recording completes (state → Idle), `effectiveTone` is NOT reset — it stays until the next `onStartInput()` call (next time user taps into a field). This gives the user a clean "this recording only" override without needing explicit reset logic.

`onStartRecording()` captures the current `effectiveTone` at the moment recording begins and passes it to `RecordingController`:

```kotlin
recordingController.onStartRecording(
    startRecording = { audioRecorder.startRecording() },
    toneOverride = effectiveTone,
)
```

`RecordingController.onStartRecording()` stores it as `private var capturedTone` and uses it in the refinement call instead of the flow-collected `toneStyle`.

---

## Data Model

```kotlin
// PreferencesManager additions
val autoToneEnabledFlow: Flow<Boolean>               // default: true
val customAppToneRulesFlow: Flow<Map<String, String>> // packageName → toneKey

suspend fun setAutoToneEnabled(enabled: Boolean)
suspend fun setCustomAppToneRule(packageName: String, toneKey: String)
suspend fun removeCustomAppToneRule(packageName: String)
```

`customAppToneRules` stored in DataStore as a JSON string via `kotlinx.serialization`:
```
key: "custom_app_tone_rules"
value: "{\"com.myapp\":\"professional\",\"com.otherapp\":\"casual\"}"
```

---

## Settings UI

New section in `SettingsScreen`, placed after the Tone Style section:

```
Auto Tone
─────────────────────────────────────────────
[Toggle] Auto-detect tone by app        [ON]
Automatically adjusts tone based on
the active app when you start typing.

Custom App Rules                    [+ Add]
─────────────────────────────────────────────
  Gmail                               📧  [×]
  com.mycompany.app                   💼  [×]
─────────────────────────────────────────────
```

**Add Rule dialog:**
- Package name text field (no installed-app picker in v1 — type manually)
- Tone selector (6 radio buttons)
- Confirm / Cancel

Custom rules list is only visible when Auto Tone toggle is ON.

---

## Testing

| Test | What |
|------|------|
| `AppToneDetectorTest` | custom rule wins over builtin; builtin package match; SHORT_MESSAGE fallback; unknown app → null |
| `BuiltinAppToneTableTest` | all entries map to a valid ToneStyle (smoke test) |
| `PreferencesManagerTest` | autoToneEnabled default true; setCustomAppToneRule persists and reads back; remove rule |
| `SettingsViewModelTest` | setAutoToneEnabled, setCustomAppToneRule, removeCustomAppToneRule delegate to prefs |
| `RecordingControllerTest` | toneOverride is used in refinement instead of flow value |

---

## Out of Scope (v1)

- Installed app picker UI for Add Rule dialog (just text input)
- `inputType` signals beyond `SHORT_MESSAGE` (too unreliable)
- Reading Play Store category via `ApplicationInfo.category` (almost always UNDEFINED)
- Auto-expanding the builtin table after ship — file a PR to add more apps
