package com.voxink.app.ui

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class HomeScreenTest {

    @Test
    fun `should have HomeScreen composable defined`() {
        // Verify HomeScreen class/function exists and is importable
        val className = HomeScreen::class.qualifiedName
        assertThat(className).isNotNull()
    }
}
