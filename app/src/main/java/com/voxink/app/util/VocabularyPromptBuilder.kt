package com.voxink.app.util

import com.voxink.app.data.model.SttLanguage
import kotlin.math.ceil

object VocabularyPromptBuilder {
    private const val WHISPER_TOKEN_BUDGET = 200

    fun buildWhisperPrompt(
        language: SttLanguage,
        vocabulary: List<String>,
    ): String {
        val basePrompt = language.prompt
        if (vocabulary.isEmpty()) return basePrompt

        val baseTokens = estimateTokens(basePrompt)
        val remainingBudget = WHISPER_TOKEN_BUDGET - baseTokens
        if (remainingBudget <= 0) return basePrompt

        val selected = mutableListOf<String>()
        var usedTokens = 0
        for (word in vocabulary) {
            val wordTokens = estimateTokens(word) + 1 // +1 for ", " separator
            if (usedTokens + wordTokens > remainingBudget) break
            selected.add(word)
            usedTokens += wordTokens
        }

        if (selected.isEmpty()) return basePrompt
        return basePrompt + " " + selected.joinToString(", ")
    }

    fun buildLlmSuffix(
        language: SttLanguage,
        vocabulary: List<String>,
    ): String {
        if (vocabulary.isEmpty()) return ""

        val words = vocabulary.joinToString(", ")
        return when (language) {
            SttLanguage.English ->
                "\nVocabulary (prefer these terms): $words"
            SttLanguage.Japanese ->
                "\n用語集（これらの用語を優先してください）：$words"
            else ->
                "\n術語表（請優先使用這些詞彙）：$words"
        }
    }

    fun estimateTokens(text: String): Int {
        var cjkChars = 0
        var latinChars = 0
        for (ch in text) {
            if (ch.code in 0x4E00..0x9FFF ||
                ch.code in 0x3400..0x4DBF ||
                ch.code in 0x3040..0x309F ||
                ch.code in 0x30A0..0x30FF
            ) {
                cjkChars++
            } else {
                latinChars++
            }
        }
        return cjkChars * 2 + ceil(latinChars / 4.0).toInt()
    }
}
