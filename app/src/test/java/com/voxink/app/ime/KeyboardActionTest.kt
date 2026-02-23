package com.voxink.app.ime

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class KeyboardActionTest {

    @Test
    fun `should define all required keyboard actions`() {
        val actions: List<KeyboardAction> = listOf(
            KeyboardAction.Backspace,
            KeyboardAction.Enter,
            KeyboardAction.SwitchKeyboard,
            KeyboardAction.MicTap,
            KeyboardAction.OpenSettings,
        )
        assertThat(actions).hasSize(5)
    }

    @Test
    fun `should distinguish between different actions`() {
        assertThat(KeyboardAction.Backspace).isNotEqualTo(KeyboardAction.Enter)
        assertThat(KeyboardAction.MicTap).isNotEqualTo(KeyboardAction.SwitchKeyboard)
    }

    @Test
    fun `should be a sealed interface with exhaustive when`() {
        val action: KeyboardAction = KeyboardAction.Backspace
        val label = when (action) {
            KeyboardAction.Backspace -> "backspace"
            KeyboardAction.Enter -> "enter"
            KeyboardAction.SwitchKeyboard -> "switch"
            KeyboardAction.MicTap -> "mic"
            KeyboardAction.OpenSettings -> "settings"
        }
        assertThat(label).isEqualTo("backspace")
    }
}
