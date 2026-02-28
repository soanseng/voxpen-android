package com.voxpen.app.ime

import com.voxpen.app.data.model.VoiceCommand

object VoiceCommandRecognizer {
    private val COMMANDS: Map<String, VoiceCommand> = buildMap {
        // Enter / Send
        listOf("送出", "傳送", "寄出", "send", "enter", "return", "submit").forEach {
            put(it, VoiceCommand.Enter)
        }
        // Backspace / Delete
        listOf("刪除", "退格", "delete", "backspace", "erase").forEach {
            put(it, VoiceCommand.Backspace)
        }
        // Newline
        listOf("換行", "new line", "newline", "next line").forEach {
            put(it, VoiceCommand.Newline)
        }
        // Space
        listOf("空格", "space").forEach {
            put(it, VoiceCommand.Space)
        }
    }

    /** Returns a [VoiceCommand] if [text] exactly matches a known command, null otherwise. */
    fun recognize(text: String): VoiceCommand? = COMMANDS[text.trim().lowercase()]
}
