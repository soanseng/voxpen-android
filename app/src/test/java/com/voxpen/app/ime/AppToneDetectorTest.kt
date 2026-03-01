package com.voxpen.app.ime

import com.google.common.truth.Truth.assertThat
import com.voxpen.app.data.model.ToneStyle
import org.junit.jupiter.api.Test

class AppToneDetectorTest {
    // android.text.InputType is not available in JVM unit tests (no Robolectric).
    // Use raw constants instead:
    //   TYPE_CLASS_TEXT                    = 0x00000001
    //   TYPE_TEXT_VARIATION_SHORT_MESSAGE  = 0x00000010
    private val TYPE_CLASS_TEXT = 1
    private val TYPE_TEXT_VARIATION_SHORT_MESSAGE = 0x00000010

    @Test
    fun `custom rule wins over builtin`() {
        val customRules = mapOf("com.whatsapp" to ToneStyle.Professional)
        val result = AppToneDetector.detect(
            packageName = "com.whatsapp",
            inputType = 0,
            customRules = customRules,
        )
        assertThat(result).isEqualTo(ToneStyle.Professional)
    }

    @Test
    fun `builtin package match returned when no custom rule`() {
        val result = AppToneDetector.detect(
            packageName = "com.slack",
            inputType = 0,
            customRules = emptyMap(),
        )
        assertThat(result).isEqualTo(ToneStyle.Professional)
    }

    @Test
    fun `SHORT_MESSAGE inputType falls back to Casual when no package match`() {
        val shortMessageInputType = TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_SHORT_MESSAGE
        val result = AppToneDetector.detect(
            packageName = "com.unknown.app",
            inputType = shortMessageInputType,
            customRules = emptyMap(),
        )
        assertThat(result).isEqualTo(ToneStyle.Casual)
    }

    @Test
    fun `unknown app with non-message inputType returns null`() {
        val result = AppToneDetector.detect(
            packageName = "com.unknown.app",
            inputType = TYPE_CLASS_TEXT,
            customRules = emptyMap(),
        )
        assertThat(result).isNull()
    }

    @Test
    fun `empty package name returns null`() {
        val result = AppToneDetector.detect(
            packageName = "",
            inputType = 0,
            customRules = emptyMap(),
        )
        assertThat(result).isNull()
    }
}
