# plan.md — VoxInk (語墨) Development Plan

## Vision

Build the best BYOK AI voice keyboard for Android — Typeless-quality UX, open architecture, zero subscription fees. Users bring their own API keys (Groq, OpenAI, Anthropic) and get premium voice-to-text with intelligent refinement.

## Market Context

| Product | Model | Price | Our Advantage |
|---------|-------|-------|---------------|
| Typeless | SaaS, closed | $12-30/mo | BYOK = near-zero cost, open |
| Dictate Keyboard | BYOK, open | $3.49 once | Better UX, Kotlin, modern UI |
| FUTO Keyboard | Offline, open | $10 once | Cloud quality > on-device |
| Wispr Flow | SaaS, closed | $10/mo, no Android | We have Android |

**Positioning**: Typeless UX + Dictate's BYOK model + modern Kotlin codebase

---

## Phase 0: Project Setup (Day 1-2)

### 0.1 Repository & Environment
- [ ] Fork `DevEmperor/Dictate` on GitHub
- [ ] Create new branch `dev` for all development
- [ ] Set up Android Studio project with Kotlin DSL Gradle
- [ ] Change package name to `com.voxink.app`
- [ ] Change app name to "VoxInk" / "語墨"
- [ ] Add `.editorconfig`, `ktlint`, `detekt` configs
- [ ] Set up CI (GitHub Actions): lint + build

### 0.2 Study Dictate Codebase
- [ ] Map out Dictate's Java classes and their responsibilities
- [ ] Identify core IME lifecycle (`DictateInputMethodService.java`)
- [ ] Understand Dictate's API call flow (Whisper → GPT)
- [ ] Note Dictate's UI layout XML files
- [ ] Document which parts to keep vs rewrite

### Deliverable
Clean Kotlin project skeleton that builds and runs (blank IME).

---

## Phase 1: Core IME + Groq STT (Week 1-2)

### 1.1 Minimal IME Service
- [ ] Create `VoxInkIME.kt` extending `InputMethodService`
- [ ] Register IME in `AndroidManifest.xml` + `method.xml`
- [ ] Implement basic keyboard view (Compose or XML):
  - 🎤 Mic button (center, large)
  - ⌫ Backspace
  - ␣ Space
  - ⏎ Enter
  - 🌐 Switch keyboard
  - ⚙️ Settings
- [ ] Handle `commitText()` to insert text into target app
- [ ] Handle `switchToPreviousInputMethod()` / `switchToNextInputMethod()`

### 1.2 Audio Recording
- [ ] Implement `AudioRecorder.kt` using `AudioRecord` API
- [ ] Support two modes:
  - **Hold-to-record**: press and hold mic, release to stop
  - **Tap-to-toggle**: tap to start, tap again to stop
- [ ] Encode PCM → WAV (or OPUS for smaller payloads)
- [ ] Visual feedback: recording indicator (red pulse animation)
- [ ] Audio cue: subtle start/stop sound + haptic

### 1.3 Groq Whisper Integration
- [ ] Create `GroqApi.kt` Retrofit interface
- [ ] Implement `SttRepository.kt` with Groq as default provider
- [ ] Send audio → receive transcription
- [ ] Language modes:
  - **Auto-detect** (default): best for mixed-language (中英混合)
  - **中文** (zh): prompt bias for 繁體中文
  - **English** (en)
  - **日本語** (ja)
- [ ] Mixed-language support: omit `language` param + use prompt hint "繁體中文，可能夾雜英文"
- [ ] Per-language initial_prompt for better accuracy
- [ ] Error handling: network errors, API errors, timeout
- [ ] Display transcription in candidate bar → commit on tap

### 1.4 API Key Setup
- [ ] Create basic Settings screen (Jetpack Compose)
- [ ] API key input for Groq (masked display)
- [ ] Store with `EncryptedSharedPreferences`
- [ ] Validate key on save (test API call)
- [ ] First-run onboarding: guide user to get Groq API key

### Deliverable
Working voice keyboard: tap mic → speak → see Chinese/English text → tap to insert.

---

## Phase 2: LLM Refinement + Dual Display (Week 3-4)

