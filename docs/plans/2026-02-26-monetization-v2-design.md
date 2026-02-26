# Monetization v2: Remove Ads + LemonSqueezy Bundle License

**Date:** 2026-02-26
**Status:** Approved

## Summary

Replace ad-based monetization with stricter free tier limits and dual Pro activation (Google Play in-app purchase + LemonSqueezy bundle license key). Clean removal of all AdMob code.

## Decisions

| Decision | Choice |
|----------|--------|
| Ads | Clean removal — delete all files, dependencies, permissions |
| Pro activation | Dual: Google Play purchase OR LemonSqueezy bundle key |
| Free voice inputs/day | 15 (was 20) |
| Free refinements/day | 3 (was 5) |
| Free file transcription/day | 5 min total duration (was 2 files) |
| Reset timing | Midnight local time |
| License validation | Online + local cache, trust until online |
| License revocation | Only on explicit API failure (refund/deactivate) |
| Limit-reached UI | Upgrade prompt + countdown to midnight + "I have a key" link |
| License input location | Settings screen + link from limit-reached prompt |
| Architecture | ProStatusResolver merging BillingManager + LicenseManager |

## Architecture

### Component Overview

```
billing/
├── BillingManager.kt          # KEEP — Google Play billing (unchanged)
├── ProStatus.kt               # MODIFY — add ProSource enum
├── ProStatusResolver.kt       # NEW — merges Google Play + LemonSqueezy
├── UsageLimiter.kt            # MODIFY — new limits + duration tracking
├── DailyUsage.kt              # MODIFY — add transcription duration field
└── LicenseManager.kt          # NEW — LemonSqueezy validation + cache

data/remote/
└── LemonSqueezyApi.kt         # NEW — Retrofit interface

ads/                           # DELETE entirely
├── AdManager.kt               ✗
├── RewardedAdLoader.kt        ✗
├── InterstitialAdLoader.kt    ✗
└── BannerAdView.kt            ✗
```

### Data Flow — License Activation

```
User enters license key
  → LicenseManager.activateLicense(key)
    → LemonSqueezyApi.activateLicense(key, instanceName)
      → Success: cache key + validation timestamp in EncryptedPrefs
      → Failure: show error
    → Emit ProStatus.Pro(LICENSE_KEY) to ProStatusResolver
```

### Data Flow — Pro Status Resolution

```
ProStatusResolver:
  combine(billingManager.proStatus, licenseManager.proStatus)
    → Pro(GOOGLE_PLAY) if Google Play purchase active
    → Pro(LICENSE_KEY) if LemonSqueezy license valid
    → Free if neither
```

### Data Flow — Periodic Re-validation

```
App launch / app foreground
  → LicenseManager checks: is there a cached key?
    → Yes + online: re-validate silently via LemonSqueezyApi.validateLicense()
      → Valid: update cache timestamp
      → Invalid (revoked/refunded): revoke Pro, clear cache
      → Network error: keep Pro (trust until online)
    → Yes + offline: keep Pro
    → No cached key: stay Free
```

## ProStatus Model

```kotlin
sealed interface ProStatus {
    data object Free : ProStatus
    data class Pro(val source: ProSource) : ProStatus
    val isPro: Boolean get() = this is Pro
}

enum class ProSource {
    GOOGLE_PLAY,
    LICENSE_KEY
}
```

## ProStatusResolver

```kotlin
@Singleton
class ProStatusResolver @Inject constructor(
    private val billingManager: BillingManager,
    private val licenseManager: LicenseManager
) {
    val proStatus: StateFlow<ProStatus> = combine(
        billingManager.proStatus,
        licenseManager.proStatus
    ) { billing, license ->
        when {
            billing.isPro -> ProStatus.Pro(ProSource.GOOGLE_PLAY)
            license.isPro -> ProStatus.Pro(ProSource.LICENSE_KEY)
            else -> ProStatus.Free
        }
    }.stateIn(scope, SharingStarted.Eagerly, ProStatus.Free)
}
```

## LemonSqueezy API

```kotlin
interface LemonSqueezyApi {
    @POST("v1/licenses/validate")
    suspend fun validateLicense(@Body request: ValidateLicenseRequest): ValidateLicenseResponse

    @POST("v1/licenses/activate")
    suspend fun activateLicense(@Body request: ActivateLicenseRequest): ActivateLicenseResponse

    @POST("v1/licenses/deactivate")
    suspend fun deactivateLicense(@Body request: DeactivateLicenseRequest): DeactivateLicenseResponse
}

// Base URL: https://api.lemonsqueezy.com/
```

