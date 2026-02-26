package com.voxpen.app.ime

import android.view.KeyEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class KeyboardActionHandlerTest {
    private val onSendKeyEvent: (Int) -> Unit = mockk(relaxed = true)
    private val onSwitchKeyboard: () -> Boolean = mockk()
    private val onOpenSettings: () -> Unit = mockk(relaxed = true)
    private val onMicTap: () -> Unit = mockk(relaxed = true)

    private lateinit var handler: KeyboardActionHandler

    @BeforeEach
    fun setUp() {
        every { onSwitchKeyboard() } returns true
        handler =
            KeyboardActionHandler(
                onSendKeyEvent = onSendKeyEvent,
                onSwitchKeyboard = onSwitchKeyboard,
                onOpenSettings = onOpenSettings,
                onMicTap = onMicTap,
            )
    }

    @Test
    fun `should send DEL key event on Backspace action`() {
        handler.handle(KeyboardAction.Backspace)
        verify(exactly = 1) { onSendKeyEvent(KeyEvent.KEYCODE_DEL) }
    }

    @Test
    fun `should send ENTER key event on Enter action`() {
        handler.handle(KeyboardAction.Enter)
        verify(exactly = 1) { onSendKeyEvent(KeyEvent.KEYCODE_ENTER) }
    }

    @Test
    fun `should call switch keyboard on SwitchKeyboard action`() {
        handler.handle(KeyboardAction.SwitchKeyboard)
        verify(exactly = 1) { onSwitchKeyboard() }
    }

    @Test
    fun `should call open settings on OpenSettings action`() {
        handler.handle(KeyboardAction.OpenSettings)
        verify(exactly = 1) { onOpenSettings() }
    }

    @Test
    fun `should call mic tap on MicTap action`() {
        handler.handle(KeyboardAction.MicTap)
        verify(exactly = 1) { onMicTap() }
    }

    @Test
    fun `should not call other callbacks when handling specific action`() {
        handler.handle(KeyboardAction.Backspace)
        verify(exactly = 0) { onSwitchKeyboard() }
        verify(exactly = 0) { onOpenSettings() }
        verify(exactly = 0) { onMicTap() }
    }
}