### 2.1 LLM Integration
- [ ] Create LLM API interfaces (Groq LLaMA, OpenAI GPT, Anthropic Claude)
- [ ] Implement `LlmRepository.kt` with provider abstraction
- [ ] Default refinement system prompt (zh-TW + en)
- [ ] Refinement pipeline:
  1. STT returns raw text → display immediately as "Original"
  2. Simultaneously send to LLM for refinement
  3. Refined text streams in → display as "Refined"

### 2.2 Dual Result UI (Candidate Bar)
- [ ] Design candidate bar layout:
  ```
  ┌──────────────────────────────┐
  │ 📝 那個我明天下午三點要開會    │ ← Original (tap to select)
  │ ✨ 我明天下午三點要開會        │ ← Refined (tap to select, default)
  └──────────────────────────────┘
  ```
- [ ] Tap behavior: insert selected version
- [ ] Swipe left/right to switch between versions
- [ ] Long text: scrollable within candidate bar
- [ ] Loading state: show spinner on refined line while LLM processes
- [ ] Toggle: user can disable refinement (only show original)

### 2.3 Typeless-Inspired Post-Processing
- [ ] **Filler word removal** (per-language):
  - zh: 嗯、那個、就是、然後、對對對、呃
  - en: um, uh, like, you know, I mean, basically
  - ja: えーと、あの、まあ、なんか、ちょっと
- [ ] **Self-correction detection**: "不是明天，後天" → "後天"
- [ ] **Repetition removal**: "我我我覺得" → "我覺得"
- [ ] **Auto-punctuation**: ensure proper punctuation per language
- [ ] **List formatting**: detect numbered items and format
- [ ] **Mixed-language handling**: preserve code-switching, don't force translate
- [ ] These can be done either:
  - In the LLM prompt (easier, slower)
  - As local regex/rules pre-processing (faster, less accurate)
  - Hybrid: local pre-clean → LLM polish

### 2.4 Settings Expansion
- [ ] STT provider selector (Groq / OpenAI / Custom endpoint)
- [ ] LLM provider selector (Groq / OpenAI / Anthropic / Custom)
- [ ] Per-provider API key management
- [ ] LLM model selector per provider
- [ ] Refinement on/off toggle
- [ ] Language preference: Auto-detect / 中文 / English / 日本語
- [ ] Per-language refinement prompt (auto-selected, user-customizable)
- [ ] Mixed-language mode hint in auto-detect

### Deliverable
Full voice keyboard with Original vs Refined display, BYOK for multiple providers.

---

## Phase 3: Audio File Transcription (Week 5)

### 3.1 Transcription Screen
- [ ] New Activity/Screen: file-based transcription
- [ ] File picker: select audio/video from device
- [ ] Supported formats: WAV, MP3, M4A, OGG, MP4, WebM
- [ ] Display file info (duration, size, format)

### 3.2 Chunking & Batch Processing
- [ ] Auto-detect if file > 25MB
- [ ] Chunk strategies:
  - Fixed-duration segments (e.g., 5-minute chunks)
  - Silence-based splitting (using AudioTrack analysis)
- [ ] Sequential chunk upload with progress bar
- [ ] Merge chunk results into full transcript

### 3.3 Transcript Management
- [ ] Save transcripts to Room database
- [ ] Display transcript with timestamps (if verbose_json)
- [ ] Edit transcript in-app
- [ ] Export: plain text, SRT subtitles, copy to clipboard
- [ ] Share to other apps

### 3.4 Optional LLM Refinement for Transcripts
- [ ] Apply same refinement pipeline to full transcripts
- [ ] Show original vs refined side-by-side
- [ ] Paragraph-by-paragraph refinement for long texts

### Deliverable
Complete transcription tool: upload audio → get transcript → export.

---

## Phase 4: UI Polish & Typeless Parity (Week 6-7)

### 4.1 Visual Design Overhaul
- [ ] Design system: color palette, typography, spacing
- [ ] Material 3 / Material You dynamic theming
- [ ] Custom mic button with recording animation:
  - Idle: subtle breathing glow
  - Recording: red pulse + waveform visualization
  - Processing: rotating dots / spinner
  - Done: checkmark flash
- [ ] Smooth transitions between states
- [ ] Dark mode + light mode + follow system

