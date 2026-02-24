package com.voxink.app.data.local

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DictionaryEntryTest {
    @Test
    fun `should have default id of zero`() {
        val entry = DictionaryEntry(word = "語墨", createdAt = 1000L)
        assertThat(entry.id).isEqualTo(0)
    }

    @Test
    fun `should store word and createdAt`() {
        val entry = DictionaryEntry(word = "Anthropic", createdAt = 1234567890L)
        assertThat(entry.word).isEqualTo("Anthropic")
        assertThat(entry.createdAt).isEqualTo(1234567890L)
    }
}
