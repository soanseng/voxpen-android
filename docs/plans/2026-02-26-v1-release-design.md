# VoxPen v1 Release Design

Date: 2026-02-26

## v1 Feature Completion

### C — Custom STT Endpoint

- Add Custom provider support to `SttRepository`
- Reuse existing custom base URL setting from `ApiKeyManager`
- Use OpenAI-compatible `/v1/audio/transcriptions` multipart API format (same as Groq)
- Allows self-hosted Whisper servers

### D — File Transcription + LLM Refinement

- After transcribing each chunk in `TranscribeFileUseCase`, call `RefineTextUseCase`
- Store result in `TranscriptionEntity.refinedText` (field exists, currently always null)
- Respect user's refinement toggle setting
- Show refined text in transcription detail screen

### E — SRT Export UI + Real Timestamps

- Parse Whisper `verbose_json` response segments (currently `WhisperResponse` only captures `text`)
- Add `segments` field to `WhisperResponse` with start/end timestamps
- Store segment-level timestamps in `TranscriptionEntity`
- Add "Export SRT" button to transcription detail screen (save to Downloads or share)
- Update `ExportHelper.toSrt()` to use real timestamps instead of 5s estimates

### G — File Transcription Language Picker

- Add language selector to Transcription screen (currently hardcoded `SttLanguage.Auto`)
- Reuse existing language list UI component from Settings

## Release Preparation

- **Audit debug-only code**: scan for `BuildConfig.DEBUG`, test-only endpoints, hardcoded keys
- **Release keystore**: create and configure signing config in build.gradle
- **Play Console billing**: create `voxpen_pro` in-app product (manual, in Play Console)
- **Play Console listing**: in progress (screenshots, description, etc.)

## Post-v1 Backlog

| Feature | Notes |
|---|---|
| Anthropic Claude LLM | Add to `LlmProvider` enum |
| OpenAI Whisper STT | whisper-1, gpt-4o-transcribe as alternative STT |
| Translation mode | Speak language A, output language B |
| Compose UI tests | Onboarding, Settings, Transcription screens |
| UX polish | Animations, error handling, Settings reorganization |
