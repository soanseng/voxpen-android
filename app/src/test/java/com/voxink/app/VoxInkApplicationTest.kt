package com.voxink.app

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class VoxInkApplicationTest {
    @Test
    fun `should have correct qualified name`() {
        val name = VoxInkApplication::class.qualifiedName
        assertThat(name).isEqualTo("com.voxink.app.VoxInkApplication")
    }
}
