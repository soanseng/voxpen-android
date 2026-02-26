package com.voxpen.app.ime

sealed interface KeyboardAction {
    data object Backspace : KeyboardAction

    data object Enter : KeyboardAction

    data object SwitchKeyboard : KeyboardAction

    data object MicTap : KeyboardAction

    data object OpenSettings : KeyboardAction
}
