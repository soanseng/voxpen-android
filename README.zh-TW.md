<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" width="100" alt="VoxPen logo" />
</p>

<h1 align="center">VoxPen (語墨)</h1>

<p align="center">
  開源 AI 語音鍵盤，專為 Android 打造。<br/>
  自然說話，即時取得精修文字。
</p>

<p align="center">
  <a href="https://github.com/soanseng/voxpen-android/releases"><img src="https://img.shields.io/github/v/release/soanseng/voxpen-android?style=flat-square" alt="Release" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/soanseng/voxpen-android?style=flat-square" alt="License" /></a>
  <a href="https://github.com/soanseng/voxpen-android/actions"><img src="https://img.shields.io/github/actions/workflow/status/soanseng/voxpen-android/ci.yml?style=flat-square&label=CI" alt="CI" /></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-green?style=flat-square" alt="Min SDK 26" />
</p>

<p align="center">
  <a href="https://voxpen.app">Website</a> &nbsp;|&nbsp;
  <a href="README.md">English</a>
</p>

---

## 截圖

<p align="center">
  <img src="docs/screenshots/home.png" width="180" alt="主畫面" />
  &nbsp;&nbsp;
  <img src="docs/screenshots/keyboard.png" width="180" alt="鍵盤" />
  &nbsp;&nbsp;
  <img src="docs/screenshots/keyboard-tone.png" width="180" alt="語氣選擇" />
  &nbsp;&nbsp;
  <img src="docs/screenshots/settings-llm.png" width="180" alt="設定 - LLM" />
  &nbsp;&nbsp;
  <img src="docs/screenshots/settings-tone.png" width="180" alt="設定 - 語氣" />
</p>

---

## VoxPen 是什麼？

VoxPen 是一款 Android 語音鍵盤，透過 Whisper 將語音轉為文字，再以 LLM 潤稿，將乾淨的文字插入任何 App。採用 **BYOK（自帶金鑰）** 模式 — 使用你自己的 API 金鑰，用多少付多少。無需訂閱、不收集資料、完全開源。

## 功能特色

### 語音聽寫
點擊麥克風說話，VoxPen 透過 Whisper 轉錄，可選擇以 LLM 潤稿，候選列同時顯示原文與潤稿版本。點擊即可插入。

### 翻譯模式
說 A 語言，輸出 B 語言。直接在鍵盤上快速切換翻譯目標語言，無需進入設定。

### 語音編輯
在任何 App 中選取文字，切換到 VoxPen，啟用編輯模式，說出你的指令（例如「讓它更正式」）。LLM 會直接改寫選取的文字。

### 自動語氣
VoxPen 偵測目前使用的 App，自動選擇適合的寫作風格 — 通訊軟體用口語、Email 用正式語氣、Slack 用專業語氣。支援自訂 App 規則。

### 語音指令
10 個三語指令（中/英/日）— 送出、刪除、換行、空格、復原、全選、複製、貼上、剪下、全部刪除。不需呼叫 API。

### 音檔轉錄
匯入音訊/影片檔案進行轉錄，支援進度追蹤。可匯出為 TXT 純文字或 SRT 字幕檔。

### 隱私優先
- **BYOK**：音訊從你的裝置直接傳送至 API 服務商
- **無遙測、無分析、無使用者帳號**
- API 金鑰以 Android Keystore 加密儲存
- 僅需 2 個權限：`INTERNET` + `RECORD_AUDIO`

## 鍵盤配置

```
┌──────────────────────────────────────┐
│  🔄 說中文 → English            [×] │  ← 翻譯指示列
│  🔵 原文：[語音辨識結果]             │  ← 點擊插入
│  ✨ 潤稿：[精修文字]                 │  ← 點擊插入
├──────────────────────────────────────┤
│  🌐  │  ⌫  │    🎤    │  ⏎  │  ⚙️  │
└──────────────────────────────────────┘
```

| 按鍵 | 點擊 | 長按 |
|------|------|------|
| 🌐 | 切換上一個鍵盤 | 系統輸入法選擇器 |
| ⌫ | 退格 | — |
| 🎤 | 開始/停止錄音 | — |
| ⏎ | Enter | — |
| ⚙️ | 開啟設定 | 快速設定（語言/潤稿/翻譯/編輯模式） |

