# VoxPen vs Typeless — Feature Comparison & Roadmap

Date: 2026-02-28

---

## Feature Comparison

### ✅ Parity — Both implement these

| Feature | VoxPen | Typeless |
|---------|--------|----------|
| Voice-to-text via Whisper | Groq / OpenAI / Custom | Hosted (opaque) |
| Filler word removal | zh-TW / en / ja / mixed | All languages |
| Mid-sentence self-correction | LLM prompt-based | AI-based |
| Auto-format lists / structure | LLM prompt-based | AI-based |
| 6-minute recording limit | ✅ with countdown warning | ✅ |
| Custom vocabulary / dictionary | ✅ (50 free / ∞ Pro) | ✅ personal dictionary |
| Mixed-language code-switching | ✅ auto-detect mode | ✅ |
| On-device history | ✅ Room database | ✅ |
| File transcription | ✅ with SRT export | ❌ (keyboard-only) |
| Dual-result display (raw + refined) | ✅ candidate bar | ❌ (outputs once) |

---

### ⚡ VoxPen Advantages

| Feature | VoxPen | Typeless |
|---------|--------|----------|
| **BYOK model** | ✅ Groq / OpenAI / OpenRouter / Custom | ❌ subscription-only |
| **Self-hosted STT** | ✅ custom endpoint | ❌ |
| **Multi-LLM provider** | ✅ 4 providers | ❌ opaque |
| **File transcription + SRT** | ✅ full workflow | ❌ |
| **zh-TW focus** | ✅ 繁體 prompts, vocabulary | 通用 |
| **One-time Pro purchase** | ✅ | ❌ $12/mo subscription |
| **Pricing transparency** | You pay API costs directly | Opaque cloud costs |

---

### ❌ VoxPen Gaps vs Typeless

| Gap | Typeless Feature | Priority |
|-----|-----------------|----------|
| **Translation Mode** | Speak zh → output en (and vice versa) | P1 — in backlog |
| **Auto Context-Aware Tone** | Detect foreground app (Gmail = formal, WhatsApp = casual) | P1 |
| **Speak to Edit** | Select text in any app → voice-edit by describing changes | P1 |
| **100+ Languages** | 100+ vs VoxPen's 11 exposed (Whisper supports 99) | P2 |
| **Personalization / Style Learning** | Adapts to user's writing patterns over time | P3 |
| **AI Query on Selected Text** | Summarize / explain / translate highlighted content | P3 |

---

## Featured Roadmap

### Phase A — Core Parity (v1.x, high impact / feasible)

#### A1. Translation Mode
**What**: User speaks in Language A, VoxPen outputs in Language B.
**Why**: Major use case for zh-TW users communicating in English (and vice versa).
**How**:
- Add "Translation" toggle in Settings and IME quick settings
- Add target language selector (e.g., "Translate to: English")
- Modify LLM system prompt: `"你是翻譯助手，將以下繁體中文口語翻譯成自然的英文書面語，不要添加說明。"`
- Works on top of existing Groq/OpenAI LLM refinement pipeline
- Store `translationEnabled` + `translationTargetLanguage` in DataStore

**Files to touch**: `PreferencesManager`, `SettingsRepository`, `SettingsScreen`, `KeyboardView`, `VoxPenIME`

---

#### A2. Auto Context-Aware Tone
**What**: IME detects which app is active and auto-selects appropriate tone style.
**Why**: Typeless's killer UX — zero manual tone selection needed.
**How**:
- Read `currentInputEditorInfo.packageName` in `VoxPenIME` (already available in IME context)
- Build a simple app → tone mapping table:
  ```
  com.whatsapp, com.facebook.orca → Casual (💬)
  com.google.android.gm, com.microsoft.office.outlook → Email (📧)
  com.slack → Professional (💼)
  com.twitter.android, com.instagram.android → Social (📱)
  com.notion.id, com.bear.notes → Note (📝)
  * (default) → user preference
  ```
- Add "Auto-detect tone" toggle in Settings (default: ON)
- When auto-detect is ON, override tone selection with mapped value
- Show detected tone name briefly in keyboard UI as a toast/snackbar

**Files to touch**: `VoxPenIME`, `SettingsRepository`, `SettingsScreen`, `PreferencesManager`

