package com.voxpen.app.ime

import com.voxpen.app.data.model.VoiceCommand

object VoiceCommandRecognizer {
    private val COMMANDS: Map<String, VoiceCommand> = buildMap {
        // Enter / Send
        listOf("送出", "傳送", "寄出", "send", "enter", "return", "submit", "送信", "確定", "送る").forEach {
            put(it, VoiceCommand.Enter)
        }
        // Backspace / Delete
        listOf("刪除", "退格", "delete", "backspace", "erase", "削除", "バックスペース").forEach {
            put(it, VoiceCommand.Backspace)
        }
        // Newline
        listOf("換行", "new line", "newline", "next line", "改行").forEach {
            put(it, VoiceCommand.Newline)
        }
        // Space
        listOf("空格", "space", "スペース").forEach {
            put(it, VoiceCommand.Space)
        }
        // Undo
        listOf("復原", "undo", "元に戻す").forEach {
            put(it, VoiceCommand.Undo)
        }
        // Select All
        listOf("全選", "select all", "全て選択", "すべて選択").forEach {
            put(it, VoiceCommand.SelectAll)
        }
        // Copy
        listOf("複製", "copy", "コピー").forEach {
            put(it, VoiceCommand.Copy)
        }
        // Paste
        listOf("貼上", "paste", "貼り付け").forEach {
            put(it, VoiceCommand.Paste)
        }
        // Cut
        listOf("剪下", "cut", "切り取り").forEach {
            put(it, VoiceCommand.Cut)
        }
        // Clear All
        listOf("全部刪除", "clear all", "clear", "全削除").forEach {
            put(it, VoiceCommand.ClearAll)
        }
    }

    /** Returns a [VoiceCommand] if [text] exactly matches a known command, null otherwise. */
    fun recognize(text: String): VoiceCommand? = COMMANDS[text.trim().lowercase()]
}
