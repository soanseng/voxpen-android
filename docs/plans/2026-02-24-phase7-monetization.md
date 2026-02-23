# Phase 7: Monetization — Free + Pro + Ads Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add Free/Pro tier system with Google Play Billing, AdMob ads, and daily usage limits.

**Architecture:** UsageLimiter (DataStore-based daily counters) gates Free tier features. BillingManager wraps Google Play Billing for one-time Pro purchase. AdManager handles AdMob initialization and UMP consent. Ads appear only in Activity screens (never in IME). ProStatus flows through ViewModels to conditionally show/hide ads and enforce limits.

**Tech Stack:** Google Play Billing Library 7.x, AdMob (play-services-ads 23.x), UMP 3.x, DataStore, Hilt DI

---

## Batch 7A: Dependencies + Core Data Models

### Task 1: Add Phase 7 Dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

Add billing, ads, and UMP libraries.

### Task 2: Create ProStatus Model

**Files:**
- Create: `app/src/main/java/com/voxink/app/billing/ProStatus.kt`
- Create: `app/src/test/java/com/voxink/app/billing/ProStatusTest.kt`

### Task 3: Create DailyUsage Model

**Files:**
- Create: `app/src/main/java/com/voxink/app/billing/DailyUsage.kt`
- Create: `app/src/test/java/com/voxink/app/billing/DailyUsageTest.kt`

### Task 4: Update AndroidManifest

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

Add AdMob APPLICATION_ID meta-data (test ID for now).

### Task 5: Add Strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`

---

## Batch 7B: UsageLimiter

### Task 6: Create UsageLimiter

**Files:**
- Create: `app/src/main/java/com/voxink/app/billing/UsageLimiter.kt`
- Create: `app/src/test/java/com/voxink/app/billing/UsageLimiterTest.kt`

DataStore-based daily counter with midnight reset.

### Task 7: Wire UsageLimiter into DI

**Files:**
- Modify: `app/src/main/java/com/voxink/app/di/AppModule.kt`

---

## Batch 7C: BillingManager

### Task 8: Create BillingManager

**Files:**
- Create: `app/src/main/java/com/voxink/app/billing/BillingManager.kt`
- Create: `app/src/test/java/com/voxink/app/billing/BillingManagerTest.kt`

### Task 9: Wire BillingManager into DI

**Files:**
- Modify: `app/src/main/java/com/voxink/app/di/AppModule.kt`

---

## Batch 7D: Ad Components

### Task 10: Create AdManager

**Files:**
- Create: `app/src/main/java/com/voxink/app/ads/AdManager.kt`
- Create: `app/src/test/java/com/voxink/app/ads/AdManagerTest.kt`

### Task 11: Create BannerAdView Composable

**Files:**
- Create: `app/src/main/java/com/voxink/app/ads/BannerAdView.kt`

### Task 12: Create InterstitialAdLoader

**Files:**
- Create: `app/src/main/java/com/voxink/app/ads/InterstitialAdLoader.kt`
- Create: `app/src/test/java/com/voxink/app/ads/InterstitialAdLoaderTest.kt`

### Task 13: Create RewardedAdLoader

**Files:**
- Create: `app/src/main/java/com/voxink/app/ads/RewardedAdLoader.kt`
- Create: `app/src/test/java/com/voxink/app/ads/RewardedAdLoaderTest.kt`

---

## Batch 7E: IME Usage Limit Integration

### Task 14: Update IME EntryPoint and RecordingController

**Files:**
- Modify: `app/src/main/java/com/voxink/app/ime/VoxInkIMEEntryPoint.kt`
- Modify: `app/src/main/java/com/voxink/app/ime/RecordingController.kt`
- Modify: `app/src/main/java/com/voxink/app/ime/VoxInkIME.kt`
- Modify: `app/src/test/java/com/voxink/app/ime/RecordingControllerTest.kt`

---

## Batch 7F: UI Integration — Settings + Home

### Task 15: Update SettingsViewModel with ProStatus

**Files:**
- Modify: `app/src/main/java/com/voxink/app/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/voxink/app/ui/settings/SettingsUiState.kt`
- Modify: `app/src/main/java/com/voxink/app/ui/settings/SettingsScreen.kt`
- Modify: `app/src/test/java/com/voxink/app/ui/settings/SettingsViewModelTest.kt`

### Task 16: Update HomeScreen with Usage Summary

**Files:**
- Modify: `app/src/main/java/com/voxink/app/ui/HomeScreen.kt`

---

## Batch 7G: UI Integration — Transcription

### Task 17: Update TranscriptionViewModel with Limits

**Files:**
- Modify: `app/src/main/java/com/voxink/app/ui/transcription/TranscriptionViewModel.kt`
- Modify: `app/src/main/java/com/voxink/app/ui/transcription/TranscriptionUiState.kt`
- Modify: `app/src/main/java/com/voxink/app/ui/transcription/TranscriptionScreen.kt`
- Modify: `app/src/test/java/com/voxink/app/ui/transcription/TranscriptionViewModelTest.kt`

---

## Batch 7H: Export + Feature Gating

### Task 18: Gate Features by ProStatus

**Files:**
- Modify: `app/src/main/java/com/voxink/app/util/ExportHelper.kt`
- Modify: `app/src/main/java/com/voxink/app/ui/transcription/TranscriptionScreen.kt`
- Modify: `app/src/main/java/com/voxink/app/ui/settings/SettingsScreen.kt`

### Task 19: ProGuard Rules for New Dependencies

**Files:**
- Modify: `app/proguard-rules.pro`

### Task 20: Commit

Commit all Phase 7 changes.
