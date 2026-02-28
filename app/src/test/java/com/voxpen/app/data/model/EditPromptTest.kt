package com.voxpen.app.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class EditPromptTest {
    @Test
    fun `should include selected text in prompt`() {
        val prompt = EditPrompt.build(
            selectedText = "hello world",
            instruction = "make it formal",
            language = SttLanguage.English,
        )
        assertThat(prompt).contains("hello world")
        assertThat(prompt).contains("make it formal")
    }

    @Test
    fun `should include Chinese instruction in Chinese prompt`() {
        val prompt = EditPrompt.build(
            selectedText = "你好",
            instruction = "讓它更正式",
            language = SttLanguage.Chinese,
        )
        assertThat(prompt).contains("你好")
        assertThat(prompt).contains("讓它更正式")
        assertThat(prompt).contains("繁體中文")
    }

    @Test
    fun `should instruct LLM to output only revised text`() {
        val prompt = EditPrompt.build("foo", "bar", SttLanguage.English)
        assertThat(prompt.lowercase()).contains("only")
    }
}