### 4.2 Onboarding Flow
- [ ] Step 1: Welcome — what VoxInk does (with demo animation)
- [ ] Step 2: Choose STT provider (Groq recommended)
- [ ] Step 3: Enter API key (with link to get one)
- [ ] Step 4: Enable keyboard in system settings (guided)
- [ ] Step 5: Test it — record a sentence
- [ ] Optional: set up LLM for refinement

### 4.3 i18n — Traditional Chinese + English
- [ ] All strings externalized to `strings.xml`
- [ ] `values/strings.xml` — English
- [ ] `values-zh-rTW/strings.xml` — Traditional Chinese
- [ ] Settings: language override (system / zh-TW / en)
- [ ] UI auto-follows system locale by default

### 4.4 Advanced Keyboard Features
- [ ] Basic text editing keys (arrow keys row, select all, copy, paste)
- [ ] Emoji button (optional, or defer to main keyboard)
- [ ] Quick-settings popup from ⚙️ key:
  - Toggle refinement
  - Switch language
  - Switch STT provider
- [ ] Recording mode preference in keyboard: hold vs toggle

### 4.5 Performance Optimization
- [ ] Minimize IME launch time (< 200ms)
- [ ] Preload API connections
- [ ] Audio encoding in background thread
- [ ] LLM streaming (display tokens as they arrive)
- [ ] Cache last N transcriptions for quick re-commit

### Deliverable
Polished, Typeless-quality app ready for beta testing.

---

## Phase 5: Beta Testing & Play Store (Week 8-9)

### 5.1 Beta Testing
- [ ] Internal testing: install on own devices
- [ ] Test across apps: LINE, Messenger, Gmail, Notes, Chrome
- [ ] Test languages: Mandarin, English, Japanese, 中英混合, auto-detect
- [ ] Test edge cases: long recordings, noisy environments, Bluetooth mic
- [ ] Fix bugs from testing

### 5.2 Play Store Preparation
- [ ] App icon design
- [ ] Feature graphic (1024x500)
- [ ] Screenshots (phone + tablet)
- [ ] Store listing: title, description (zh-TW + en)
- [ ] Privacy policy (mandatory for IME apps)
- [ ] Content rating questionnaire
- [ ] Data safety section (network access for API calls, no data stored on servers)

### 5.3 Release
- [ ] Signed release APK
- [ ] Internal testing track → Closed testing → Open testing → Production
- [ ] Price: Free (BYOK model — user pays their own API costs)

### Deliverable
VoxInk v1.0 on Google Play Store.

---

## Phase 6: Post-Launch & Future (Month 2+)

### 6.1 Additional Languages (Post-v1)
- [ ] Add Korean (ko), Spanish (es), French (fr), German (de), Thai (th), Vietnamese (vi)
- [ ] Each language needs: refinement prompt + filler word list
- [ ] Whisper STT works out-of-box, only LLM refinement needs tuning

### 6.2 Taiwanese Hokkien Support (Research)
- [ ] Contact 意傳科技 (ithuan.tw) — ask about STT API availability
- [ ] Contact 教育部臺灣台語輸入法 team (ttgsujip@mail.moe.gov.tw) — ask if ASR engine has API/SDK for third-party use
- [ ] Evaluate 廖元甫教授 (NYCU) fine-tuned models / 臺灣台語語料庫
- [ ] Evaluate Google Cloud Speech-to-Text for nan-tw
- [ ] Evaluate self-hosting fine-tuned Whisper (e.g., ChineseTaiwaneseWhisper)
- [ ] Implement as additional STT provider option
- [ ] Challenge: output format (台語漢字? 臺羅拼音? 華語翻譯? All three?)

### 6.3 Context-Aware Tone (Typeless Feature)
- [ ] Detect foreground app via `AccessibilityService` or `UsageStatsManager`
- [ ] Adjust LLM prompt based on context:
  - Email app → formal tone
  - LINE/Messenger → casual tone
  - Notes → structured
- [ ] User-configurable per-app prompt overrides

### 6.4 Speak-to-Edit (Typeless Feature)
- [ ] Select text in any app → invoke VoxInk
- [ ] Speak editing command: "make it shorter" / "翻成英文" / "改成正式語氣"
- [ ] LLM processes selected text + voice command → replace text
- [ ] Requires Accessibility Service

