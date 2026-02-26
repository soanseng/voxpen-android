package com.voxpen.app.data.model

sealed class SttLanguage(
    val code: String?,
    val prompt: String,
    val emoji: String,
) {
    data object Auto : SttLanguage(
        code = null,
        prompt = "繁體中文，可能夾雜英文。",
        emoji = "\uD83C\uDF10",
    )

    data object Chinese : SttLanguage(
        code = "zh",
        prompt = "繁體中文轉錄。",
        emoji = "\uD83C\uDDF9\uD83C\uDDFC",
    )

    data object English : SttLanguage(
        code = "en",
        prompt = "Transcribe the following English speech.",
        emoji = "\uD83C\uDDFA\uD83C\uDDF8",
    )

    data object Japanese : SttLanguage(
        code = "ja",
        prompt = "以下の日本語音声を文字起こししてください。",
        emoji = "\uD83C\uDDEF\uD83C\uDDF5",
    )

    data object Korean : SttLanguage(
        code = "ko",
        prompt = "한국어 음성을 전사합니다.",
        emoji = "\uD83C\uDDF0\uD83C\uDDF7",
    )

    data object French : SttLanguage(
        code = "fr",
        prompt = "Transcription de la parole française.",
        emoji = "\uD83C\uDDEB\uD83C\uDDF7",
    )

    data object German : SttLanguage(
        code = "de",
        prompt = "Transkription der deutschen Sprache.",
        emoji = "\uD83C\uDDE9\uD83C\uDDEA",
    )

    data object Spanish : SttLanguage(
        code = "es",
        prompt = "Transcripción del habla en español.",
        emoji = "\uD83C\uDDEA\uD83C\uDDF8",
    )

    data object Vietnamese : SttLanguage(
        code = "vi",
        prompt = "Phiên âm giọng nói tiếng Việt.",
        emoji = "\uD83C\uDDFB\uD83C\uDDF3",
    )

    data object Indonesian : SttLanguage(
        code = "id",
        prompt = "Transkripsi ucapan bahasa Indonesia.",
        emoji = "\uD83C\uDDEE\uD83C\uDDE9",
    )

    data object Thai : SttLanguage(
        code = "th",
        prompt = "ถอดเสียงภาษาไทย",
        emoji = "\uD83C\uDDF9\uD83C\uDDED",
    )
}
