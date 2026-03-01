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
    fun `should recognize 送信 as Enter`() {
        assertThat(VoiceCommandRecognizer.recognize("送信")).isEqualTo(VoiceCommand.Enter)
    }

    @Test
    fun `should recognize 確定 as Enter`() {
        assertThat(VoiceCommandRecognizer.recognize("確定")).isEqualTo(VoiceCommand.Enter)
    }

    @Test
    fun `should recognize 削除 as Backspace`() {
        assertThat(VoiceCommandRecognizer.recognize("削除")).isEqualTo(VoiceCommand.Backspace)
    }

    @Test
    fun `should recognize バックスペース as Backspace`() {
        assertThat(VoiceCommandRecognizer.recognize("バックスペース")).isEqualTo(VoiceCommand.Backspace)
    }

    @Test
    fun `should recognize 改行 as Newline`() {
        assertThat(VoiceCommandRecognizer.recognize("改行")).isEqualTo(VoiceCommand.Newline)
    }

    @Test
    fun `should recognize スペース as Space`() {
        assertThat(VoiceCommandRecognizer.recognize("スペース")).isEqualTo(VoiceCommand.Space)
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

    // --- Undo ---
    @Test
    fun `should recognize 復原 as Undo`() {
        assertThat(VoiceCommandRecognizer.recognize("復原")).isEqualTo(VoiceCommand.Undo)
    }

    @Test
    fun `should recognize undo as Undo`() {
        assertThat(VoiceCommandRecognizer.recognize("undo")).isEqualTo(VoiceCommand.Undo)
    }

    @Test
    fun `should recognize 元に戻す as Undo`() {
        assertThat(VoiceCommandRecognizer.recognize("元に戻す")).isEqualTo(VoiceCommand.Undo)
    }

    // --- Select All ---
    @Test
    fun `should recognize 全選 as SelectAll`() {
        assertThat(VoiceCommandRecognizer.recognize("全選")).isEqualTo(VoiceCommand.SelectAll)
    }

    @Test
    fun `should recognize select all as SelectAll`() {
        assertThat(VoiceCommandRecognizer.recognize("select all")).isEqualTo(VoiceCommand.SelectAll)
    }

    @Test
    fun `should recognize 全て選択 as SelectAll`() {
        assertThat(VoiceCommandRecognizer.recognize("全て選択")).isEqualTo(VoiceCommand.SelectAll)
    }

    @Test
    fun `should recognize すべて選択 as SelectAll`() {
        assertThat(VoiceCommandRecognizer.recognize("すべて選択")).isEqualTo(VoiceCommand.SelectAll)
    }

    // --- Copy ---
    @Test
    fun `should recognize 複製 as Copy`() {
        assertThat(VoiceCommandRecognizer.recognize("複製")).isEqualTo(VoiceCommand.Copy)
    }

    @Test
    fun `should recognize copy as Copy`() {
        assertThat(VoiceCommandRecognizer.recognize("copy")).isEqualTo(VoiceCommand.Copy)
    }

    @Test
    fun `should recognize コピー as Copy`() {
        assertThat(VoiceCommandRecognizer.recognize("コピー")).isEqualTo(VoiceCommand.Copy)
    }

    // --- Paste ---
    @Test
    fun `should recognize 貼上 as Paste`() {
        assertThat(VoiceCommandRecognizer.recognize("貼上")).isEqualTo(VoiceCommand.Paste)
    }

    @Test
    fun `should recognize paste as Paste`() {
        assertThat(VoiceCommandRecognizer.recognize("paste")).isEqualTo(VoiceCommand.Paste)
    }

    @Test
    fun `should recognize 貼り付け as Paste`() {
        assertThat(VoiceCommandRecognizer.recognize("貼り付け")).isEqualTo(VoiceCommand.Paste)
    }

    // --- Cut ---
    @Test
    fun `should recognize 剪下 as Cut`() {
        assertThat(VoiceCommandRecognizer.recognize("剪下")).isEqualTo(VoiceCommand.Cut)
    }

    @Test
    fun `should recognize cut as Cut`() {
        assertThat(VoiceCommandRecognizer.recognize("cut")).isEqualTo(VoiceCommand.Cut)
    }

    @Test
    fun `should recognize 切り取り as Cut`() {
        assertThat(VoiceCommandRecognizer.recognize("切り取り")).isEqualTo(VoiceCommand.Cut)
    }

    // --- Clear All ---
    @Test
    fun `should recognize 全部刪除 as ClearAll`() {
        assertThat(VoiceCommandRecognizer.recognize("全部刪除")).isEqualTo(VoiceCommand.ClearAll)
    }

    @Test
    fun `should recognize clear all as ClearAll`() {
        assertThat(VoiceCommandRecognizer.recognize("clear all")).isEqualTo(VoiceCommand.ClearAll)
    }

    @Test
    fun `should recognize clear as ClearAll`() {
        assertThat(VoiceCommandRecognizer.recognize("clear")).isEqualTo(VoiceCommand.ClearAll)
    }

    @Test
    fun `should recognize 全削除 as ClearAll`() {
        assertThat(VoiceCommandRecognizer.recognize("全削除")).isEqualTo(VoiceCommand.ClearAll)
    }
}