## 支援語言

| 語言 | 語音轉文字 | LLM 潤稿 | 翻譯 |
|------|-----------|---------|------|
| 中文（繁體） | Whisper | 專屬提示詞 | 目標/來源 |
| English | Whisper | 專屬提示詞 | 目標/來源 |
| 日本語 | Whisper | 專屬提示詞 | 目標/來源 |
| 自動偵測 | Whisper | 混合語言提示詞 | — |

Whisper 支援 99 種語言的語音轉文字。VoxPen 目前提供 3 種語言 + 自動偵測，並附有專屬潤稿提示詞。

## 支援的 API 服務商

| 服務商 | 語音轉文字 | LLM | 備註 |
|--------|-----------|-----|------|
| **Groq** | Whisper large-v3-turbo | LLaMA、Qwen 等 | 有免費額度 |
| **OpenAI** | Whisper、GPT-4o transcribe | GPT-4o 等 | |
| **Anthropic** | — | Claude | 僅 LLM |
| **自訂** | 任何 Whisper 相容端點 | 任何 OpenAI 相容端點 | 支援自架伺服器 |

## 開始使用

### 從 Release 安裝

1. 從 [Releases](https://github.com/soanseng/voxpen-android/releases) 下載最新 APK
2. 安裝至你的 Android 裝置（8.0 以上）
3. 依照引導精靈設定 API 金鑰

### 從原始碼建置

**前置需求**：Android Studio Ladybug+ / JDK 17

```bash
git clone https://github.com/soanseng/voxpen-android.git
cd voxpen-android
./gradlew assembleDebug
```

Debug APK 位於 `app/build/outputs/apk/debug/app-debug.apk`。

### 設定步驟

1. 至 [console.groq.com](https://console.groq.com) 免費取得 Groq API 金鑰
2. 開啟 VoxPen → 輸入 API 金鑰
3. 至 **設定 → 系統 → 鍵盤** 啟用 VoxPen Voice
4. 在任何文字欄位切換至 VoxPen，開始說話

## 架構

```
┌─────────────┐
│   IME 層     │  VoxPenIME (InputMethodService)
│              │  AudioRecorder, KeyboardView, CandidateView
├──────────────┤
│  Domain 層   │  TranscribeAudioUseCase, RefineTextUseCase, EditTextUseCase
├──────────────┤
│  Data 層     │  SttRepository, LlmRepository, SettingsRepository
│              │  Retrofit APIs, Room DB, DataStore
├──────────────┤
│  DI          │  Hilt modules (AppModule, NetworkModule)
└──────────────┘
```

- **語言**：Kotlin
- **UI**：Jetpack Compose + Material 3
- **依賴注入**：Hilt
- **非同步**：Coroutines + Flow
- **網路**：Retrofit + OkHttp
- **儲存**：DataStore（偏好設定）+ Room（歷史紀錄）
- **測試**：JUnit 5 + MockK + Turbine

## 貢獻

歡迎貢獻！步驟如下：

1. Fork 此儲存庫
2. 建立功能分支（`git checkout -b feature/my-feature`）
3. 進行修改
4. 執行測試：`./gradlew test`
5. 執行 lint 檢查：`./gradlew ktlintCheck detekt`
6. 使用 conventional commits 提交（`feat:`、`fix:`、`refactor:` 等）
7. 開啟 Pull Request

### 開發注意事項

- 專案遵循 TDD（測試驅動開發）— 先寫測試
- 提交 PR 前請執行 `./gradlew test`
- IME 測試需要實體裝置或已啟用鍵盤的模擬器
- 詳細架構文件請參閱 [CLAUDE.md](CLAUDE.md)

## 授權條款

```
Copyright 2026 VoxPen Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

VoxPen 是從 [Dictate Keyboard](https://github.com/DevEmperor/Dictate)（DevEmperor 開發，Apache 2.0 授權）fork 而來，已全面改寫為 Kotlin 並採用新架構。

## 隱私權

VoxPen 採用 BYOK 模式。你的音訊從裝置直接傳送至你選擇的 API 服務商，我們絕不會接觸你的資料。完整 [隱私權政策](docs/privacy-policy.md)。
