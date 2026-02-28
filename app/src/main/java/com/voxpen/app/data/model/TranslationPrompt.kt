package com.voxpen.app.data.model

object TranslationPrompt {
    fun build(source: SttLanguage, target: SttLanguage): String =
        when (target) {
            SttLanguage.English -> toEnglish(source)
            SttLanguage.Chinese -> toChinese(source)
            SttLanguage.Japanese -> toJapanese(source)
            else -> toEnglish(source)
        }

    private fun toEnglish(source: SttLanguage): String =
        "You are a translator. Translate the following ${sourceName(source)} speech transcription " +
            "into natural written English. Remove filler words and self-corrections in the process. " +
            "Output only the translated English text, no explanations."

    private fun toChinese(source: SttLanguage): String =
        "你是翻譯助手。請將以下${sourceName(source)}口語逐字稿翻譯成自然流暢的繁體中文書面語。" +
            "同時移除口語贅字和停頓。只輸出翻譯後的繁體中文文字，不要加任何解釋。"

    private fun toJapanese(source: SttLanguage): String =
        "あなたは翻訳アシスタントです。以下の${sourceName(source)}の音声書き起こしを、" +
            "自然な日本語書き言葉に翻訳してください。フィラーや言い直しも除去してください。" +
            "翻訳後の日本語テキストのみ出力し、説明は不要です。"

    private fun sourceName(source: SttLanguage): String =
        when (source) {
            SttLanguage.Chinese -> "Traditional Chinese"
            SttLanguage.English -> "English"
            SttLanguage.Japanese -> "Japanese"
            SttLanguage.Korean -> "Korean"
            SttLanguage.French -> "French"
            SttLanguage.German -> "German"
            SttLanguage.Spanish -> "Spanish"
            SttLanguage.Vietnamese -> "Vietnamese"
            SttLanguage.Indonesian -> "Indonesian"
            SttLanguage.Thai -> "Thai"
            SttLanguage.Auto -> "spoken"
        }
}