---

#### A3. Speak to Edit
**What**: User selects text in any app, taps a "voice edit" button in the keyboard, speaks an edit command (e.g., "make it more formal"), and the selected text is replaced.
**Why**: This is Typeless's most distinctive feature. Transforms VoxPen from a dictation tool to a full voice-editing assistant.
**How**:
- Read selected text via `currentInputConnection.getSelectedText(0)` in IME
- Add "Edit" mode button to keyboard layout (pencil icon, only visible when text is selected)
- On tap: enter Edit mode — record user's edit command
- After recording: send to LLM with context:
  ```
  System: "You are a text editor. Given selected text and an edit instruction, output ONLY the revised text."
  User: "Selected text: [selection]\nEdit instruction: [transcription]"
  ```
- Replace selected text via `currentInputConnection.commitText()` after deleting selection

**Difficulty**: Medium-High (new IME flow, selection detection, new LLM prompt type)
**Files to touch**: `VoxPenIME`, `KeyboardView`, new `EditModeViewModel` or extend existing IME state machine

---

### Phase B — Reach Expansion (v2.x)

#### B1. More Language Exposure
**What**: Expose all Whisper-supported languages in the language picker.
**Why**: Whisper already supports 99 languages. VoxPen currently shows only 11.
**How**:
- Expand `SttLanguage` enum with all Whisper language codes
- Group in UI: "Popular" (current 11) + "More Languages" (expandable section)
- Add refinement prompt templates for high-value languages: Korean, French, Spanish, German
- Low effort for STT; medium effort for quality refinement prompts

**Files to touch**: `SttLanguage.kt`, `SettingsScreen.kt`, `KeyboardView.kt`

---

#### B2. Per-App Custom Tone Mapping
**What**: Users can assign specific tone styles to specific apps beyond the defaults.
**Why**: Extension of A2 — power users can customize the auto-detection mapping.
**How**:
- Settings screen section: "App Tone Rules"
- List of installed IME-enabled apps with tone assignment dropdown
- Stored as `Map<packageName, ToneStyle>` in DataStore

---

### Phase C — Differentiation (v2.x+)

#### C1. AI Query on Selected Text
**What**: User selects text → tap "Ask AI" in keyboard → speak a question (summarize, translate, explain).
**Why**: Makes VoxPen a full AI assistant keyboard, not just dictation.
**How**:
- New button in keyboard toolbar (AI wand icon)
- Read `getSelectedText(0)` from InputConnection
- Record user's question
- LLM call: `System: "Answer questions about the provided text concisely."`
- Display answer in candidate bar or overlay panel

---

#### C2. Personalization / Writing Style Learning
**What**: VoxPen learns from committed texts to build a personal writing style profile.
**Why**: Makes refinement more personalized over time.
**How**:
- Store last N committed outputs in Room as "style examples"
- Include top examples in LLM system prompt as few-shot examples
- Privacy: local-only, never sent anywhere except user's own LLM API
- Toggle: "Learn from my writing" in Settings (default: OFF for privacy clarity)

---

## Priority Matrix

| Feature | Impact | Effort | Priority |
|---------|--------|--------|----------|
| Translation Mode | High | Low | **P1 → Ship in v1.x** |
| Auto Context-Aware Tone | High | Low | **P1 → Ship in v1.x** |
| Speak to Edit | High | Medium | **P1 → Target v1.5** |
| More Languages (UI) | Medium | Low | P2 |
| Per-App Custom Tone | Medium | Medium | P2 |
| AI Query on Selected Text | Medium | Medium | P3 |
| Personalization / Style Learning | Low | High | P3 |

---

## Summary

VoxPen's core differentiators vs Typeless are:
1. **BYOK** — Typeless is a $12/month black box; VoxPen puts the user in control of API costs and providers
2. **File transcription + SRT** — Typeless is keyboard-only
3. **zh-TW depth** — Typeless is generic; VoxPen has 繁體-specific prompts, vocabulary, and UX

The three highest-priority gaps to close:
1. **Translation Mode** — low effort, high demand for zh-TW↔en users
2. **Auto Context-Aware Tone** — IME already has `packageName` access, minimal code needed
3. **Speak to Edit** — Typeless's crown jewel; required for feature parity at the high end
