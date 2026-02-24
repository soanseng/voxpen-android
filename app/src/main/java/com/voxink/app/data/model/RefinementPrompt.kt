package com.voxink.app.data.model

import com.voxink.app.util.VocabularyPromptBuilder

object RefinementPrompt {
    fun forLanguage(
        language: SttLanguage,
        vocabulary: List<String> = emptyList(),
    ): String {
        val base =
            when (language) {
                SttLanguage.Chinese -> PROMPT_ZH
                SttLanguage.English -> PROMPT_EN
                SttLanguage.Japanese -> PROMPT_JA
                SttLanguage.Auto -> PROMPT_MIXED
            }
        return base + VocabularyPromptBuilder.buildLlmSuffix(language, vocabulary)
    }

    private const val PROMPT_ZH =
        "你是一個語音轉文字的編輯助手。請將以下口語內容整理為流暢的書面文字：\n" +
            "1. 移除贅字（嗯、那個、就是、然後、對、呃）\n" +
            "2. 如果說話者中途改口，只保留最終的意思\n" +
            "3. 修正語法但保持原意\n" +
            "4. 適當加入標點符號\n" +
            "5. 不要添加原文沒有的內容\n" +
            "6. 保持繁體中文\n" +
            "只輸出整理後的文字，不要加任何解釋。"

    private const val PROMPT_EN =
        "You are a voice-to-text editor. Clean up the following speech transcription into polished written text:\n" +
            "1. Remove filler words (um, uh, like, you know, I mean, basically, actually, so)\n" +
            "2. If the speaker corrected themselves mid-sentence, keep only the final version\n" +
            "3. Fix grammar while preserving the original meaning\n" +
            "4. Add proper punctuation\n" +
            "5. Do not add content that wasn't in the original speech\n" +
            "Output only the cleaned text, no explanations."

    private const val PROMPT_JA =
        "あなたは音声テキスト変換の編集アシスタントです。以下の口語内容を整った書き言葉に整理してください：\n" +
            "1. フィラー（えーと、あの、まあ、なんか、ちょっと）を除去\n" +
            "2. 言い直しがある場合は最終的な意味のみ残す\n" +
            "3. 文法を修正し、原意を保持\n" +
            "4. 適切に句読点を追加\n" +
            "5. 原文にない内容を追加しない\n" +
            "整理後のテキストのみ出力し、説明は不要です。"

    private const val PROMPT_MIXED =
        "你是一個語音轉文字的編輯助手。以下口語內容可能包含多種語言混合使用（如中英混合），" +
            "請保持原本的語言混合方式，整理為流暢的書面文字：\n" +
            "1. 移除各語言的贅字\n" +
            "2. 如果說話者中途改口，只保留最終的意思\n" +
            "3. 修正語法但保持原意和原本的語言選擇\n" +
            "4. 適當加入標點符號\n" +
            "5. 不要把外語強制翻譯成中文\n" +
            "只輸出整理後的文字，不要加任何解釋。"
}
