package com.voxink.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ThemeTest {

    @Nested
    @DisplayName("Brand Colors")
    inner class BrandColors {

        @Test
        fun `should define VoxInk purple as primary brand color`() {
            assertThat(VoxInkPurple).isEqualTo(Color(0xFF6366F1))
        }

        @Test
        fun `should define light purple variant`() {
            assertThat(VoxInkPurpleLight).isEqualTo(Color(0xFF818CF8))
        }

        @Test
        fun `should define dark purple variant`() {
            assertThat(VoxInkPurpleDark).isEqualTo(Color(0xFF4F46E5))
        }
    }

    @Nested
    @DisplayName("Semantic Colors")
    inner class SemanticColors {

        @Test
        fun `should define red for active mic`() {
            assertThat(MicActive).isEqualTo(Color(0xFFEF4444))
        }

        @Test
        fun `should define purple for idle mic`() {
            assertThat(MicIdle).isEqualTo(Color(0xFF6366F1))
        }

        @Test
        fun `should define dark surface for keyboard background`() {
            assertThat(KeyboardSurface).isEqualTo(Color(0xFF1C1B1F))
        }
    }

    @Nested
    @DisplayName("Typography")
    inner class TypographyTests {

        @Test
        fun `should define titleLarge at 22sp`() {
            assertThat(VoxInkTypography.titleLarge.fontSize.value).isEqualTo(22f)
        }

        @Test
        fun `should define bodyLarge at 16sp`() {
            assertThat(VoxInkTypography.bodyLarge.fontSize.value).isEqualTo(16f)
        }
    }
}
