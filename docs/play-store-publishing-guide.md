# VoxInk Play Store 上架指南

## 目錄

1. [前置準備](#1-前置準備)
2. [產生簽名金鑰 (Keystore)](#2-產生簽名金鑰-keystore)
3. [設定 Release Build 簽名](#3-設定-release-build-簽名)
4. [建構 Release AAB/APK](#4-建構-release-aabapk)
5. [建立 Google Play 開發者帳號](#5-建立-google-play-開發者帳號)
6. [在 Play Console 建立 App](#6-在-play-console-建立-app)
7. [填寫 App 資訊](#7-填寫-app-資訊)
8. [上傳 AAB 並發佈](#8-上傳-aab-並發佈)
9. [不用 Windows / Android Studio 也能上架嗎？](#9-不用-windows--android-studio-也能上架嗎)
10. [GitHub Actions 自動建構可安裝 APK](#10-github-actions-自動建構可安裝-apk)
11. [上架前 Checklist](#11-上架前-checklist)

---

## 1. 前置準備

上架前確認以下項目：

- [ ] AdMob Ad Unit IDs 已切換為正式 ID（參考 `docs/ads-testing-guide.md`）
- [ ] 隱私政策 URL 已準備好（`docs/privacy-policy.md` 可以部署到 GitHub Pages）
- [ ] App Icon 已設定（`mipmap/ic_launcher`）
- [ ] `versionCode` 和 `versionName` 已更新
- [ ] ProGuard/R8 shrink 已啟用（已設定在 release buildType）
- [ ] 所有測試通過：`./gradlew test`
- [ ] 已在實機上完整測試過 release build

---

## 2. 產生簽名金鑰 (Keystore)

> **重要：** Keystore 遺失 = 無法更新 App。請備份到安全的地方（加密 USB、密碼管理器等）。

```bash
keytool -genkeypair \
  -v \
  -keystore voxink-release.keystore \
  -alias voxink \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass '你的store密碼' \
  -keypass '你的key密碼' \
  -dname "CN=VoxInk, OU=Development, O=VoxInk, L=Taipei, ST=Taiwan, C=TW"
```

建立 `keystore.properties`（已被 `.gitignore` 忽略）：

```properties
storeFile=../voxink-release.keystore
storePassword=你的store密碼
keyAlias=voxink
keyPassword=你的key密碼
```

---

## 3. 設定 Release Build 簽名

在 `app/build.gradle.kts` 中加入 signing config：

```kotlin
import java.io.FileInputStream
import java.util.Properties

val keystoreProperties = Properties()
val keystoreFile = rootProject.file("keystore.properties")
if (keystoreFile.exists()) keystoreProperties.load(FileInputStream(keystoreFile))

android {
    // ... 現有設定 ...

    signingConfigs {
        create("release") {
            storeFile = file(keystoreProperties["storeFile"] as? String ?: "")
            storePassword = keystoreProperties["storePassword"] as? String ?: ""
            keyAlias = keystoreProperties["keyAlias"] as? String ?: ""
            keyPassword = keystoreProperties["keyPassword"] as? String ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}
```

---

## 4. 建構 Release AAB/APK

Play Store 上架需要 **AAB (Android App Bundle)**，不是 APK：

```bash
# 建構 AAB（Play Store 需要）
./gradlew bundleRelease

# 產出：app/build/outputs/bundle/release/app-release.aab
```

如果需要 APK（側載或 GitHub Release 用）：

```bash
# 建構簽名 APK
./gradlew assembleRelease

# 產出：app/build/outputs/apk/release/app-release.apk
```

驗證簽名：

```bash
# 檢查 APK 簽名
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk

# 檢查 AAB（需要 bundletool）
jarsigner -verify app/build/outputs/bundle/release/app-release.aab
```

---

## 5. 建立 Google Play 開發者帳號

1. 前往 [Google Play Console](https://play.google.com/console)
2. 使用 Google 帳號登入
3. 支付 **一次性 $25 USD** 註冊費
4. 填寫開發者資訊（名稱、地址、電話）
5. 驗證身份（可能需要上傳證件）

> 帳號審核通常 2-7 天。

---

## 6. 在 Play Console 建立 App

1. Play Console → **Create app**
2. 填寫：
   - App name: `VoxInk (語墨)`
   - Default language: `Chinese (Traditional)` 或 `English`
   - App or game: **App**
   - Free or paid: **Free**（有廣告和 IAP）
3. 同意開發者政策

---

## 7. 填寫 App 資訊

Play Console 要求填寫多個區塊，逐一完成：

### 7.1 Store listing (商店頁面)

| 欄位 | 內容 |
|------|------|
| App name | VoxInk (語墨) |
| Short description | AI 語音鍵盤 — 說話就能打字，支援繁中/英/日 |
| Full description | （準備 200-4000 字的完整說明） |
| App icon | 512 x 512 PNG（圓角或圓形） |
| Feature graphic | 1024 x 500 PNG |
| Screenshots | 至少 2 張手機截圖（每種裝置類型），建議 4-8 張 |
| Video | （選填）YouTube 連結 |

截圖建議：
- 使用者按住麥克風錄音
- 原始 vs 精修文字對比
- 設定頁面
- 支援的語言展示

### 7.2 Content rating (內容分級)

1. 前往 **Policy → Content rating**
2. 填寫問卷（VoxInk 不含暴力、色情等內容）
3. 結果通常是 **PEGI 3 / Everyone**

### 7.3 Target audience (目標受眾)

- Target age group: **18+**（避免 COPPA 合規問題，除非你想處理兒童隱私）

### 7.4 Privacy policy

- URL: 部署 `docs/privacy-policy.md` 到可公開存取的網址
- 最簡單：啟用 GitHub Pages，URL 格式為 `https://你的username.github.io/voxink-android/privacy-policy`

### 7.5 App access (App 存取)

- 說明 App 需要哪些特殊存取權限
- VoxInk 需要：INTERNET、RECORD_AUDIO
- 如果需要 API key 才能用，提供測試帳號給審查人員

### 7.6 Ads declaration (廣告聲明)

- 選 **Yes, my app contains ads**

### 7.7 Data safety (資料安全)

填寫 app 收集和分享的資料類型：

| 資料類型 | 收集 | 分享 | 用途 |
|----------|------|------|------|
| Audio files | Yes (暫存) | No | 語音轉文字 |
| App interactions | Yes | No | 功能分析 |
| Device identifiers | Yes (AdMob) | Yes (with ad networks) | 廣告 |

---

## 8. 上傳 AAB 並發佈

### 8.1 選擇發佈 Track

| Track | 說明 | 建議 |
|-------|------|------|
| Internal testing | 最多 100 人，即時生效 | 第一次先用這個 |
| Closed testing | 需要 email 清單 | 小規模 Beta |
| Open testing | 任何人可加入 | 大規模 Beta |
| Production | 正式上架 | 確認沒問題後 |

**建議順序：** Internal testing → Closed testing → Production

### 8.2 上傳步驟

1. Play Console → **Release → Internal testing** → **Create new release**
2. **App signing**：首次會要求設定 Google Play App Signing
   - 推薦使用 **Google-managed key**（Google 幫你管理上傳金鑰）
   - 或上傳你自己的 signing key
3. 上傳 `app-release.aab`
4. 填寫 Release notes（更新說明）
5. **Review and roll out**

### 8.3 審核時間

- Internal testing：通常**幾小時內**
- Production：通常 **1-7 天**（首次可能更久）

---

## 9. 不用 Windows / Android Studio 也能上架嗎？

**完全可以。** 上架 Play Store 不需要 Windows 也不需要 Android Studio GUI。

### 你需要的工具

| 工具 | 用途 | 安裝方式 |
|------|------|----------|
| JDK 17 | 編譯 Kotlin/Java | `apt install openjdk-17-jdk` |
| Android SDK command-line tools | SDK 管理 | 下載 cmdline-tools |
| Gradle wrapper | 建構 | 已包含在專案中 (`./gradlew`) |
| `keytool` | 產生 keystore | JDK 內建 |
| `bundletool` | 測試 AAB（選用） | GitHub 下載 |
| 瀏覽器 | 操作 Play Console | 任何瀏覽器 |

### Linux 上完整流程

```bash
# 1. 確認 JDK
java -version  # 需要 17+

# 2. 確認 Android SDK（如果用 GitHub Actions 可以跳過本地）
echo $ANDROID_HOME

# 3. 產生 keystore
keytool -genkeypair -v -keystore voxink-release.keystore \
  -alias voxink -keyalg RSA -keysize 2048 -validity 10000

# 4. 設定 keystore.properties
cat > keystore.properties << 'EOF'
storeFile=../voxink-release.keystore
storePassword=你的密碼
keyAlias=voxink
keyPassword=你的密碼
EOF

# 5. 建構 AAB
./gradlew bundleRelease

# 6. 用瀏覽器上傳到 Play Console
# https://play.google.com/console
```

**結論：Mac、Linux、甚至 Chromebook 都可以完成上架。Android Studio 只是方便但不是必要的。**

---

## 10. GitHub Actions 自動建構可安裝 APK

### 目前狀態

你已經有兩個 workflow：
- `ci.yml`：push/PR 時建構 debug APK 並上傳為 artifact
- `release.yml`：push tag 時建構 debug APK 並附加到 GitHub Release

### 問題：目前只建構 debug APK

Debug APK 可以安裝使用，但：
- 沒有 ProGuard/R8 最佳化
- 使用 debug signing key（每台機器不同）
- 較大的 APK 體積

### 升級方案：建構 signed release APK

要在 GitHub Actions 上建構 signed release APK，需要把 keystore 和密碼存為 GitHub Secrets。

#### Step 1：將 keystore 轉為 base64

```bash
base64 -w 0 voxink-release.keystore > keystore.base64.txt
```

#### Step 2：設定 GitHub Secrets

前往 GitHub repo → Settings → Secrets and variables → Actions → New repository secret：

| Secret Name | 值 |
|-------------|------|
| `KEYSTORE_BASE64` | keystore.base64.txt 的內容 |
| `KEYSTORE_PASSWORD` | store 密碼 |
| `KEY_ALIAS` | `voxink` |
| `KEY_PASSWORD` | key 密碼 |

#### Step 3：更新 release.yml

```yaml
name: Release

on:
  push:
    tags:
      - 'v*'

jobs:
  build:
    runs-on: ubuntu-latest
    permissions:
      contents: write

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Run unit tests
        run: ./gradlew test

      - name: Decode keystore
        run: |
          echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > app/voxink-release.keystore

      - name: Create keystore.properties
        run: |
          cat > keystore.properties << EOF
          storeFile=voxink-release.keystore
          storePassword=${{ secrets.KEYSTORE_PASSWORD }}
          keyAlias=${{ secrets.KEY_ALIAS }}
          keyPassword=${{ secrets.KEY_PASSWORD }}
          EOF

      - name: Build release APK
        run: ./gradlew assembleRelease

      - name: Build release AAB
        run: ./gradlew bundleRelease

      - name: Rename artifacts
        run: |
          VERSION="${GITHUB_REF_NAME#v}"
          mv app/build/outputs/apk/release/app-release.apk \
             "app/build/outputs/apk/release/voxink-${VERSION}-release.apk"
          mv app/build/outputs/bundle/release/app-release.aab \
             "app/build/outputs/bundle/release/voxink-${VERSION}-release.aab"

      - name: Upload APK & AAB to release
        uses: softprops/action-gh-release@v2
        with:
          files: |
            app/build/outputs/apk/release/voxink-*-release.apk
            app/build/outputs/bundle/release/voxink-*-release.aab

      - name: Cleanup secrets
        if: always()
        run: |
          rm -f app/voxink-release.keystore keystore.properties
```

#### Step 4：觸發建構

```bash
# 更新 version
# 在 app/build.gradle.kts 中更新 versionCode 和 versionName

# 建立 tag 並 push
git tag v1.2.0
git push origin v1.2.0
```

#### 結果

GitHub Release 頁面會自動出現：
- `voxink-1.2.0-release.apk` — 簽名的 APK，任何人都可以下載安裝
- `voxink-1.2.0-release.aab` — 用於上傳到 Play Store

### 讓每次 CI 也能下載 APK

如果你也想在每次 PR/push 時都有可安裝的 APK：

`ci.yml` 已經有 `upload-artifact` 步驟，debug APK 可以從 GitHub Actions 的 Artifacts 區塊下載。這對日常測試已經足夠。

---

## 11. 上架前 Checklist

```markdown
### 帳號與法律
- [ ] Google Play Developer 帳號已建立 ($25)
- [ ] 隱私政策已部署到公開 URL
- [ ] AdMob 帳號已建立，App 已註冊

### 簽名與建構
- [ ] Release keystore 已產生並安全備份
- [ ] keystore.properties 已設定（本地）
- [ ] GitHub Secrets 已設定（CI/CD）
- [ ] `./gradlew assembleRelease` 成功
- [ ] `./gradlew bundleRelease` 成功
- [ ] APK 簽名已驗證

### 廣告
- [ ] AdMob Ad Unit IDs 已切換為正式 ID（release buildType）
- [ ] Debug buildType 保留測試 ID
- [ ] 測試裝置已註冊（避免誤點正式廣告）

### App 品質
- [ ] 所有測試通過：`./gradlew test`
- [ ] 實機完整測試（IME、錄音、轉錄、廣告）
- [ ] ProGuard 沒有把需要的 class 移除（檢查 mapping.txt）
- [ ] App Icon 已設定
- [ ] 版本號已更新

### Play Console
- [ ] Store listing 已填寫完整
- [ ] Screenshots 已上傳（至少 2 張）
- [ ] Content rating 問卷已完成
- [ ] Data safety 已填寫
- [ ] Ads declaration: Yes
- [ ] Target audience 已設定
- [ ] App access 說明已填寫

### 發佈
- [ ] 先發佈到 Internal testing track
- [ ] 在測試 track 驗證安裝和功能正常
- [ ] 推進到 Production track
- [ ] 等待 Google 審核（1-7 天）
```
