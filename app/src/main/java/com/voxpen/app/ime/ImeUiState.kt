package com.voxpen.app.ime

import com.voxpen.app.data.model.VoiceCommand

sealed interface ImeUiState {
    data object Idle : ImeUiState

    data object Recording : ImeUiState

    data object Processing : ImeUiState

    data class Result(val text: String) : ImeUiState

    data class Refining(val original: String) : ImeUiState

    data class Refined(val original: String, val refined: String) : ImeUiState

    data class Error(val message: String) : ImeUiState

    /** A voice command was recognised — execute the keyboard action instead of inserting text. */
    data class CommandDetected(val command: VoiceCommand) : ImeUiState

    /** Speak-to-Edit: STT produced an edit instruction; VoxPenIME will read the selection and call the LLM. */
    data class EditInstruction(val instruction: String) : ImeUiState

    /** Speak-to-Edit: LLM edit call is in progress. */
    data object Editing : ImeUiState

    /** Speak-to-Edit: LLM returned revised text — commit it to the input field. */
    data class EditResult(val revised: String) : ImeUiState
}