### Instance Name Strategy

`instance_name` = `"android-{androidId}"` — unique per device, allows bundle device limits.

### Cached Data (EncryptedSharedPreferences)

```
license_key: String
license_instance_id: String
license_valid: Boolean
license_validated_at: Long       // epoch millis
license_product_name: String     // e.g., "VoxInk Bundle"
```

## LicenseManager

| Method | Purpose |
|--------|---------|
| `activateLicense(key)` | First-time activation via API, cache result |
| `validateCachedLicense()` | Silent re-validation on app foreground |
| `deactivateLicense()` | User manually removes license |
| `proStatus: StateFlow<ProStatus>` | Emits current license state |

## Updated Free Tier Limits

```kotlin
FREE_VOICE_INPUT_LIMIT = 15              // was 20
FREE_REFINEMENT_LIMIT = 3                // was 5
FREE_FILE_TRANSCRIPTION_DURATION = 300   // 5 min in seconds (was 2 files)
```

### DailyUsage Changes

```kotlin
data class DailyUsage(
    val date: LocalDate,
    val voiceInputCount: Int,
    val refinementCount: Int,
    val fileTranscriptionSeconds: Int    // was fileTranscriptionCount
)
```

### Duration Tracking Flow

```
User picks audio file
  → Get file duration via MediaMetadataRetriever
  → Check: usageLimiter.canTranscribeFile(durationSeconds)
    → remaining = 300 - fileTranscriptionSeconds
    → if durationSeconds <= remaining: allow
    → else: show limit prompt with remaining time
  → After successful transcription:
    → usageLimiter.addFileTranscriptionDuration(durationSeconds)
```

## UI Changes

### Limit-Reached Prompt

```
┌─────────────────────────────────────┐
│  Daily limit reached                │
│                                     │
│  You've used 15/15 voice inputs     │
│  Resets in 6h 23m                   │
│                                     │
│  [Upgrade to Pro]                   │
│  [I have a license key]             │
└─────────────────────────────────────┘
```

### Settings Screen — Pro Status Section

```
├── Pro Status Section
│     ├── Current: Free / Pro (Google Play) / Pro (License)
│     ├── [Upgrade to Pro] → Google Play
│     ├── [Activate License Key] → text input dialog
│     ├── [Restore Purchase] → Google Play restore
│     └── [Deactivate License] → only if license active
```

### License Key Input Dialog

```
┌─────────────────────────────────────┐
│  Activate License Key               │
│                                     │
│  Enter your VoxInk Bundle key:      │
│  ┌─────────────────────────────┐    │
│  │ XXXXXXXX-XXXX-XXXX-XXXX... │    │
│  └─────────────────────────────┘    │
│                                     │
│  [Cancel]              [Activate]   │
└─────────────────────────────────────┘
```

## Files to Delete (Ad Removal)

| File | Action |
|------|--------|
| `ads/AdManager.kt` | Delete |
| `ads/RewardedAdLoader.kt` | Delete |
| `ads/InterstitialAdLoader.kt` | Delete |
| `ads/BannerAdView.kt` | Delete |
| `build.gradle` — AdMob dependency | Remove `com.google.android.gms:play-services-ads` |
| `AndroidManifest.xml` — AdMob metadata | Remove `com.google.android.gms.ads.APPLICATION_ID` |
| `AndroidManifest.xml` — AD_ID permission | Remove `com.google.android.gms.permission.AD_ID` |

## References to Clean Up

| Location | What to Remove |
|----------|---------------|
| `SettingsViewModel` | `watchRewardedAd()`, `rewardedAdLoader` injection, ad state |
| `SettingsUiState` | `isAdLoading`, ad-related fields |
| `SettingsScreen` | Banner ad, rewarded ad button, ad loading UI |
| `TranscriptionViewModel` | Rewarded ad logic, ad prompts |
| `TranscriptionScreen` | Ad-related UI |
| `di/AppModule` or `NetworkModule` | Ad-related Hilt providers |
| `strings.xml` (en + zh-TW) | Ad-related string resources |
| `UsageLimiter` | `bonusVoiceInputs`, `addBonusVoiceInputs()`, `REWARDED_AD_BONUS` |

## Injection Changes

Everywhere that currently injects `BillingManager` for `proStatus` will inject `ProStatusResolver` instead:

- `RecordingController`
- `TranscriptionViewModel`
- `SettingsViewModel`
- `UsageLimiter` (if it checks pro status)

`BillingManager` stays injected only where Google Play purchase/restore flows are needed.
