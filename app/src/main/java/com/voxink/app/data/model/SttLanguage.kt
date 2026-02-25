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

    data object Korean : SttLanguage(
        code = "ko",
        prompt = "한국어 음성을 전사합니다.",
    )

    data object French : SttLanguage(
        code = "fr",
        prompt = "Transcription de la parole française.",
    )

    data object German : SttLanguage(
        code = "de",
        prompt = "Transkription der deutschen Sprache.",
    )

    data object Spanish : SttLanguage(
        code = "es",
        prompt = "Transcripción del habla en español.",
    )

    data object Vietnamese : SttLanguage(
        code = "vi",
        prompt = "Phiên âm giọng nói tiếng Việt.",
    )

    data object Indonesian : SttLanguage(
        code = "id",
        prompt = "Transkripsi ucapan bahasa Indonesia.",
    )

    data object Thai : SttLanguage(
        code = "th",
        prompt = "ถอดเสียงภาษาไทย",
    )
}
