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

    /** Undoes the last action */
    data object Undo : VoiceCommand()

    /** Selects all text in the field */
    data object SelectAll : VoiceCommand()

    /** Copies selected text to clipboard */
    data object Copy : VoiceCommand()

    /** Pastes clipboard content */
    data object Paste : VoiceCommand()

    /** Cuts selected text to clipboard */
    data object Cut : VoiceCommand()

    /** Clears all text in the field */
    data object ClearAll : VoiceCommand()
}
