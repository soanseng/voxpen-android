# Free Tier Usage Limits Update

**Date**: 2026-02-27
**Status**: Approved

## Summary

Adjust free tier daily usage limits and add promotional copy in Settings.

## Changes

### New Limits

| Feature | Old | New |
|---------|-----|-----|
| Voice input | 15/day | **30/day** |
| AI refinement | 3/day | **10/day** |
| File transcription | 300 sec/day | **2 times/day** (count-based) |

### File Transcription: Seconds → Count

The file transcription limit changes from duration-based (seconds) to count-based (times per day). This simplifies the model — users get 2 transcriptions per day regardless of file length.

**API changes in UsageLimiter:**
- `canTranscribeFile(durationSeconds)` → `canTranscribeFile()` (no params)
- `addFileTranscriptionDuration(seconds)` → `incrementFileTranscription()`
- `remainingFileTranscriptionSeconds()` → `remainingFileTranscriptions()`

**DailyUsage data class:**
- `fileTranscriptionSeconds: Int` → `fileTranscriptionCount: Int`

### Settings UI Copy

Inside the Pro Status section (for free users), add:

> Free plan — 每日 30 次語音、10 次潤稿、2 次轉稿。升級 Pro 解鎖無限使用。

English:
> Free plan — 30 voice inputs, 10 refinements, 2 transcriptions daily. Upgrade to Pro for unlimited use.

### Affected Files

| File | Change |
|------|--------|
| `billing/UsageLimiter.kt` | Constants + API rename |
| `billing/DailyUsage.kt` | Field rename |
| `ui/transcription/TranscriptionViewModel.kt` | Use new API |
| `ui/settings/SettingsScreen.kt` | Add copy |
| `ui/settings/SettingsViewModel.kt` | Remaining count calc |
| `ui/settings/SettingsUiState.kt` | Field rename |
| `ui/HomeScreen.kt` | UsageSummaryCard update |
| `res/values/strings.xml` | Add/update strings |
| `res/values-zh-rTW/strings.xml` | Chinese translations |
| `test/.../UsageLimiterTest.kt` | Test updates |
