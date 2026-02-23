package com.voxink.app.data.model

sealed class SttLanguage(
    val code: String?,
    val prompt: String,
) {
    data object Auto : SttLanguage(
        code = null,
        prompt = "繁體中文，可能夾雜英文。",
    )

    data object Chinese : SttLanguage(
        code = "zh",
        prompt = "繁體中文轉錄。",
    )

    data object English : SttLanguage(
        code = "en",
        prompt = "Transcribe the following English speech.",
    )

    data object Japanese : SttLanguage(
        code = "ja",
        prompt = "以下の日本語音声を文字起こししてください。",
    )
}
