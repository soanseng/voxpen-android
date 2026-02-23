# VoxInk Privacy Policy

**Last updated:** 2026-02-24

## Overview

VoxInk (語墨) is an open-source AI voice keyboard that operates on a BYOK (Bring Your Own Key) model. We do not collect, store, or transmit any user data to our servers.

## Data Collection

**VoxInk does not collect any personal data.** Specifically:

- No analytics or telemetry
- No crash reporting to our servers
- No user accounts or registration
- No advertising or tracking

## Audio Data

When you use VoxInk for voice input:

1. Audio is recorded locally on your device
2. Audio is sent **directly** from your device to the API provider you configured (e.g., Groq, OpenAI)
3. Audio is **not** stored on your device after transcription completes
4. VoxInk has no access to the audio data sent to your API provider

Your API provider's privacy policy governs how they handle the audio data. We recommend reviewing:
- [Groq Privacy Policy](https://groq.com/privacy-policy/)
- [OpenAI Privacy Policy](https://openai.com/privacy/)

## API Keys

- API keys are stored locally on your device using Android's EncryptedSharedPreferences
- Keys are encrypted at rest using Android Keystore
- Keys are never transmitted to any server other than the API provider you configured
- Keys are never logged or displayed in full

## Transcription History

- Transcription results are stored locally on your device in a Room database
- You can delete individual transcriptions or all data at any time
- No transcription data leaves your device unless you explicitly share or export it

## LLM Refinement

When text refinement is enabled:

1. Transcribed text is sent **directly** to your configured LLM provider (e.g., Groq, OpenAI, Anthropic)
2. VoxInk does not see or store the refined text beyond your local device
3. Your LLM provider's privacy policy applies to this data

## Permissions

VoxInk requests the following Android permissions:

| Permission | Purpose |
|---|---|
| `RECORD_AUDIO` | Voice recording for speech-to-text |
| `INTERNET` | Sending audio to your API provider |

## Third-Party Services

VoxInk communicates only with the API providers you explicitly configure. We do not integrate any third-party SDKs for analytics, advertising, or tracking.

## Children's Privacy

VoxInk does not knowingly collect data from children under 13. Since VoxInk collects no data at all, this is inherently satisfied.

## Changes

We may update this privacy policy from time to time. Changes will be posted in the app's repository.

## Contact

For privacy questions, please open an issue on our GitHub repository.

---

# VoxInk 隱私權政策

**最後更新：** 2026-02-24

## 概述

VoxInk（語墨）是一款開源 AI 語音鍵盤，採用 BYOK（自帶金鑰）模式運作。我們不會收集、儲存或傳輸任何使用者資料到我們的伺服器。

## 資料收集

**VoxInk 不收集任何個人資料。**

- 無分析或遙測資料
- 無回報到我們伺服器的當機報告
- 無使用者帳號或註冊
- 無廣告或追蹤

## 音訊資料

使用 VoxInk 語音輸入時：

1. 音訊在您的裝置上本機錄製
2. 音訊**直接**從您的裝置傳送至您設定的 API 服務商（如 Groq、OpenAI）
3. 轉錄完成後音訊**不會**儲存在裝置上
4. VoxInk 無法存取傳送至 API 服務商的音訊資料

## API 金鑰

- API 金鑰使用 Android EncryptedSharedPreferences 本機儲存在您的裝置上
- 金鑰使用 Android Keystore 靜態加密
- 金鑰絕不會傳輸至您設定的 API 服務商以外的伺服器

## 聯絡方式

如有隱私權問題，請在我們的 GitHub 儲存庫提出 issue。
