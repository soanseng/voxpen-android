package com.voxink.app.ime

import android.view.KeyEvent

class KeyboardActionHandler(
    private val onSendKeyEvent: (Int) -> Unit,
    private val onSwitchKeyboard: () -> Boolean,
    private val onOpenSettings: () -> Unit,
    private val onMicTap: () -> Unit,
) {
    fun handle(action: KeyboardAction) {
        when (action) {
            KeyboardAction.Backspace -> onSendKeyEvent(KeyEvent.KEYCODE_DEL)
            KeyboardAction.Enter -> onSendKeyEvent(KeyEvent.KEYCODE_ENTER)
            KeyboardAction.SwitchKeyboard -> onSwitchKeyboard()
            KeyboardAction.OpenSettings -> onOpenSettings()
            KeyboardAction.MicTap -> onMicTap()
        }
    }
}
