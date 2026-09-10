# Custom BYOK Manual Verification Checklist

Date: 2026-07-14

Related plan: `docs/plans/2026-07-14-byok-custom-provider-parity.md`

## Prerequisites
- Real Android device (emulator is unreliable for IME and audio)
- A working self-hosted / local OpenAI-compatible server for testing:
  - STT example: whisper.cpp server, faster-whisper, or any /v1/audio/transcriptions endpoint
  - LLM example: Ollama (`http://localhost:11434/v1/`), LM Studio, LiteLLM, vLLM, etc.
- Optional: A custom model name that is not in the default lists (e.g. `llama3.1:8b` or a Groq experimental model)

## Test Cases

### 1. STT Custom with no API key (keyless)
**Steps:**
1. Settings → Speech → Provider → Custom
2. Enter a valid custom STT base URL (e.g. `http://192.168.x.x:8000/`)
3. Leave the API key field empty / do not save any key for Custom
4. Go back to keyboard, start a recording

**Expected:**
- Recording succeeds
- Transcription returns text (no "API key not configured" error)
- Original text appears in candidate bar

**Verify in logs (Timber / Logcat):**
- No early "API key not configured" from RecordingController
- `SttRepository` did not throw on blank key for Custom
- The Authorization header sent was empty or minimal

**Pass / Fail:** ____

### 2. LLM Custom with no API key (keyless refinement)
**Steps:**
1. Settings → LLM Provider → Custom
2. Enter custom base URL (e.g. `http://192.168.x.x:11434/v1/`)
3. Enter a model name (e.g. `llama3.1:8b`)
4. Leave API key empty for this provider
5. Enable refinement
6. Record speech (Chinese or English)

**Expected:**
- Refinement runs without "API key not configured"
- Refined text appears (may be raw if the local model is weak, but no crash)

**Verify:**
- `LlmRepository.refine` was called with blank key for Custom
- Chat completion used empty auth header

**Pass / Fail:** ____

### 3. Speak-to-Edit with Custom LLM
**Steps:**
1. Use the same Custom LLM settings as above
2. In any app, select some text
3. Long-press ⚙️ on keyboard → turn on Edit Mode
4. Speak an edit instruction (e.g. "make it more formal" or "改得更正式")

**Expected:**
- The selected text is replaced with the LLM-edited version
- Uses the custom model (not the default llama one)

**Verify (logs):**
- `performEditWithLlm` resolved `customLlmModel`
- `EditTextUseCase` / `LlmRepository.editText` received the custom model

**Pass / Fail:** ____

### 4. Model override on built-in provider (e.g. Groq)
**Steps:**
1. Set LLM Provider to Groq (or OpenAI / OpenRouter)
2. In the model section, type a valid but non-listed model name in the override field (e.g. a specific Groq model ID)
3. Record with refinement on

**Expected:**
- The request to the LLM uses the typed model name (check provider dashboard or logs)
- No forced reset to the default curated model

**Pass / Fail:** ____

### 5. File Transcription with Custom STT + Custom LLM
**Steps:**
1. Configure both STT and LLM as Custom (keyless or with keys as needed)
2. Go to Transcription screen
3. Pick an audio file
4. Transcribe

**Expected:**
- Both transcription and optional refinement succeed using the custom endpoints
- No key errors if left blank for Custom

**Pass / Fail:** ____

### 6. Mixed scenarios (regression)
- Normal Groq / OpenAI flow still works with proper keys
- Switching providers does not lose previously entered custom URLs/models
- Error messages are reasonable when custom URL is wrong (not generic key error)

**Pass / Fail:** ____

## Notes for tester
- Clear logs first: `adb logcat -c`
- Capture full session to file: `adb logcat -v threadtime > custom-byok-test.log &`
- Filter relevant output (recommended):
  `adb logcat -v threadtime | grep -E "(VoxPen|SttRepository|LlmRepository|RecordingController|performEditWithLlm|custom|Authorization|API key not configured)"`
- Package-specific (more efficient):
  `adb logcat --pid=$(adb shell pidof com.voxpen.app) -v threadtime | grep -E "(stt|llm|custom|key)"`
- Errors only: `adb logcat *:E | grep -i voxpen`
- For local servers on the same machine as emulator, use `http://10.0.2.2:port/`
- On real device, use the machine's LAN IP (e.g. `http://192.168.1.100:11434/v1/`)
- After testing, you can leave the custom settings or reset them

**Useful one-liner for live monitoring during a test:**
```bash
adb logcat -c && adb logcat -v threadtime | grep --line-buffered -E "(SttRepository|LlmRepository|RecordingController|EditTextUseCase|customStt|customLlm|Bearer)" | tee custom-test.log
```

## Sign-off
- All 6 cases passed on device:  [ ] Yes  [ ] No (list issues)
- Tester: ________________   Date: ________________

---

This checklist fulfills the "Manual verification checklist" requirement in the plan.