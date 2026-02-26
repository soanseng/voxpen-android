package com.voxpen.app

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class VoxPenApplicationTest {
    @Test
    fun `should have correct qualified name`() {
        val name = VoxPenApplication::class.qualifiedName
        assertThat(name).isEqualTo("com.voxpen.app.VoxPenApplication")
    }
}
