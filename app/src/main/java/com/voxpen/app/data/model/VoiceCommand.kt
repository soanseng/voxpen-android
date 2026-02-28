package com.voxpen.app.data.model

import android.view.KeyEvent

sealed class VoiceCommand(val keyCode: Int) {
    /** Sends Enter / submits the text field */
    data object Enter : VoiceCommand(KeyEvent.KEYCODE_ENTER)

    /** Deletes the character before cursor */
    data object Backspace : VoiceCommand(KeyEvent.KEYCODE_DEL)

    /** Inserts a newline character (for multi-line fields) */
    data object Newline : VoiceCommand(KeyEvent.KEYCODE_ENTER)

    /** Inserts a space character */
    data object Space : VoiceCommand(KeyEvent.KEYCODE_SPACE)
}
