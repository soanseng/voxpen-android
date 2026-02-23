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
- [x] ~~Fork `DevEmperor/Dictate` on GitHub~~ Clean rewrite, not a fork
- [x] Create new branch `dev` for all development
- [x] Set up Android project with Kotlin DSL Gradle
- [x] Change package name to `com.voxink.app`
- [x] Change app name to "VoxInk" / "語墨"
- [x] Add `.editorconfig`, `ktlint`, `detekt` configs
- [x] Set up CI (GitHub Actions): lint + build

### 0.2 Study Dictate Codebase
- [ ] Map out Dictate's Java classes and their responsibilities
- [ ] Identify core IME lifecycle (`DictateInputMethodService.java`)
- [ ] Understand Dictate's API call flow (Whisper → GPT)
- [ ] Note Dictate's UI layout XML files
- [ ] Document which parts to keep vs rewrite

### Deliverable
Clean Kotlin project skeleton that builds and runs (blank IME).

**Status: COMPLETE** (v0.0.1 tagged 2026-02-23)
- 21 unit tests passing (JUnit 5 + Truth + MockK)
- `./gradlew clean ktlintCheck detekt test assembleDebug` all green
- TDD: test files committed before production code for all `.kt` files
- 6 test files, 8 production files, CI pipeline ready

---

## Phase 1: Core IME + Groq STT (Week 1-2)

### 1.1 Minimal IME Service
- [x] Create `VoxInkIME.kt` extending `InputMethodService`
- [x] Register IME in `AndroidManifest.xml` + `method.xml`
- [x] Implement basic keyboard view (XML):
  - 🎤 Mic button (center, large)
  - ⌫ Backspace
  - ␣ Space
  - ⏎ Enter
  - 🌐 Switch keyboard
  - ⚙️ Settings
- [x] Handle `commitText()` to insert text into target app
- [x] Handle `switchToPreviousInputMethod()` / `switchToNextInputMethod()`

### 1.2 Audio Recording
- [x] Implement `AudioRecorder.kt` using `AudioRecord` API
- [x] Support two modes:
  - **Hold-to-record**: press and hold mic, release to stop
  - **Tap-to-toggle**: tap to start, tap again to stop
- [x] Encode PCM → WAV
- [ ] Visual feedback: recording indicator (red pulse animation)
- [ ] Audio cue: subtle start/stop sound + haptic

### 1.3 Groq Whisper Integration
- [x] Create `GroqApi.kt` Retrofit interface
- [x] Implement `SttRepository.kt` with Groq as default provider
- [x] Send audio → receive transcription
- [x] Language modes:
  - **Auto-detect** (default): best for mixed-language (中英混合)
  - **中文** (zh): prompt bias for 繁體中文
  - **English** (en)
  - **日本語** (ja)
- [x] Mixed-language support: omit `language` param + use prompt hint "繁體中文，可能夾雜英文"
- [x] Per-language initial_prompt for better accuracy
- [x] Error handling: network errors, API errors, timeout
- [x] Display transcription in candidate bar → commit on tap

### 1.4 API Key Setup
- [x] Create basic Settings screen (Jetpack Compose)
- [x] API key input for Groq (masked display)
- [x] Store with `EncryptedSharedPreferences`
- [ ] Validate key on save (test API call)
- [ ] First-run onboarding: guide user to get Groq API key

### Deliverable
Working voice keyboard: tap mic → speak → see Chinese/English text → tap to insert.

**Status: COMPLETE** (v0.1.0 tagged 2026-02-23)
- 50+ unit tests passing (JUnit 5 + Truth + MockK + Turbine + MockWebServer)
- Full lint pipeline green (ktlint + detekt)
- TDD throughout: tests written before production code
- 37 files changed, 2137 insertions

---

## Phase 2: LLM Refinement + Dual Display (Week 3-4)

### 2.1 LLM Integration
- [x] Create LLM API interfaces (Groq LLaMA chat completion)
- [x] Implement `LlmRepository.kt` with Groq provider
- [x] Default refinement system prompt (zh-TW, en, ja, mixed)
- [x] Refinement pipeline:
  1. STT returns raw text → display immediately as "Original"
  2. Send to LLM for refinement → show "Refining" state
  3. Refined text arrives → display as "Refined"

### 2.2 Dual Result UI (Candidate Bar)
- [x] Design candidate bar layout (status row + original row + refined row)
- [x] Tap behavior: insert selected version (original or refined)
- [ ] Swipe left/right to switch between versions
- [ ] Long text: scrollable within candidate bar
- [x] Loading state: show spinner on refined line while LLM processes
- [x] Toggle: user can disable refinement (only show original)

### 2.3 Typeless-Inspired Post-Processing
- [x] **Filler word removal** (via LLM prompt per language)
- [x] **Self-correction detection** (via LLM prompt)
- [x] **Auto-punctuation** (via LLM prompt)
- [x] **Mixed-language handling** (dedicated mixed-language prompt)
- [ ] **Repetition removal**: local pre-processing
- [ ] **List formatting**: detect numbered items and format
- [ ] Hybrid: local pre-clean → LLM polish

