# VoxInk 廣告測試指南 (AdMob Testing Guide)

## 目錄

1. [目前的廣告架構](#1-目前的廣告架構)
2. [測試用 Ad Unit IDs](#2-測試用-ad-unit-ids)
3. [在裝置上測試廣告 (Step-by-Step)](#3-在裝置上測試廣告-step-by-step)
4. [從測試切換到正式廣告](#4-從測試切換到正式廣告)
5. [常見問題排除](#5-常見問題排除)

---

## 1. 目前的廣告架構

VoxInk 使用 Google AdMob SDK，目前實作了三種廣告：

| 類型 | 檔案 | 說明 |
|------|------|------|
| Banner | `ads/BannerAdView.kt` | 頁面底部的橫幅廣告 |
| Interstitial | `ads/InterstitialAdLoader.kt` | 全螢幕插頁廣告（3天寬限期，每天最多3次，間隔5分鐘） |
| Rewarded | `ads/RewardedAdLoader.kt` | 看廣告獲得獎勵（例如免費轉錄次數） |

AdMob 初始化在 `AdManager.kt`，Application ID 設定在 `AndroidManifest.xml`。

---

## 2. 測試用 Ad Unit IDs

**目前已設定 Google 官方測試 ID（不會產生真實費用）：**

| 類型 | Test Ad Unit ID |
|------|-----------------|
| App ID | `ca-app-pub-3940256099942544~3347511713` |
| Banner | `ca-app-pub-3940256099942544/6300978111` |
| Interstitial | `ca-app-pub-3940256099942544/1033173712` |
| Rewarded | `ca-app-pub-3940256099942544/5224354917` |

> 這些是 Google 官方提供的測試 ID，會顯示「Test Ad」標記的假廣告。**永遠不要用真實 Ad Unit ID 來點擊測試**，否則帳號會被停權。

---

## 3. 在裝置上測試廣告 (Step-by-Step)

### 前置條件

- 實體 Android 裝置或模擬器（API 26+）
- Google Play Services 已安裝（模擬器需用 Google APIs image）
- 網路連線

### Step 1：建構並安裝 debug APK

```bash
# 在專案目錄
./gradlew assembleDebug

# 安裝到連線的裝置
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

或用 Android Studio 直接 Run。

### Step 2：確認 AdMob 初始化

打開 app，檢查 Logcat 輸出：

```bash
adb logcat -s VoxInk:D | grep -i "AdMob"
```

應該看到：
```
D/VoxInk: AdMob initialized: ...
```

### Step 3：驗證 Banner 廣告

1. 打開 VoxInk 主畫面（HomeScreen 或 TranscriptionScreen）
2. 頁面底部應該出現一個標示 **"Test Ad"** 的橫幅
3. 如果沒出現，檢查 Logcat 有無錯誤訊息

### Step 4：測試 Interstitial 廣告

Interstitial 有保護機制（`canShow()` 在 `InterstitialAdLoader.kt`）：
- 安裝後 3 天內不會顯示
- 每天最多 3 次
- 間隔至少 5 分鐘

**測試時繞過限制的方法：**

```kotlin
// 在測試時暫時調整 InterstitialAdLoader 的 installTime
// 例如在 debug build 中：
interstitialAdLoader.setInstallTime(0L) // 假裝安裝時間很久以前
```

或者暫時修改 companion object 常數：

```kotlin
// 僅供測試，正式版要改回來
const val MIN_INTERVAL_MS = 10 * 1000L      // 10 秒
const val MAX_DAILY_SHOWS = 100
const val GRACE_PERIOD_MS = 0L               // 無寬限期
```

### Step 5：測試 Rewarded 廣告

1. 觸發顯示 Rewarded 廣告的功能（例如：免費用戶使用 Pro 功能時）
2. 應看到全螢幕影片廣告（測試廣告會是假影片）
3. 觀看完畢後，Logcat 應顯示：
   ```
   D/VoxInk: User earned reward: 1 reward_item
   ```

### Step 6：測試 Ad Inspector（推薦）

Google 提供的內建偵錯工具：

```kotlin
// 在 AdManager.initialize() 之後加入（僅 debug build）：
MobileAds.openAdInspector(context) { error ->
    if (error != null) Timber.e("Ad Inspector error: ${error.message}")
}
```

Ad Inspector 會在 app 內顯示一個疊加層，讓你檢查：
- 每個 Ad Unit 的載入狀態
- 請求/回應的詳細資訊
- Mediation chain 資訊

### Step 7：新增測試裝置（用正式 Ad Unit ID 時必做）

當你切換到正式 Ad Unit ID 後，必須註冊測試裝置：

```kotlin
// 在 AdManager.initialize() 之前
val testDeviceIds = listOf("你的裝置ID") // 從 Logcat 取得
val configuration = RequestConfiguration.Builder()
    .setTestDeviceIds(testDeviceIds)
    .build()
MobileAds.setRequestConfiguration(configuration)
```

取得裝置 ID：
```bash
adb logcat | grep "Use RequestConfiguration"
# 輸出: Use RequestConfiguration.Builder.setTestDeviceIds(Arrays.asList("ABCDEF123456"))
```

---

## 4. 從測試切換到正式廣告

### Step 1：建立 AdMob 帳號並設定 App

1. 前往 [AdMob Console](https://admob.google.com)
2. 新增 App → Android → 輸入 package name `com.voxink.app`
3. 記下你的 **App ID**（格式：`ca-app-pub-XXXXXXXX~YYYYYYYYYY`）
4. 建立三個 Ad Units（Banner / Interstitial / Rewarded），記下各自的 ID

### Step 2：更新 Ad Unit IDs

**方法 A：使用 BuildConfig（推薦）**

在 `app/build.gradle.kts` 中用 buildConfigField 區分 debug/release：

```kotlin
buildTypes {
    debug {
        buildConfigField("String", "ADMOB_APP_ID", "\"ca-app-pub-3940256099942544~3347511713\"")
        buildConfigField("String", "BANNER_AD_ID", "\"ca-app-pub-3940256099942544/6300978111\"")
        buildConfigField("String", "INTERSTITIAL_AD_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
        buildConfigField("String", "REWARDED_AD_ID", "\"ca-app-pub-3940256099942544/5224354917\"")
    }
    release {
        buildConfigField("String", "ADMOB_APP_ID", "\"ca-app-pub-你的正式APP_ID\"")
        buildConfigField("String", "BANNER_AD_ID", "\"ca-app-pub-你的正式BANNER_ID\"")
        buildConfigField("String", "INTERSTITIAL_AD_ID", "\"ca-app-pub-你的正式INTERSTITIAL_ID\"")
        buildConfigField("String", "REWARDED_AD_ID", "\"ca-app-pub-你的正式REWARDED_ID\"")
    }
}
```

然後更新 `AdManager.kt`：

```kotlin
companion object {
    const val BANNER_AD_UNIT_ID = BuildConfig.BANNER_AD_ID
    const val INTERSTITIAL_AD_UNIT_ID = BuildConfig.INTERSTITIAL_AD_ID
    const val REWARDED_AD_UNIT_ID = BuildConfig.REWARDED_AD_ID
}
```

以及 `AndroidManifest.xml`：

```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="${ADMOB_APP_ID}" />
```

並在 `build.gradle.kts` 加入 manifestPlaceholders：

```kotlin
debug {
    manifestPlaceholders["ADMOB_APP_ID"] = "ca-app-pub-3940256099942544~3347511713"
}
release {
    manifestPlaceholders["ADMOB_APP_ID"] = "ca-app-pub-你的正式APP_ID"
}
```

### Step 3：驗證

1. Build debug → 看到 "Test Ad" 標記 → 正確
2. Build release → 看到真實廣告（需註冊測試裝置避免誤點）→ 正確

---

## 5. 常見問題排除

| 問題 | 原因 | 解決方案 |
|------|------|----------|
| 廣告沒有顯示 | 模擬器沒有 Google Play Services | 使用 Google APIs 系統映像 |
| `Error code 0: Internal error` | 第一次請求通常需要等待 | 等待幾秒後重試，或重啟 app |
| `Error code 3: No fill` | 測試 ID 偶爾無填充 | 確認使用官方測試 ID，重試即可 |
| Banner 空白 | 網路問題或 AdView 沒有足夠空間 | 檢查網路連線和佈局 |
| 真實廣告下帳號被停權 | 用正式 ID 自己點廣告 | 一定要用測試 ID 或註冊測試裝置 |
| Interstitial 不顯示 | 被 canShow() 限制 | 測試時調整常數（見 Step 4） |
