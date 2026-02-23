package com.voxink.app.ime

sealed interface ImeUiState {
    data object Idle : ImeUiState

    data object Recording : ImeUiState

    data object Processing : ImeUiState

    data class Result(val text: String) : ImeUiState

    data class Error(val message: String) : ImeUiState
}
