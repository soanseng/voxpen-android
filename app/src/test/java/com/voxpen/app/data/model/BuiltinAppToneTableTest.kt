package com.voxpen.app.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class BuiltinAppToneTableTest {
    @Test
    fun `all entries map to a valid ToneStyle`() {
        BuiltinAppToneTable.rules.forEach { (pkg, tone) ->
            assertThat(ToneStyle.all).contains(tone)
        }
    }

    @Test
    fun `table contains 22 entries`() {
        assertThat(BuiltinAppToneTable.rules).hasSize(22)
    }

    @Test
    fun `whatsapp maps to Casual`() {
        assertThat(BuiltinAppToneTable.rules["com.whatsapp"]).isEqualTo(ToneStyle.Casual)
    }

    @Test
    fun `gmail maps to Email`() {
        assertThat(BuiltinAppToneTable.rules["com.google.android.gm"]).isEqualTo(ToneStyle.Email)
    }

    @Test
    fun `slack maps to Professional`() {
        assertThat(BuiltinAppToneTable.rules["com.slack"]).isEqualTo(ToneStyle.Professional)
    }

    @Test
    fun `notion maps to Note`() {
        assertThat(BuiltinAppToneTable.rules["com.notion.id"]).isEqualTo(ToneStyle.Note)
    }

    @Test
    fun `twitter maps to Social`() {
        assertThat(BuiltinAppToneTable.rules["com.twitter.android"]).isEqualTo(ToneStyle.Social)
    }
}
