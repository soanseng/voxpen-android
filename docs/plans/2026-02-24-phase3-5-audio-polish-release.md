# Phase 3-5 Implementation Plan: Audio Transcription, UI Polish, Release

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Complete VoxInk from v0.2.0 to v1.0.0 — add file transcription, polish the UI with onboarding & i18n, and prepare for Play Store release.

**Architecture:** Phase 3 adds Room for transcript persistence, AudioChunker for large files, and a Compose transcription screen. Phase 4 adds onboarding flow, complete zh-TW/en i18n, and theme polish. Phase 5 finalizes release configuration.

**Tech Stack (additions):** Room 2.6.1, Navigation Compose 2.8.6, Compose Material Icons Extended

---

## Phase 3: Audio File Transcription

### Batch 3A: Dependencies & Database

#### Task 1: Add Room + Navigation Dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

Add Room 2.6.1, Navigation Compose 2.8.6, material-icons-extended.

#### Task 2: TDD — TranscriptionEntity + DAO

**Files:**
- Test: `app/src/test/java/com/voxink/app/data/local/TranscriptionEntityTest.kt`
- Create: `app/src/main/java/com/voxink/app/data/local/TranscriptionEntity.kt`
- Create: `app/src/main/java/com/voxink/app/data/local/TranscriptionDao.kt`
- Create: `app/src/main/java/com/voxink/app/data/local/AppDatabase.kt`
- Modify: `app/src/main/java/com/voxink/app/di/AppModule.kt`

Room entity with fields: id, fileName, filePath, duration, language, originalText, refinedText, createdAt, format (WAV/MP3/M4A/OGG/MP4/WebM).

#### Task 3: TDD — AudioChunker

**Files:**
- Test: `app/src/test/java/com/voxink/app/util/AudioChunkerTest.kt`
- Create: `app/src/main/java/com/voxink/app/util/AudioChunker.kt`

Split WAV byte arrays into fixed-duration chunks (5 minutes each, ~9.6MB at 16kHz/16bit/mono). Handle non-WAV by reading raw bytes.

### Batch 3B: Domain Layer

#### Task 4: TDD — TranscriptionRepository

**Files:**
- Test: `app/src/test/java/com/voxink/app/data/repository/TranscriptionRepositoryTest.kt`
- Create: `app/src/main/java/com/voxink/app/data/repository/TranscriptionRepository.kt`

Wraps TranscriptionDao: insert, getAll (Flow), getById, delete, update.

#### Task 5: TDD — TranscribeFileUseCase

**Files:**
- Test: `app/src/test/java/com/voxink/app/domain/usecase/TranscribeFileUseCaseTest.kt`
- Create: `app/src/main/java/com/voxink/app/domain/usecase/TranscribeFileUseCase.kt`

Orchestrate: read file bytes → chunk if > 25MB → transcribe each chunk → merge results → optionally refine → save to Room.

#### Task 6: TDD — ExportHelper

**Files:**
- Test: `app/src/test/java/com/voxink/app/util/ExportHelperTest.kt`
- Create: `app/src/main/java/com/voxink/app/util/ExportHelper.kt`

Format transcript for export: plain text, SRT subtitle format.

### Batch 3C: UI

#### Task 7: TranscriptionViewModel

**Files:**
- Test: `app/src/test/java/com/voxink/app/ui/transcription/TranscriptionViewModelTest.kt`
- Create: `app/src/main/java/com/voxink/app/ui/transcription/TranscriptionUiState.kt`
- Create: `app/src/main/java/com/voxink/app/ui/transcription/TranscriptionViewModel.kt`

States: Idle, Picking, Transcribing(progress), Done(result), Error. Actions: pickFile, transcribe, refine, export, delete.

#### Task 8: TranscriptionScreen

**Files:**
- Create: `app/src/main/java/com/voxink/app/ui/transcription/TranscriptionScreen.kt`

Compose screen: file picker button, transcription list from Room, detail view with original/refined, export buttons.

