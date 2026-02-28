package com.voxpen.app.ime

import com.google.common.truth.Truth.assertThat
import com.voxpen.app.data.model.VoiceCommand
import org.junit.jupiter.api.Test

class VoiceCommandRecognizerTest {
    @Test
    fun `should recognize 送出 as Enter`() {
        assertThat(VoiceCommandRecognizer.recognize("送出")).isEqualTo(VoiceCommand.Enter)
    }

    @Test
    fun `should recognize 傳送 as Enter`() {
        assertThat(VoiceCommandRecognizer.recognize("傳送")).isEqualTo(VoiceCommand.Enter)
    }

    @Test
    fun `should recognize send as Enter (case insensitive)`() {
        assertThat(VoiceCommandRecognizer.recognize("Send")).isEqualTo(VoiceCommand.Enter)
    }

    @Test
    fun `should recognize 刪除 as Backspace`() {
        assertThat(VoiceCommandRecognizer.recognize("刪除")).isEqualTo(VoiceCommand.Backspace)
    }

    @Test
    fun `should recognize delete as Backspace`() {
        assertThat(VoiceCommandRecognizer.recognize("delete")).isEqualTo(VoiceCommand.Backspace)
    }

    @Test
    fun `should recognize 換行 as Newline`() {
        assertThat(VoiceCommandRecognizer.recognize("換行")).isEqualTo(VoiceCommand.Newline)
    }

    @Test
    fun `should recognize new line as Newline`() {
        assertThat(VoiceCommandRecognizer.recognize("new line")).isEqualTo(VoiceCommand.Newline)
    }

    @Test
    fun `should recognize 空格 as Space`() {
        assertThat(VoiceCommandRecognizer.recognize("空格")).isEqualTo(VoiceCommand.Space)
    }

    @Test
    fun `should return null for normal text`() {
        assertThat(VoiceCommandRecognizer.recognize("你好世界")).isNull()
        assertThat(VoiceCommandRecognizer.recognize("hello world")).isNull()
        assertThat(VoiceCommandRecognizer.recognize("")).isNull()
    }

    @Test
    fun `should ignore leading and trailing whitespace`() {
        assertThat(VoiceCommandRecognizer.recognize("  送出  ")).isEqualTo(VoiceCommand.Enter)
    }
}
