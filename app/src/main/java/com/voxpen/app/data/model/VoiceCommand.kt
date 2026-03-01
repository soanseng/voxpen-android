package com.voxpen.app.data.model

sealed class VoiceCommand {
    /** Sends Enter / submits the text field */
    data object Enter : VoiceCommand()

    /** Deletes the character before cursor */
    data object Backspace : VoiceCommand()

    /** Inserts a newline character (for multi-line fields) */
    data object Newline : VoiceCommand()

    /** Inserts a space character */
    data object Space : VoiceCommand()
}