### 2.4 Settings Expansion
- [ ] STT provider selector (Groq / OpenAI / Custom endpoint)
- [ ] LLM provider selector (Groq / OpenAI / Anthropic / Custom)
- [ ] Per-provider API key management
- [ ] LLM model selector per provider
- [x] Refinement on/off toggle
- [x] Language preference: Auto-detect / 中文 / English / 日本語
- [ ] Per-language refinement prompt (user-customizable)
- [ ] Mixed-language mode hint in auto-detect

### Deliverable
Full voice keyboard with Original vs Refined display, BYOK for multiple providers.

**Status: COMPLETE** (v0.2.0 tagged 2026-02-23)
- 95 unit tests passing (JUnit 5 + Truth + MockK + Turbine + MockWebServer)
- Full lint pipeline green (ktlint + detekt)
- TDD throughout: tests written before production code
- 25 files changed, 999 insertions
- Core LLM refinement pipeline: ChatCompletion API → LlmRepository → RefineTextUseCase
- Per-language system prompts (zh-TW, en, ja, mixed)
- Dual candidate display in keyboard (original + refined rows)
- Refinement toggle in Settings with DataStore persistence

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

---

## Phase 7: Monetization — Free + Pro + Ads (Post-Launch)

### 7.1 Free vs Pro 功能分級

| 功能 | Free | Pro |
|------|------|-----|
| **語音輸入 (IME)** | 20 次/天 | 無限制 |
| **STT 提供者** | Groq only | Groq + OpenAI + 自訂端點 |
| **語言選擇** | Auto-detect only | Auto / 中文 / English / 日本語 |
| **LLM 智慧潤稿** | 5 次/天 | 無限制 |
| **音檔轉逐字稿** | 2 檔/天，每檔 ≤ 3 分鐘 | 無限制，每檔 ≤ 25MB |
| **逐字稿歷史紀錄** | 保留 7 天 | 無限保留 |
| **匯出格式** | 複製到剪貼簿 | 純文字 / SRT 字幕 / 分享 |
| **自訂潤稿 Prompt** | ❌（僅預設） | ✅ 可自訂/儲存/匯出 |
| **Per-App 語氣設定** | ❌ | ✅ |
| **主題** | 跟隨系統 | 淺色 / 深色 / 跟隨系統 |
| **廣告** | 有（非 IME 畫面） | 無廣告 |

#### 設計理念

- **Free 要夠用**：20 次/天的語音輸入足夠輕度使用者日常使用，Auto-detect 模式涵蓋大多數場景
- **Pro 差異明確**：多 STT 提供者、無限潤稿、完整匯出、自訂 Prompt 是 power user 剛需
- **BYOK 心理**：用戶已付 API 費用，Pro 收費應定位為「app 本身的開發價值」而非「功能解鎖」，避免雙重收費感

### 7.2 廣告策略

#### 廣告 SDK
- Google AdMob（唯一選擇，Play Store 最友好）
- 整合 UMP（User Messaging Platform）處理 consent

#### 廣告位置與格式

| 位置 | AdMob 格式 | 尺寸 | 觸發時機 |
|------|-----------|------|---------|
| 設定頁面底部 | Banner | 320×50 (BANNER) | 進入設定頁時載入 |
| 逐字稿歷史列表 | Native Advanced | In-feed | 每 5 筆紀錄插入 1 則 |
| 音檔轉寫完成後 | Interstitial | 全螢幕 | 轉寫結果顯示後 |
| 每日用量達上限 | Rewarded | 全螢幕（用戶主動觀看） | 看廣告換 5 次額外語音輸入 |
| 主畫面（轉寫頁）底部 | Banner | 320×50 (BANNER) | 進入轉寫頁時載入 |

#### 絕對不放廣告的位置

- **IME 鍵盤視窗內**：Play Store 對 IME 內廣告審查極嚴，極高機率被拒
- **錄音 / 處理中**：打斷工作流程，UX 災難
- **Onboarding 流程**：第一印象最重要
- **候選文字列（Original / Refined）**：核心交互區，絕不能有干擾

#### 頻率控制

- Interstitial：同一 session 間隔 ≥ 5 分鐘，每日最多 3 次
- 新用戶前 3 天不顯示 Interstitial（先讓用戶養成習慣）
- Rewarded 不設頻率限制（用戶主動選擇觀看）
- Banner 使用 Adaptive Banner 自動調整尺寸

### 7.3 定價策略

#### 建議方案：一次買斷

| 方案 | 價格 | 說明 |
|------|------|------|
| **正式價** | NT$150（~US$4.99） | 介於 Dictate ($3.49) 和 FUTO ($10) 之間 |
| **上架促銷價** | NT$99（~US$2.99） | 前 1000 名或前 30 天，吸引早期用戶 |

#### 為什麼選一次買斷而非訂閱

