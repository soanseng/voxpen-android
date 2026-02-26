package com.voxink.app.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ToneStyleTest {
    @Test
    fun `all tone styles should have unique keys`() {
        val keys = ToneStyle.all.map { it.key }
        assertThat(keys).containsNoDuplicates()
    }

    @Test
    fun `fromKey should return correct tone`() {
        assertThat(ToneStyle.fromKey("casual")).isEqualTo(ToneStyle.Casual)
        assertThat(ToneStyle.fromKey("professional")).isEqualTo(ToneStyle.Professional)
        assertThat(ToneStyle.fromKey("email")).isEqualTo(ToneStyle.Email)
        assertThat(ToneStyle.fromKey("note")).isEqualTo(ToneStyle.Note)
        assertThat(ToneStyle.fromKey("social")).isEqualTo(ToneStyle.Social)
        assertThat(ToneStyle.fromKey("custom")).isEqualTo(ToneStyle.Custom)
    }

    @Test
    fun `fromKey should default to Casual for unknown key`() {
        assertThat(ToneStyle.fromKey("unknown")).isEqualTo(ToneStyle.Casual)
    }

    @Test
    fun `default tone should be Casual`() {
        assertThat(ToneStyle.DEFAULT).isEqualTo(ToneStyle.Casual)
    }

    @Test
    fun `all should contain exactly 6 tones`() {
        assertThat(ToneStyle.all).hasSize(6)
    }

    @Test
    fun `each tone style has a unique emoji`() {
        val emojis = ToneStyle.all.map { it.emoji }
        assertThat(emojis).hasSize(6)
        assertThat(emojis).containsNoDuplicates()
    }

    @Test
    fun `emoji values match expected`() {
        assertThat(ToneStyle.Casual.emoji).isEqualTo("\uD83D\uDCAC")           // 💬
        assertThat(ToneStyle.Professional.emoji).isEqualTo("\uD83D\uDCBC")     // 💼
        assertThat(ToneStyle.Email.emoji).isEqualTo("\uD83D\uDCE7")            // 📧
        assertThat(ToneStyle.Note.emoji).isEqualTo("\uD83D\uDCDD")             // 📝
        assertThat(ToneStyle.Social.emoji).isEqualTo("\uD83D\uDCF1")           // 📱
        assertThat(ToneStyle.Custom.emoji).isEqualTo("⚙")
    }
}
