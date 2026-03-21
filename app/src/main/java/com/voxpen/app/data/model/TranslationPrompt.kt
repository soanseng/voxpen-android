package com.voxpen.app.data.model

object TranslationPrompt {
    fun build(source: SttLanguage, target: SttLanguage): String =
        when (target) {
            SttLanguage.English -> toEnglish()
            SttLanguage.Chinese -> toChinese()
            SttLanguage.Japanese -> toJapanese()
            else -> toEnglish()
        }

    private fun toEnglish(): String =
        "You are a translator. Translate the following speech transcription into natural written English.\n" +
            "The input may contain multiple languages mixed together (e.g., Chinese with English words, " +
            "or other language combinations). Translate ALL content into English — " +
            "including parts that are already in English (clean them up for written style).\n" +
            "Also remove filler words and self-corrections in the process.\n" +
            "Output only the translated English text, no explanations."

    private fun toChinese(): String =
        "你是翻譯助手。請將以下口語逐字稿翻譯成自然流暢的繁體中文書面語。\n" +
            "輸入可能包含多種語言混合使用（例如中英混合、或其他語言組合），" +
            "請將所有內容統一翻譯成繁體中文——包括已經是中文的部分（整理為書面語）。\n" +
            "同時移除口語贅字和停頓。一律使用全形標點（，。、；：「」（）！？——……）。\n" +
            "只輸出翻譯後的繁體中文文字，不要加任何解釋。"

    private fun toJapanese(): String =
        "あなたは翻訳アシスタントです。以下の音声書き起こしを自然な日本語書き言葉に翻訳してください。\n" +
            "入力には複数の言語が混在している場合があります（例：中国語と英語の混合など）。" +
            "すべての内容を日本語に翻訳してください——既に日本語の部分も書き言葉に整えてください。\n" +
            "フィラーや言い直しも除去してください。翻訳後の日本語テキストのみ出力し、説明は不要です。"

}
