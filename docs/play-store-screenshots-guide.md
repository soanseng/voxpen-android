# Play Store 截圖指南

本指南說明如何在 **Windows + Android Studio** 環境下為 Google Play Console 產生各尺寸截圖。

## 目錄

1. [Play Store 截圖規格](#1-play-store-截圖規格)
2. [建立模擬器](#2-建立模擬器)
3. [安裝 App 到模擬器](#3-安裝-app-到模擬器)
4. [截圖方法](#4-截圖方法)
5. [建議截取的畫面](#5-建議截取的畫面)
6. [美化截圖](#6-美化截圖)
7. [上傳到 Play Console](#7-上傳到-play-console)

---

## 1. Play Store 截圖規格

### 必要

| 裝置類型 | 數量 | 比例 | 建議解析度 |
|----------|------|------|-----------|
| **手機** | 2-8 張 | 16:9 或 9:16 | 1080 x 1920 (portrait) |

### 選填（但建議提供）

| 裝置類型 | 數量 | 建議解析度 |
|----------|------|-----------|
| 7 吋平板 | 最多 8 張 | 1200 x 1920 |
| 10 吋平板 | 最多 8 張 | 1600 x 2560 |

### 共通限制

- 格式：PNG 或 JPEG（PNG 較清晰）
- 每邊最小 320px，最大 3840px
- 不可有 alpha 透明度

---

## 2. 建立模擬器

在 Android Studio 中為每種尺寸建立一個模擬器。

### 2.1 手機模擬器

1. **Tools → Device Manager → Create Virtual Device**
2. 選擇 **Phone** 分類
3. 選 **Pixel 8**（1080 x 2400, 420dpi）— 這是目前主流尺寸
4. System Image 選 **API 35**（或最新穩定版）
5. 完成建立

### 2.2 7 吋平板模擬器（選填）

1. **Create Virtual Device → Tablet**
2. 選 **Nexus 7**（1200 x 1920, 323dpi）
3. System Image 同上

### 2.3 10 吋平板模擬器（選填）

1. **Create Virtual Device → Tablet**
2. 選 **Pixel Tablet**（1600 x 2560, 320dpi）
3. System Image 同上

> **提示：** 如果電腦效能有限，先只建手機模擬器即可。平板截圖是選填的。

---

## 3. 安裝 App 到模擬器

### 方法 A：從 Android Studio 直接 Run

1. 選擇目標模擬器
2. **Run → Run 'app'** 或按 Shift+F10
3. 等待安裝完成

### 方法 B：用 adb 安裝 APK

在 **Windows Terminal / PowerShell / CMD** 中執行：

```powershell
# 確認 adb 可用（Android Studio 內建）
# 預設路徑：C:\Users\<你的使用者名稱>\AppData\Local\Android\Sdk\platform-tools\adb.exe

# 如果 adb 不在 PATH 中，先加入：
$env:PATH += ";$env:LOCALAPPDATA\Android\Sdk\platform-tools"

# 確認模擬器已連接
adb devices

# 安裝 APK
adb install app\build\outputs\apk\debug\app-debug.apk
```

> **注意：** 所有 adb 命令都在 **Windows Terminal / PowerShell** 中執行，不是 Linux。
> Android Studio 底部也有內建 Terminal 可以直接用。

---

## 4. 截圖方法

### 方法 A：Android Studio 模擬器 UI（最簡單）

1. 啟動模擬器，操作 app 到想要的畫面
2. 點模擬器側欄的 **📷 相機按鈕**（Take Screenshot）
3. 截圖自動存到桌面（或 Android Studio 設定的路徑）

### 方法 B：adb 命令（適合批次作業）

在 Windows Terminal 中：

```powershell
# 建立存放截圖的資料夾
mkdir screenshots

# 截圖（存到模擬器內部）
adb shell screencap -p /sdcard/screenshot_home.png

# 拉回 Windows
adb pull /sdcard/screenshot_home.png screenshots\

# 刪除模擬器上的暫存
adb shell rm /sdcard/screenshot_home.png
```

#### 批次截圖腳本（PowerShell）

建立 `take-screenshot.ps1`：

```powershell
# 用法: .\take-screenshot.ps1 <名稱>
# 範例: .\take-screenshot.ps1 keyboard-recording

param(
    [Parameter(Mandatory=$true)]
    [string]$Name
)

$outputDir = "screenshots"
if (-not (Test-Path $outputDir)) { New-Item -ItemType Directory -Path $outputDir }

$timestamp = Get-Date -Format "yyyyMMdd"
$filename = "${timestamp}_${Name}.png"

adb shell screencap -p /sdcard/screenshot.png
adb pull /sdcard/screenshot.png "$outputDir\$filename"
adb shell rm /sdcard/screenshot.png

Write-Host "Saved: $outputDir\$filename"
```

使用方式：

```powershell
.\take-screenshot.ps1 keyboard-idle
.\take-screenshot.ps1 keyboard-recording
.\take-screenshot.ps1 result-comparison
.\take-screenshot.ps1 settings-main
```

### 方法 C：Android Studio 內建 Logcat 截圖

1. **View → Tool Windows → Logcat**
2. 左側工具欄有 📷 按鈕
3. 可選擇裝置和格式

---

## 5. 建議截取的畫面

以下是 VoxInk 建議的截圖清單，按優先度排列：

### 手機（必要，建議 5-6 張）

| # | 畫面 | 說明 | 操作步驟 |
|---|------|------|---------|
| 1 | **鍵盤 - 待機** | 展示鍵盤主介面 | 打開任意 app 的文字輸入框，切到 VoxInk 鍵盤 |
| 2 | **鍵盤 - 錄音中** | 麥克風動畫 + 錄音狀態 | 按住麥克風按鈕開始錄音 |
| 3 | **結果對比** | 原文 vs 潤飾文字 | 完成一段錄音，等結果出現在候選列 |
| 4 | **設定頁面** | 展示功能豐富度 | 進入 VoxInk 設定主頁 |
| 5 | **語言選擇** | 多語言支援 | 設定中展開語言選項 |
| 6 | **Onboarding** | 首次使用引導 | 清除 app 資料重新啟動，或截 onboarding 特定步驟 |

### 平板（選填）

截取相同畫面即可，平板主要展示 UI 在大螢幕上的適配。

---

## 6. 美化截圖

Play Store 的截圖可以是純螢幕截圖，但加上裝飾文字和手機框會更專業。

### 免費線上工具

| 工具 | 特點 | 連結 |
|------|------|------|
| **Figma** | 最靈活，可完全自訂 | figma.com |
| **Canva** | 有手機框模板，快速 | canva.com |
| **App Mockup** | 專門做 app 截圖 | app-mockup.com |

### Figma 流程（推薦）

1. 建立 1080 x 1920 的 Frame
2. 放入背景色（配合 app 主題色）
3. 加入手機框（搜尋 "phone mockup" 社群資源）
4. 放入截圖
5. 加上 1-2 行功能說明文字（中文大字）
6. 匯出 PNG

### 文字建議

| 截圖 | 標題文字 |
|------|---------|
| 鍵盤待機 | 說話就能打字 |
| 錄音中 | AI 即時語音辨識 |
| 結果對比 | 智慧文字潤飾 |
| 設定頁面 | 自訂 API・完全掌控 |
| 語言選擇 | 支援 11 種語言 |
| Onboarding | 簡單設定・立即使用 |

---

## 7. 上傳到 Play Console

1. 前往 **Play Console → Your app → Store presence → Main store listing**
2. 捲到 **Screenshots** 區塊
3. 點 **Phone** 標籤 → 拖放或上傳截圖
4. 如果有平板截圖，切到 **7-inch tablet** / **10-inch tablet** 標籤上傳
5. 調整截圖順序（第一張最重要，會出現在搜尋結果中）
6. **Save** → 檢查是否有錯誤提示

### 截圖順序建議

1. 鍵盤錄音中（最吸引眼球）
2. 結果對比（展示核心價值）
3. 設定頁面（展示功能豐富度）
4. 語言選擇
5. Onboarding

---

## 附錄：常見問題

### Q: 模擬器截圖有狀態列和導航列怎麼辦？

沒關係，Play Store 截圖可以包含系統 UI。如果要隱藏：
- 設定 → Display → Navigation bar → 改為手勢導航（更現代）
- 用美化工具時裁切掉狀態列

### Q: 截圖需要用真機嗎？

不需要。模擬器截圖的解析度和品質完全符合要求。真機截圖的好處是可以展示真實的通知列等細節，但對上架來說模擬器就夠了。

### Q: Feature Graphic 也要做嗎？

是的，Play Console 要求一張 **1024 x 500** 的 Feature Graphic。這不是截圖，而是一張宣傳橫幅。建議用 Figma/Canva 製作，放入 app icon + 名稱 + 一句話標語。