### 6.5 Personal Dictionary
- [ ] User adds custom words (names, jargon, abbreviations)
- [ ] Injected into Whisper prompt for better recognition
- [ ] Injected into LLM context

### 6.6 Usage Statistics
- [ ] Track words dictated, time saved (local only)
- [ ] Display stats in settings (like Typeless does)
- [ ] "You've saved X hours this month"

### 6.7 iOS Version (Future)
- [ ] Evaluate architecture: Kotlin Multiplatform for shared logic
- [ ] iOS Custom Keyboard Extension (requires Full Access for mic + network)
- [ ] Alternative: iOS Shortcut / Action Extension instead of keyboard
- [ ] Shared: API layer, refinement logic, prompts
- [ ] Platform-specific: UI, audio recording, keyboard service

---

## Technical Decisions Log

### Why Kotlin full rewrite (not incremental Java→Kotlin)?
Dictate's codebase is ~94% Java with tightly coupled UI and logic. Incremental conversion would create a messy hybrid. A clean Kotlin rewrite with Compose lets us architect properly from the start. We reference Dictate's logic but don't carry its technical debt.

### Why Jetpack Compose for keyboard UI?
Compose offers faster iteration for the candidate bar and settings UI. However, the base keyboard view may need XML if Compose-in-IME has performance issues. We'll benchmark and fall back to XML only if needed.

### Why BYOK instead of SaaS?
1. No server cost for us
2. User controls their data
3. Near-zero marginal cost for users (Groq free tier is generous)
4. Appeals to privacy-conscious users
5. No vendor lock-in — user can switch providers

### Why Groq as default STT provider?
1. Fastest Whisper inference (~0.5s for 30s audio)
2. Generous free tier
3. Same API format as OpenAI (easy to add both)
4. Supports `whisper-large-v3-turbo` (best speed/quality ratio)

### Whisper Language Support
V1 supports 3 languages + mixed-language:
- **中文** (zh-TW): Traditional Chinese with prompt bias
- **English** (en)
- **日本語** (ja)
- **Auto-detect** (default): best for mixed-language input (中英混合、日中混合)

For mixed-language, we omit the `language` parameter and use prompt hints like "繁體中文，可能夾雜英文" to help Whisper. This works because Whisper large-v3 naturally handles code-switching when not forced to a single language. More languages (ko, es, fr, etc.) can be added post-v1 — Whisper supports 90+ natively, we just need to add refinement prompts.

### Taiwanese Hokkien — Current State (2025)
Whisper does NOT officially support Taiwanese Hokkien (nan-tw). However:
- With `language=zh`, Whisper transcribes Taiwanese speech → Mandarin Chinese text (transliteration)
- 教育部臺灣台語輸入法 (launched Jan 2026) has working台語 ASR (speech → 台語漢字 or 臺羅拼音), but no public API
- The ASR engine likely comes from 廖元甫教授 (NYCU) team's 208-hour annotated corpus project
- 意傳科技 has open-source台語 NLP tools but STT API availability is unclear
- This is a post-v1 research item with concrete investigation steps in Phase 6

---

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Play Store IME review rejection | High | Thorough privacy policy, no data collection, clear disclosures |
| Groq API changes/pricing | Medium | Multi-provider support, easy to switch |
| Compose-in-IME performance | Medium | Benchmark early, fall back to XML if needed |
| Chinese refinement quality | Medium | Extensive prompt engineering, user-customizable prompts |
| Competition (Typeless grows) | Low | Different value prop (BYOK, open, zero cost) |

---

## Success Metrics (v1.0)

- [ ] IME works reliably in top 10 Android apps (LINE, Messenger, Gmail, etc.)
- [ ] STT latency < 2s for 15-second recordings (Groq)
- [ ] LLM refinement latency < 3s total (including STT)
- [ ] Accurate transcription in zh-TW, en, ja and mixed (中英混合)
- [ ] Auto-detect correctly identifies language in 90%+ of cases
- [ ] Zero crashes in 1-week dogfooding
- [ ] Successful Play Store publication
- [ ] 繁中 + English UI complete
