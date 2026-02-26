# CLAUDE.md — VoxPen (語墨)

## Project Overview

VoxPen is an Android AI voice keyboard and transcription app, forked from [Dictate Keyboard](https://github.com/DevEmperor/Dictate) (Apache 2.0). The goal is to build a Typeless-quality UX with a BYOK (Bring Your Own Key) model, supporting Traditional Chinese and English.

**App Name**: VoxPen (語墨)
**Package**: `com.voxpen.app`
**Original Fork**: `net.devemperor.dictate` → full rewrite to Kotlin

## Tech Stack

- **Language**: Kotlin (full rewrite from Java)
- **UI**: Jetpack Compose + Material 3 (Material You)
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 35
- **Build**: Gradle (Kotlin DSL)
- **Architecture**: MVVM + Repository pattern
- **DI**: Hilt
- **Async**: Kotlin Coroutines + Flow
- **Network**: Retrofit + OkHttp
- **Local Storage**: DataStore (preferences), Room (transcription history)
- **Audio**: AudioRecord API (PCM → WAV/OPUS encoding)

## Core Features

### 1. IME Voice Keyboard
- Android `InputMethodService` based
- Minimal keyboard layout: large mic button + utility keys (backspace, space, enter, 🌐 switch)
- Press-and-hold or tap-to-toggle recording
- Dual result display: "Original" vs "Refined" (LLM-edited) in candidate bar
- User taps to select which version to commit
- Quick switch to previous keyboard via `switchToPreviousInputMethod()`

### 2. STT Providers (BYOK)
- **Groq Whisper** (primary): `whisper-large-v3-turbo` via REST API
- **OpenAI Whisper**: `whisper-1`, `gpt-4o-transcribe` as alternative
- **Custom Server**: user-defined endpoint (for self-hosted Whisper, etc.)

#### Supported Languages (v1)
- **Mandarin Chinese** (zh): Traditional Chinese output, prompt bias for 繁體
- **English** (en)
- **Japanese** (ja)
- **Auto-detect** (default): let Whisper detect language automatically
- **Mixed-language**: supports code-switching (e.g., 中英混合、日中混合). Whisper handles this natively when set to auto-detect or with prompt hints like "繁體中文，可能夾雜英文"

User can select: Auto / 中文 / English / 日本語 in settings or quick-switch from keyboard.

#### Taiwanese Hokkien (Future)
Whisper does NOT natively support Taiwanese Hokkien (nan-tw). With `language=zh`, Taiwanese speech is transcribed as Mandarin text (lossy but usable). Native Taiwanese ASR is a post-v1 goal — potential partners: 意傳科技, 教育部臺灣台語輸入法 team, or self-hosted fine-tuned models.

### 3. LLM Refinement (BYOK)
- **Purpose**: Transform raw speech into polished written text
- **Providers**: Groq (LLaMA), OpenAI (GPT), Anthropic (Claude), custom endpoint
- **Features** (Typeless-inspired):
  - Remove filler words (per-language: 嗯/那個/就是 for zh, um/uh/like for en, えーと/あの for ja)
  - Auto-detect and keep only final self-correction
  - Auto-format lists and structure
  - Context-aware tone adaptation (detect target app if possible)
  - Custom system prompts per user preference
  - Translation mode (speak Chinese → output English, and vice versa)
  - **Mixed-language aware**: handles 中英混合 output without forcing single language

### 4. Audio File Transcription
- Separate Activity/Screen for batch transcription
- File picker: audio/video files
- Auto-chunking for files > 25MB (silence detection or fixed segments)
- Progress tracking, export as text/SRT

### 5. Settings & BYOK Management
- Per-provider API key storage (EncryptedSharedPreferences)
- STT provider selector (Groq / OpenAI / Custom)
- LLM provider selector (Groq / OpenAI / Anthropic / Custom)
- Language preference (auto-detect / zh-TW / en / ja)
- Refinement toggle (on/off)
- Custom prompts management (save/load/export)
- Theme: follow system / light / dark

## UI/UX Design Principles

### Typeless-Inspired Design Language
- **Clean, minimal, modern** — no visual clutter
- **Large touch targets** — especially the mic button
- **Smooth animations** — recording pulse, text transition
- **Two-line result preview** — original (dimmed) vs refined (prominent)
- **One-tap commit** — tap refined text to insert, long-press for original
- **Status indicators** — recording (red pulse), processing (spinner), ready (green)

### i18n
- Primary: Traditional Chinese (zh-TW), English (en)
- All user-facing strings in `strings.xml` with translations
- Use Android resource qualifiers (`values-zh-rTW/`, `values/`)

### Keyboard Layout (IME)
```
┌─────────────────────────────────┐
│  App input field                │  ← system pushes up
├─────────────────────────────────┤
│  🔵 Original: [raw text here]   │  ← candidate row 1 (tappable)
│  ✨ Refined:  [polished text]   │  ← candidate row 2 (tappable)
├─────────────────────────────────┤
│  🌐  │  ⌫  │   🎤   │  ⏎  │ ⚙️ │  ← compact key row
└─────────────────────────────────┘
```
Height: as compact as possible (~180dp total)

## Project Structure

```
app/src/main/
├── java/com/voxpen/app/
│   ├── VoxPenApplication.kt          # Hilt application
│   ├── di/                             # Dependency injection modules
│   │   ├── AppModule.kt
│   │   ├── NetworkModule.kt
│   │   └── DatabaseModule.kt
│   ├── data/
│   │   ├── local/
│   │   │   ├── PreferencesManager.kt   # DataStore wrapper
│   │   │   ├── TranscriptionDao.kt     # Room DAO
│   │   │   └── AppDatabase.kt
│   │   ├── remote/
│   │   │   ├── GroqApi.kt             # Groq Whisper + LLM
│   │   │   ├── OpenAiApi.kt           # OpenAI Whisper + GPT
│   │   │   ├── AnthropicApi.kt        # Claude API
│   │   │   └── CustomApi.kt           # User-defined endpoint
│   │   ├── model/
│   │   │   ├── TranscriptionResult.kt
│   │   │   ├── RefinementResult.kt
│   │   │   ├── ApiProvider.kt         # Enum: GROQ, OPENAI, ANTHROPIC, CUSTOM
│   │   │   └── SttProvider.kt
│   │   └── repository/
│   │       ├── SttRepository.kt       # STT abstraction layer
│   │       ├── LlmRepository.kt       # LLM abstraction layer
│   │       └── SettingsRepository.kt
│   ├── domain/
│   │   ├── usecase/
│   │   │   ├── TranscribeAudioUseCase.kt
│   │   │   ├── RefineTextUseCase.kt
│   │   │   └── ChunkAudioUseCase.kt
│   │   └── model/
│   │       └── VoiceInputResult.kt    # Original + Refined pair
│   ├── ime/
│   │   ├── VoxPenIME.kt            # InputMethodService
│   │   ├── AudioRecorder.kt          # Mic recording handler
│   │   ├── KeyboardView.kt           # Compose-based keyboard UI
│   │   └── CandidateView.kt          # Original vs Refined display
│   ├── ui/
│   │   ├── theme/
│   │   │   ├── Theme.kt              # Material 3 theme
│   │   │   ├── Color.kt
│   │   │   └── Type.kt
│   │   ├── settings/
│   │   │   ├── SettingsScreen.kt
│   │   │   ├── ApiKeyScreen.kt
│   │   │   ├── PromptsScreen.kt
│   │   │   └── SettingsViewModel.kt
│   │   ├── transcription/
│   │   │   ├── TranscriptionScreen.kt
│   │   │   ├── TranscriptionViewModel.kt
│   │   │   └── FilePickerHelper.kt
│   │   ├── onboarding/
│   │   │   └── OnboardingScreen.kt    # First-run setup wizard
│   │   └── MainActivity.kt
│   └── util/
│       ├── AudioEncoder.kt           # PCM → WAV/OPUS
│       ├── AudioChunker.kt           # Split large files
│       ├── FillerWordRemover.kt      # Chinese + English filler removal
│       └── Extensions.kt
├── res/
│   ├── layout/                        # Only for IME (non-Compose)
│   ├── xml/
│   │   └── method.xml                 # IME declaration
│   ├── values/
│   │   └── strings.xml                # English (default)
│   └── values-zh-rTW/
│       └── strings.xml                # Traditional Chinese
└── AndroidManifest.xml
```

## API Interaction Patterns

### Groq Whisper STT
```
POST https://api.groq.com/openai/v1/audio/transcriptions
Headers: Authorization: Bearer {GROQ_API_KEY}
Body (multipart/form-data):
  file: audio.wav
  model: whisper-large-v3-turbo
  language: zh (or en, ja, omit for auto-detect)
  response_format: verbose_json
  prompt: "繁體中文轉錄。" (for zh)
         "繁體中文，可能夾雜英文。" (for mixed zh-en, use auto-detect)
```

### LLM Refinement
```
POST https://api.groq.com/openai/v1/chat/completions
Body:
  model: llama-3.3-70b-versatile (or user-selected)
  messages:
    - system: [refinement system prompt]
    - user: [raw transcription text]
  temperature: 0.3
  max_tokens: 2048
```

### Default Refinement System Prompts

**zh-TW (Traditional Chinese)**
```
你是一個語音轉文字的編輯助手。請將以下口語內容整理為流暢的書面文字：
1. 移除贅字（嗯、那個、就是、然後、對、呃）
2. 如果說話者中途改口，只保留最終的意思
3. 修正語法但保持原意
4. 適當加入標點符號
5. 不要添加原文沒有的內容
6. 保持繁體中文
只輸出整理後的文字，不要加任何解釋。
```

**en (English)**
```
You are a voice-to-text editor. Clean up the following speech transcription into polished written text:
1. Remove filler words (um, uh, like, you know, I mean, basically, actually, so)
2. If the speaker corrected themselves mid-sentence, keep only the final version
3. Fix grammar while preserving the original meaning
4. Add proper punctuation
5. Do not add content that wasn't in the original speech
Output only the cleaned text, no explanations.
```

**ja (Japanese)**
```
あなたは音声テキスト変換の編集アシスタントです。以下の口語内容を整った書き言葉に整理してください：
1. フィラー（えーと、あの、まあ、なんか、ちょっと）を除去
2. 言い直しがある場合は最終的な意味のみ残す
3. 文法を修正し、原意を保持
4. 適切に句読点を追加
5. 原文にない内容を追加しない
整理後のテキストのみ出力し、説明は不要です。
```

**Mixed-language (zh-en, zh-ja, etc.)**
```
你是一個語音轉文字的編輯助手。以下口語內容可能包含多種語言混合使用（如中英混合），請保持原本的語言混合方式，整理為流暢的書面文字：
1. 移除各語言的贅字
2. 如果說話者中途改口，只保留最終的意思
3. 修正語法但保持原意和原本的語言選擇
4. 適當加入標點符號
5. 不要把外語強制翻譯成中文
只輸出整理後的文字，不要加任何解釋。
```

## Development Guidelines

### Code Style
- Kotlin official style guide
- Use `ktlint` for formatting
- Prefer immutable data (`val`, `data class`, `sealed class/interface`)
- Use `sealed interface` for UI states
- Coroutines for all async work, never block main thread
- Use `Flow` for reactive data streams

### Key Conventions
- All API calls go through Repository layer
- ViewModels expose `StateFlow` to UI
- Error handling: `Result<T>` or sealed class with Success/Error/Loading
- Logging: use Timber
- No hardcoded strings in UI code — always use string resources
- API keys never logged or exposed in UI beyond masked display

### TDD — Test-Driven Development

開發遵循 TDD，核心循環為 **Red-Green-Refactor**：

1. **Red** — 先寫一個會失敗的測試，定義預期行為
2. **Green** — 寫最少量的程式碼讓測試通過（不多不少）
3. **Refactor** — 在測試保護下重構，消除重複、改善設計

原則：
- 每次只加一個測試，確認失敗後才寫實作
- 不寫沒有對應測試的產品程式碼
- Refactor 階段測試必須持續全綠
- 目標覆蓋率 ≥ 80%

#### 測試工具

| 層級 | 工具 |
|------|------|
| Unit tests | JUnit 5 + MockK |
| Flow testing | Turbine |
| API tests | MockWebServer (OkHttp) |
| UI tests | Compose Testing (`createComposeRule`) |
| Assertions | Truth / JUnit assertions |

#### 測試命名慣例

```kotlin
@Test
fun `should return transcription when API responds successfully`() { ... }

@Test
fun `should emit error state when network fails`() { ... }
```

### Testing Priority
1. Repository layer (unit tests with MockWebServer)
2. Use cases (unit tests)
3. ViewModel (unit tests with test dispatchers)
4. IME integration (manual testing on device — emulator unreliable for IME)

### Git Workflow
- `main` branch: stable releases
- `dev` branch: active development
- Feature branches: `feature/xxx`
- Commit messages: conventional commits (`feat:`, `fix:`, `refactor:`, `docs:`)

## Reference: Dictate Keyboard

The original [Dictate Keyboard](https://github.com/DevEmperor/Dictate) (Apache 2.0) is cloned locally at `reference/dictate/` for studying its implementation patterns. This directory is git-ignored and NOT part of our source tree.

### Key Reference Files

| Dictate File | What to Learn |
|---|---|
| `reference/dictate/app/src/main/java/net/devemperor/dictate/core/DictateInputMethodService.java` | IME lifecycle, audio recording flow, InputConnection usage |
| `reference/dictate/app/src/main/java/net/devemperor/dictate/core/DictatePunctuationKeyboard.java` | Key layout and button handling patterns |
| `reference/dictate/app/src/main/java/net/devemperor/dictate/rewording/` | LLM rewording/refinement logic |
| `reference/dictate/app/src/main/java/net/devemperor/dictate/settings/` | Settings and API key management |
| `reference/dictate/app/src/main/java/net/devemperor/dictate/onboarding/` | First-run setup wizard flow |
| `reference/dictate/app/src/main/res/` | Layouts, XML resources, IME declarations |

### Migration Approach

1. **Do NOT convert file-by-file** — rewrite from scratch in Kotlin using Dictate's logic as reference
2. Study Dictate's `DictateInputMethodService.java` for IME lifecycle patterns, then rewrite idiomatically
3. Study Dictate's API call patterns, then rewrite with Retrofit + Coroutines
4. Dictate's UI is XML-based Views — we use Jetpack Compose entirely (except IME keyboard view which may need XML)
5. When implementing a new feature, READ the corresponding Dictate file first to understand the problem space, then write clean Kotlin

## Known Limitations & Future Work

- **Taiwanese Hokkien**: Whisper (Groq/OpenAI) transcribes to Mandarin Chinese, not native Taiwanese. Future: investigate 意傳科技 API, 教育部臺灣台語輸入法 ASR engine, or fine-tuned Whisper models
- **v1 Languages**: zh-TW, en, ja + mixed-language (code-switching). Other Whisper languages can be added post-v1
- **iOS version**: Not in current scope. Future consideration: KMP for shared logic, native Swift UI for keyboard
- **Offline mode**: Not in v1. Future: on-device Whisper via whisper.cpp / FUTO approach
- **Context-aware tone**: v1 uses a single refinement prompt per language. Future: detect foreground app and adjust
