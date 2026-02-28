package com.voxpen.app.ime

import com.google.common.truth.Truth.assertThat
import com.voxpen.app.data.model.VoiceCommand
import org.junit.jupiter.api.Test

class ImeUiStateTest {
    @Test
    fun `should define all IME states`() {
        val states: List<ImeUiState> =
            listOf(
                ImeUiState.Idle,
                ImeUiState.Recording,
                ImeUiState.Processing,
                ImeUiState.Result("hello"),
                ImeUiState.Refining("raw"),
                ImeUiState.Refined("raw", "clean"),
                ImeUiState.Error("network error"),
                ImeUiState.CommandDetected(VoiceCommand.Enter),
                ImeUiState.EditInstruction("make it formal"),
                ImeUiState.Editing,
                ImeUiState.EditResult("revised text"),
            )
        assertThat(states).hasSize(11)
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
    fun `Refining should hold original text`() {
        val state = ImeUiState.Refining("嗯那個明天開會")
        assertThat(state.original).isEqualTo("嗯那個明天開會")
    }

    @Test
    fun `Refined should hold both original and refined text`() {
        val state = ImeUiState.Refined("嗯那個明天開會", "明天開會")
        assertThat(state.original).isEqualTo("嗯那個明天開會")
        assertThat(state.refined).isEqualTo("明天開會")
    }

    @Test
    fun `should be exhaustive in when expression`() {
        val state: ImeUiState = ImeUiState.Idle
        val label =
            when (state) {
                ImeUiState.Idle -> "idle"
                ImeUiState.Recording -> "recording"
                ImeUiState.Processing -> "processing"
                is ImeUiState.Result -> "result"
                is ImeUiState.Refining -> "refining"
                is ImeUiState.Refined -> "refined"
                is ImeUiState.Error -> "error"
                is ImeUiState.CommandDetected -> "command"
                is ImeUiState.EditInstruction -> "edit_instruction"
                ImeUiState.Editing -> "editing"
                is ImeUiState.EditResult -> "edit_result"
            }
        assertThat(label).isEqualTo("idle")
    }
}
