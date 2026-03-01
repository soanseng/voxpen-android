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
| Dual-result display (raw + refined) | ✅ candidate bar + copy button | ❌ (outputs once) |

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
| ~~**Translation Mode**~~ | ~~Speak zh → output en (and vice versa)~~ | ✅ **Shipped** |
| ~~**Auto Context-Aware Tone**~~ | ~~Detect foreground app (Gmail = formal, WhatsApp = casual)~~ | ✅ **Shipped** |
| ~~**Speak to Edit**~~ | ~~Select text in any app → voice-edit by describing changes~~ | ✅ **Shipped** |
| **100+ Languages** | 100+ vs VoxPen's 11 exposed (Whisper supports 99) | P2 |
| **Personalization / Style Learning** | Adapts to user's writing patterns over time | P3 |
| **AI Query on Selected Text** | Summarize / explain / translate highlighted content | P3 |

---

## Featured Roadmap

### Phase A — Core Parity (v1.x, high impact / feasible)

#### A1. Translation Mode ✅ Shipped (2026-03-01)
**What**: User speaks in Language A, VoxPen outputs in Language B.
**How**: Toggle + target language selector in Settings and IME quick settings (long-press ⚙️).
Routes to a separate `TranslationPrompt` instead of the refinement prompt.
Supports zh→en, en→zh, zh→ja, and reverse.
**Quick Switch**: Tappable indicator row in candidate bar (`🔄 說中文 → English`) lets users cycle target languages and close translation without leaving the keyboard. Smart filtering excludes the current STT language from targets.

---

#### A2. Auto Context-Aware Tone ✅ Shipped (2026-03-01)
**What**: IME detects which app is active and auto-selects the appropriate tone style — without changing the user's saved preference.
**How**: `onStartInput()` in `VoxPenIME` calls `AppToneDetector.detect(packageName, inputType, customRules)` with a three-level priority cascade: user custom rules → built-in 22-app table → `TYPE_TEXT_VARIATION_SHORT_MESSAGE` inputType heuristic → null (falls back to saved preference). Effective tone is captured at recording start and passed as `toneOverride` to `RecordingController`; manual keyboard tone-button tap overrides for the current recording only, then auto-detection re-applies on next field focus.

Built-in table covers 22 apps across 5 tones (💬 Casual / 📧 Email / 💼 Professional / 📝 Note / 📱 Social). Custom per-app rules stored as JSON in DataStore; managed via Settings → Auto Tone → Custom App Rules. Toggle defaults ON; when OFF, saved preference is used.

**Also shipped**: Per-App Custom Tone (was Phase B2) — fully integrated as part of this feature.

---

#### A3. Speak to Edit ✅ Shipped (2026-03-01)
**What**: User selects text in any app, enables Edit Mode via long-press ⚙️ → ✏️, speaks an edit instruction (e.g., "讓它更正式" / "make it more formal"), and the selection is replaced with the LLM-revised text.
**How**: `isEditMode` flag in `VoxPenIME`; `RecordingController.onStopRecording(editMode=true)` emits `EditInstruction` instead of refining; `performEditWithLlm()` reads `getSelectedText(0)`, builds an `EditPrompt`, calls `EditTextUseCase`, then `commitText()`.
**Also shipped**: Voice Commands — 10 commands recognized before refinement (no API call required): send/delete/newline/space + undo/select all/copy/paste/cut/clear all. Trilingual trigger words (zh-TW, en, ja).

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

#### B2. Per-App Custom Tone Mapping ✅ Shipped (2026-03-01)
Shipped as part of A2 — see above.

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
| ~~Translation Mode~~ | High | Low | ✅ Shipped |
| ~~Auto Context-Aware Tone~~ | High | Low | ✅ Shipped |
| ~~Speak to Edit~~ | High | Medium | ✅ Shipped |
| ~~Voice Commands (10 commands)~~ | Medium | Low | ✅ Shipped |
| ~~Copy-to-Clipboard Button~~ | Medium | Low | ✅ Shipped |
| More Languages (UI) | Medium | Low | P2 |
| ~~Per-App Custom Tone~~ | Medium | Medium | ✅ Shipped |
| AI Query on Selected Text | Medium | Medium | P3 |
| Personalization / Style Learning | Low | High | P3 |

---

## Summary

VoxPen's core differentiators vs Typeless are:
1. **BYOK** — Typeless is a $12/month black box; VoxPen puts the user in control of API costs and providers
2. **File transcription + SRT** — Typeless is keyboard-only
3. **zh-TW depth** — Typeless is generic; VoxPen has 繁體-specific prompts, vocabulary, and UX
4. **Translation Mode** — shipped; speak in one language, output in another ✅
5. **Speak to Edit** — shipped; select text → voice-instruct LLM to rewrite it ✅
6. **Voice Commands** — shipped; 10 trilingual commands (send, delete, newline, space, undo, select all, copy, paste, cut, clear all) ✅
8. **Copy-to-Clipboard** — shipped; tap copy icon on refined row to copy without inserting ✅
7. **Auto Context-Aware Tone** — shipped; IME auto-selects tone by foreground app with custom per-app rules ✅

The remaining highest-priority gap:
1. **More Language Exposure** — expose all 99 Whisper-supported languages in the language picker
