# VoxInk Android — Windows 本機測試指南

## 1. 環境準備

**安裝 Android Studio**
- 下載：https://developer.android.com/studio
- 安裝時勾選 **Android SDK**、**Android SDK Platform-Tools**、**Android Emulator**
- 需要 **JDK 17**（Android Studio 內建，通常不需另外裝）

**確認 SDK 版本**
- 開啟 Android Studio → Settings → Languages & Frameworks → Android SDK
- 勾選安裝 **Android 15 (API 35)** SDK Platform（compileSdk = 35）
- 確保 **Android 8.0 (API 26)** 以上的系統映像也有安裝（minSdk = 26）

## 2. 開啟專案

1. 將專案複製到 Windows（git clone 或直接複製整個資料夾）
2. Android Studio → File → Open → 選擇 `voxink-android` 根目錄
3. 等待 Gradle sync 完成（首次可能需 5-10 分鐘下載依賴）

> **注意**：`local.properties` 裡的 `sdk.dir` 路徑需要改成 Windows 的路徑，例如：
> ```properties
> sdk.dir=C:\\Users\\你的使用者名稱\\AppData\\Local\\Android\\Sdk
> ```
> Android Studio 通常會自動偵測並覆寫這個檔案。

## 3. 建立模擬器（Emulator）

1. Android Studio → Tools → Device Manager → Create Virtual Device
2. 選擇一個手機型號（推薦 Pixel 7 或 Pixel 8）
3. 選擇系統映像 → API 34 或 35（推薦帶 Google Play Services 的版本）
   - 因為有用到 Google Play Billing 和 AdMob，建議選 **"Google APIs"** 映像
4. 完成建立後啟動模擬器

## 4. Build & 安裝

### 方式 A — Android Studio（推薦）

1. 上方工具列選擇 build variant 為 `debug`
2. 選擇目標裝置（模擬器或實體手機）
3. 點擊綠色三角形 ▶ Run 按鈕
4. 等待 build 完成，APK 會自動安裝到裝置

### 方式 B — 命令列

```bash
# 在專案根目錄
.\gradlew.bat assembleDebug

# APK 產出位置：
# app\build\outputs\apk\debug\app-debug.apk

# 安裝到已連接的裝置/模擬器
adb install app\build\outputs\apk\debug\app-debug.apk
```

## 5. 啟用 VoxInk 輸入法

安裝完成後，app 會自動開啟 MainActivity（首頁），但 **IME 需要手動啟用**：

1. 打開裝置的 Settings（設定）
2. System → Languages & input → On-screen keyboard → Manage on-screen keyboards
   （系統 → 語言與輸入 → 螢幕鍵盤 → 管理螢幕鍵盤）
3. 找到 **"VoxInk"** 並開啟
4. 確認安全性提示（啟用第三方輸入法）

## 6. 切換到 VoxInk 鍵盤

1. 開啟任意有文字輸入框的 app（例如 Chrome、訊息、備忘錄）
2. 點擊輸入框叫出鍵盤
3. 點擊導覽列的鍵盤圖示（🌐），或長按空白鍵
4. 選擇 **"VoxInk"**

## 7. 設定 API Key

VoxInk 是 BYOK 模式，需要設定 API key 才能使用語音功能：

1. 開啟 VoxInk app（點擊 app 圖示）
2. 進入 Settings 畫面
3. 輸入 Groq API Key（從 https://console.groq.com 取得）
4. 選擇語言偏好（Auto / 中文 / English / 日本語）

## 8. 測試語音輸入

1. 切換到 VoxInk 鍵盤
2. 點擊麥克風按鈕 🎤 開始錄音
3. 說話後放開 → 會顯示 Processing 狀態
4. 看到 Original（原始）和 Refined（潤飾）結果
5. 點擊要插入的版本

**模擬器麥克風注意事項**：
- 模擬器可以使用電腦的麥克風，但需確認 **主機的麥克風權限已授予 Android Emulator**
- Windows 設定 → 隱私與安全性 → 麥克風 → 確認已開啟
- 如果模擬器錄音有問題，建議用 **實體手機** 測試（USB 或 WiFi ADB）

## 9. 使用實體手機測試（推薦）

1. 手機開啟「開發人員選項」（設定 → 關於手機 → 連點版本號碼 7 次）
2. 開啟「USB 偵錯」（USB Debugging）
3. 用 USB 線連接電腦
4. 手機上確認允許 USB 偵錯
5. Android Studio 會自動偵測裝置，選擇它然後 Run

## 10. 執行測試

```bash
# 單元測試
.\gradlew.bat test

# Lint 檢查
.\gradlew.bat ktlintCheck
.\gradlew.bat detekt
```

## 常見問題

| 問題 | 解決方式 |
|------|----------|
| Gradle sync 失敗 | 確認網路暢通，File → Invalidate Caches → Restart |
| `sdk.dir` 路徑錯誤 | 刪除 `local.properties`，重新開啟專案讓 AS 自動產生 |
| 模擬器太慢 | 確認 BIOS 已啟用 Intel VT-x / AMD-V（硬體虛擬化） |
| 麥克風無聲 | 模擬器 → Extended Controls (⋯) → Microphone → 確認啟用 |
| 輸入法沒出現在列表 | 確認 APK 已安裝成功，重啟模擬器再試 |
| AdMob 初始化警告 | 正常，debug 版使用的是 Google 測試 ID |
