package com.voxink.app.ime

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ImeUiStateTest {

    @Test
    fun `should define all IME states`() {
        val states: List<ImeUiState> = listOf(
            ImeUiState.Idle,
            ImeUiState.Recording,
            ImeUiState.Processing,
            ImeUiState.Result("hello"),
            ImeUiState.Error("network error"),
        )
        assertThat(states).hasSize(5)
    }

    @Test
    fun `Result should hold transcription text`() {
        val state = ImeUiState.Result("你好世界")
        assertThat(state.text).isEqualTo("你好世界")
    }

    @Test
    fun `Error should hold error message`() {
        val state = ImeUiState.Error("API key not configured")
        assertThat(state.message).isEqualTo("API key not configured")
    }

    @Test
    fun `should be exhaustive in when expression`() {
        val state: ImeUiState = ImeUiState.Idle
        val label = when (state) {
            ImeUiState.Idle -> "idle"
            ImeUiState.Recording -> "recording"
            ImeUiState.Processing -> "processing"
            is ImeUiState.Result -> "result"
            is ImeUiState.Error -> "error"
        }
        assertThat(label).isEqualTo("idle")
    }
}
