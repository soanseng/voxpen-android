package com.voxpen.app.data.model

object EditPrompt {
    /**
     * Builds a complete prompt string for the LLM, embedding [selectedText] and [instruction].
     * The LLM receives this as the user message (no system/user split needed).
     */
    fun build(selectedText: String, instruction: String, language: SttLanguage): String =
        when (language) {
            SttLanguage.Chinese, SttLanguage.Auto ->
                "你是文字編輯助手。根據以下選取的文字和編輯指令，只輸出修改後的繁體中文文字。不要加任何說明、解釋或引號。\n\n" +
                    "選取的文字：\n$selectedText\n\n" +
                    "編輯指令：\n$instruction"

            SttLanguage.Japanese ->
                "あなたは文字編集アシスタントです。以下の選択テキストと編集指示に基づき、修正後のテキストのみを出力してください。説明や引用符は不要です。\n\n" +
                    "選択テキスト：\n$selectedText\n\n" +
                    "編集指示：\n$instruction"

            else ->
                "You are a text editing assistant. Given the selected text and editing instruction, output ONLY the revised text. No explanations or quotation marks.\n\n" +
                    "Selected text:\n$selectedText\n\n" +
                    "Editing instruction:\n$instruction"
        }
}