1. **BYOK 模式已有持續成本**：用戶每月付 API 費用，再加月費心理抗拒極高
2. **台灣市場消費習慣**：小額 app 偏好一次買斷（$3-5 美金甜蜜點），訂閱制需要極強價值感
3. **競品參考**：Dictate $3.49 買斷、FUTO $10 買斷都賣得不錯；Typeless $12-30/月是 SaaS 模式不同
4. **Play Store 內購簡單**：一次性 IAP（In-App Purchase）實作比訂閱簡單，不需處理續訂/過期邏輯
5. **口碑效應**：「只要 NT$99 就能去廣告 + 完整功能」是強力推薦誘因

#### 未來追加營收選項（非 v1）

- **VoxInk+ 訂閱**（NT$99/月或 NT$799/年）：解鎖 Phase 6 進階功能（Context-Aware Tone、Speak-to-Edit、個人字典同步）
- 分層：Pro = 完整基礎功能，VoxInk+ = 持續更新的 AI 進階功能

### 7.4 技術實作

#### 依賴項

```kotlin
// build.gradle.kts (app)
implementation("com.google.android.gms:play-services-ads:23.x.x")
implementation("com.google.android.ump:user-messaging-platform:3.x.x")
implementation("com.android.billingclient:billing-ktx:7.x.x")
```

#### 架構

```
app/src/main/java/com/voxink/app/
├── billing/
│   ├── BillingManager.kt          # Google Play Billing 封裝
│   ├── ProStatus.kt               # sealed interface: Free / Pro
│   └── UsageLimiter.kt            # 每日用量追蹤（DataStore）
├── ads/
│   ├── AdManager.kt               # AdMob 初始化 + consent
│   ├── BannerAdView.kt            # Compose wrapper for Banner
│   ├── InterstitialAdLoader.kt    # Interstitial 預載 + 頻率控制
│   ├── RewardedAdLoader.kt        # Rewarded 廣告邏輯
│   └── NativeAdProvider.kt        # Native ad 資料來源
```

#### 用量追蹤

```kotlin
// UsageLimiter.kt — DataStore-based daily counter
data class DailyUsage(
    val date: LocalDate,
    val voiceInputCount: Int = 0,
    val refinementCount: Int = 0,
    val fileTranscriptionCount: Int = 0
)
```

- 每日零點自動重置
- 用量接近上限時顯示提示（「今日剩餘 3 次語音輸入」）
- 達到上限後顯示 Rewarded Ad 選項 + 升級 Pro 按鈕

### 7.5 Play Store 上架注意事項

#### IME 相關審查

- [ ] 隱私政策必須明確說明 IME 權限用途（麥克風、網路）
- [ ] 聲明所有語音資料僅傳送至用戶自選的 API（Groq/OpenAI），app 不經手
- [ ] IME 內禁止任何廣告元素（包括 banner、按鈕、品牌 logo）
- [ ] 資料安全區（Data Safety Section）揭露：
  - 收集：無（BYOK 模式，API key 僅存本機）
  - 分享：無（語音資料直接傳至用戶選擇的 STT 提供者）
  - 廣告追蹤：是（AdMob，僅在非 IME 畫面）

#### AdMob 合規

- [ ] 整合 UMP consent（GDPR + 台灣 PDPA 考量）
- [ ] 不可在鎖定螢幕或系統 UI 上顯示廣告
- [ ] Ad placement 需通過 AdMob Policy Center 審查
- [ ] 測試用 test ad unit IDs，上線前切換正式 IDs

#### 內購 (IAP) 合規

- [ ] Pro 升級使用 Google Play Billing Library（不可用第三方支付）
- [ ] 提供「恢復購買」功能（用戶換機時）
- [ ] 免費試用期不適用於一次買斷（僅訂閱需要）
- [ ] 價格透明：在 app 內清楚標示 Pro 功能對照

---

## Risk Assessment (Updated)

| Risk | Impact | Mitigation |
|------|--------|------------|
| Play Store IME review rejection | High | Thorough privacy policy, no data collection, clear disclosures |
| Groq API changes/pricing | Medium | Multi-provider support, easy to switch |
| Compose-in-IME performance | Medium | Benchmark early, fall back to XML if needed |
| Chinese refinement quality | Medium | Extensive prompt engineering, user-customizable prompts |
| Competition (Typeless grows) | Low | Different value prop (BYOK, open, zero cost) |
| AdMob policy violation in IME | High | 嚴格隔離：IME 內零廣告，僅在 Activity 畫面顯示 |
| Free tier 太慷慨導致轉化率低 | Medium | 追蹤用量數據，A/B test 限制閾值（15 vs 20 vs 25 次/天） |
| Free tier 太嚴苛導致用戶流失 | Medium | 提供 Rewarded Ad 作為緩衝，監控 D1/D7 retention |
| BYOK + 付費的雙重收費感 | Medium | 清楚傳達 Pro = 支持開發者 + 去廣告 + 便利功能 |
