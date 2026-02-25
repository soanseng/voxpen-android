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
                "\nCustom dictionary (voice recognition may produce near-homophone errors, please correct accordingly): $words"
            SttLanguage.Japanese ->
                "\nカスタム辞書（音声認識で類似音の誤変換が発生する可能性があります。以下の語彙で修正してください）：$words"
            SttLanguage.Korean ->
                "\n사용자 사전 (음성 인식에서 유사 발음 오류가 발생할 수 있습니다. 다음 단어로 수정해 주세요): $words"
            SttLanguage.French ->
                "\nDictionnaire personnalisé (la reconnaissance vocale peut produire des erreurs d'homophones, veuillez corriger en conséquence) : $words"
            SttLanguage.German ->
                "\nBenutzerwörterbuch (Spracherkennung kann Homophon-Fehler erzeugen, bitte entsprechend korrigieren): $words"
            SttLanguage.Spanish ->
                "\nDiccionario personalizado (el reconocimiento de voz puede producir errores de homófonos, corrija en consecuencia): $words"
            SttLanguage.Vietnamese ->
                "\nTừ điển tùy chỉnh (nhận dạng giọng nói có thể tạo lỗi đồng âm, vui lòng sửa theo danh sách sau): $words"
            SttLanguage.Indonesian ->
                "\nKamus kustom (pengenalan suara mungkin menghasilkan kesalahan homofon, mohon koreksi sesuai daftar berikut): $words"
            SttLanguage.Thai ->
                "\nพจนานุกรมกำหนดเอง (การรู้จำเสียงอาจเกิดข้อผิดพลาดจากคำพ้องเสียง กรุณาแก้ไขตามรายการต่อไปนี้): $words"
            else ->
                "\n自定義詞典（語音辨識可能產生音近詞錯誤，請依此修正）：$words"
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