#### Task 9: Navigation Integration

**Files:**
- Modify: `app/src/main/java/com/voxink/app/ui/MainActivity.kt`
- Modify: `app/src/main/java/com/voxink/app/ui/HomeScreen.kt`

Add Navigation Compose with routes: home, settings, transcription. Add "Transcribe Audio" button to HomeScreen.

#### Task 10: String Resources for Phase 3

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`

All transcription-related strings in both en and zh-TW.

---

## Phase 4: UI Polish & Typeless Parity

### Batch 4A: Onboarding

#### Task 11: TDD — OnboardingViewModel

**Files:**
- Test: `app/src/test/java/com/voxink/app/ui/onboarding/OnboardingViewModelTest.kt`
- Create: `app/src/main/java/com/voxink/app/ui/onboarding/OnboardingUiState.kt`
- Create: `app/src/main/java/com/voxink/app/ui/onboarding/OnboardingViewModel.kt`

Steps: Welcome, ChooseProvider, EnterApiKey, EnableKeyboard, TestRecording, Done. Track completion in DataStore.

#### Task 12: OnboardingScreen

**Files:**
- Create: `app/src/main/java/com/voxink/app/ui/onboarding/OnboardingScreen.kt`

Step-by-step wizard with progress indicator. Each step is a composable page.

### Batch 4B: Theme & Visual Polish

#### Task 13: Material 3 Dynamic Theme

**Files:**
- Modify: `app/src/main/java/com/voxink/app/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/voxink/app/ui/theme/Color.kt`
- Modify: `app/src/main/res/values/themes.xml`

Add dark/light color schemes, dynamic color support (Android 12+), proper seed colors.

#### Task 14: i18n — Complete All Strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`

All Phase 4 strings: onboarding, theme settings, advanced features.

### Batch 4C: Navigation & Keyboard Polish

#### Task 15: Onboarding Navigation Integration

**Files:**
- Modify: `app/src/main/java/com/voxink/app/ui/MainActivity.kt`
- Modify: `app/src/main/java/com/voxink/app/data/local/PreferencesManager.kt`

Check if onboarding completed; if not, show onboarding first. Store `onboarding_completed` in DataStore.

#### Task 16: Quick Settings From Keyboard

**Files:**
- Modify: `app/src/main/res/layout/keyboard_view.xml`
- Modify: `app/src/main/java/com/voxink/app/ime/VoxInkIME.kt`

Gear button long-press → popup with: language quick-switch, refinement toggle, recording mode toggle.

---

## Phase 5: Beta Testing & Play Store

### Batch 5A: Release Configuration

#### Task 17: ProGuard Rules

**Files:**
- Modify: `app/proguard-rules.pro`

Rules for Retrofit, kotlinx-serialization, Room, OkHttp, Hilt.

#### Task 18: Release Build Config

**Files:**
- Modify: `app/build.gradle.kts`

Version bump to 1.0.0, release signing config placeholder, shrinkResources.

### Batch 5B: Store Assets

#### Task 19: Privacy Policy

**Files:**
- Create: `docs/privacy-policy.md`

BYOK model privacy policy: no data collection, audio sent directly to user-selected API.

#### Task 20: Store Listing Content

**Files:**
- Create: `docs/store-listing.md`

Title, short description, full description in en + zh-TW.

---

## Execution Order

1. **Batch 3A** (Tasks 1-3): Dependencies, database, chunker → commit
2. **Batch 3B** (Tasks 4-6): Repositories, use cases, export → commit
3. **Batch 3C** (Tasks 7-10): UI, navigation, strings → commit
4. **Batch 4A** (Tasks 11-12): Onboarding → commit
5. **Batch 4B** (Tasks 13-14): Theme, i18n → commit
6. **Batch 4C** (Tasks 15-16): Navigation integration, keyboard polish → commit
7. **Batch 5A** (Tasks 17-18): ProGuard, release config → commit
8. **Batch 5B** (Tasks 19-20): Privacy policy, store listing → commit
