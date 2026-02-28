package com.voxpen.app.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class VoiceCommandTest {
    @Test
    fun `VoiceCommand types exist`() {
        val commands: List<VoiceCommand> = listOf(
            VoiceCommand.Enter,
            VoiceCommand.Backspace,
            VoiceCommand.Newline,
            VoiceCommand.Space,
        )
        assertThat(commands).hasSize(4)
    }
}
