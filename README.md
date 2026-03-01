# VoxPen (語墨)

AI voice keyboard for Android. Speak — VoxPen transcribes, refines, and inserts polished text into any app.

**BYOK (Bring Your Own Key)** — bring your own Groq or OpenAI API key. You pay only for what you use.

---

## Features

### Voice Dictation
Tap the mic button (or hold, depending on your setting) and speak. VoxPen sends your audio to Whisper for transcription, optionally refines the result with an LLM, and shows both versions in the candidate bar. Tap to insert.

### Translation Mode
Speak in one language, output in another.

1. Go to **Settings → Translation Mode** and enable the toggle
2. Select a target language (English / 中文 / 日本語)
3. Dictate as usual — VoxPen will translate instead of refine

**Quick switch from keyboard:** When translation is active, a 🔄 indicator row appears at the top of the candidate bar (e.g., `🔄 說中文 → English`). Tap it to cycle through target languages; tap × to turn translation off. The available targets are smart-filtered — if your STT language is Chinese, you won't see "translate to Chinese" as an option.

You can also toggle translation via long-press ⚙️ → **Translation: ON/OFF**.

### Voice Commands
Say a command word instead of dictating text — VoxPen executes the keyboard action directly without inserting anything or calling the LLM.

| Say | Action |
|-----|--------|
| 送出 / 傳送 / send / enter / submit / 送信 / 確定 | ↵ Enter (send message / submit form) |
| 刪除 / 退格 / delete / backspace / 削除 / バックスペース | ⌫ Delete last character |
| 換行 / new line / newline / 改行 | ↩ Insert newline |
| 空格 / space / スペース | ␣ Insert space |
| 復原 / undo / 元に戻す | ↶ Undo last action |
| 全選 / select all / 全て選択 | Select all text |
| 複製 / copy / コピー | Copy selected text |
| 貼上 / paste / 貼り付け | Paste from clipboard |
| 剪下 / cut / 切り取り | Cut selected text |
| 全部刪除 / clear all / clear / 全削除 | Clear all text in field |

**How it works**: Just tap the mic and speak the command word. Recognition is exact-match (case-insensitive, whitespace-trimmed) — a single word or phrase with no extra text.

### Auto Tone
When you tap into a text field, VoxPen automatically selects the most appropriate tone style based on the active app — without changing your saved preference.

| Tone | Apps |
|------|------|
| 💬 Casual | WhatsApp, Telegram, Messenger, LINE, Discord, KakaoTalk, Viber |
| 📧 Email | Gmail, Outlook, Proton Mail |
| 💼 Professional | Slack, Microsoft Teams |
| 📝 Note | Google Keep, Notion, Obsidian, Evernote |
| 📱 Social | Twitter/X, Instagram, Threads, TikTok, Facebook, Dcard |

**Custom rules:** Go to **Settings → Auto Tone → Custom App Rules** to pin a specific tone to any app by package name (overrides the built-in table).

**Manual override:** Tap the tone button (💬/📧/💼…) on the keyboard to switch tone for the current recording only. Auto Tone re-applies the next time you tap into a field.

**To disable:** Settings → Auto Tone → toggle off. VoxPen falls back to your saved Tone Style preference.

### Speak to Edit
Select text in any app, speak an edit instruction, and let the LLM rewrite the selection in place.

**Step-by-step:**
1. Select the text you want to edit in any app
2. Switch to VoxPen keyboard
3. Long-press ⚙️ → tap **✏️ Edit Mode**
4. The candidate bar shows: *"✏️ Edit: select text, then tap mic"*
5. Tap the mic and speak your instruction (e.g., "讓它更正式" / "make it more concise" / "translate to English")
6. VoxPen reads the selected text, sends it with your instruction to the LLM, and replaces the selection with the result

**To exit Edit Mode:** long-press ⚙️ → tap **✏️ Edit Mode: ON** (toggles it off).

> **Note:** Speak to Edit requires a configured LLM API key (Groq or OpenAI). The selected text must not be empty.

---

## Setup

1. Install the app
2. Go through the onboarding wizard to:
   - Add your Groq API key (free tier available at [console.groq.com](https://console.groq.com))
   - Enable VoxPen keyboard in system settings
   - Grant microphone permission
3. Switch to VoxPen Voice keyboard in any text field

---

## Keyboard Layout

```
┌──────────────────────────────────────┐
│  🔄 說中文 → English            [×] │  ← translation indicator (when active)
│  🔵 Original: [raw transcription]    │  ← tap to insert
│  ✨ Refined:  [polished text]         │  ← tap to insert
├──────────────────────────────────────┤
│  🌐  │  ⌫  │    🎤    │  ⏎  │  ⚙️  │
└──────────────────────────────────────┘
```

| Button | Tap | Long-press |
|--------|-----|------------|
| 🌐 | Switch to previous keyboard | Language picker |
| ⌫ | Backspace | — |
| 🎤 | Start / stop recording | — |
| ⏎ | Enter | — |
| 💬 (tone) | Tone picker | — |
| ⚙️ | Open Settings app | Quick settings (refinement / translation / edit mode toggles) |

---

## Settings

| Setting | Description |
|---------|-------------|
| Groq API Key | Required for voice transcription and LLM refinement |
| STT Model | Whisper model for transcription (default: `whisper-large-v3-turbo`) |
| LLM Provider | Groq / OpenAI / Anthropic / Custom |
| LLM Model | Model used for refinement and speak-to-edit |
| Refinement | Enable/disable LLM text cleanup |
| Tone Style | Casual / Professional / Email / Note / Social / Custom |
| Auto Tone | Auto-detect tone by app; custom per-app rules |
| Translation Mode | Translate output to a different language |
| Recording Mode | Tap-to-toggle (default) or Hold-to-record |
| Custom Vocabulary | Words to bias Whisper transcription and LLM output |

---

## Languages

| Language | Code | Notes |
|----------|------|-------|
| Auto-detect | — | Let Whisper detect language; best for code-switching |
| 中文（繁體）| zh | Traditional Chinese; prompt bias for 繁體 |
| English | en | |
| 日本語 | ja | |

---

## Free Tier Limits (daily, resets at midnight)

| Feature | Free | Pro |
|---------|------|-----|
| Voice inputs | 30 | Unlimited |
| LLM refinements | 10 | Unlimited |
| File transcriptions | 2 | Unlimited |

Upgrade to Pro via **Settings → Upgrade to Pro**.
