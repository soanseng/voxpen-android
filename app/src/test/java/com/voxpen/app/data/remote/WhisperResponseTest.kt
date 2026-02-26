package com.voxpen.app.data.remote

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class WhisperResponseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `should deserialize minimal JSON response`() {
        val jsonString = """{"text": "你好世界"}"""
        val response = json.decodeFromString<WhisperResponse>(jsonString)
        assertThat(response.text).isEqualTo("你好世界")
    }

    @Test
    fun `should deserialize verbose JSON response`() {
        val jsonString =
            """
            {
                "task": "transcribe",
                "language": "chinese",
                "duration": 3.45,
                "text": "Hello world",
                "segments": [
                    {
                        "id": 0,
                        "start": 0.0,
                        "end": 3.45,
                        "text": "Hello world"
                    }
                ]
            }
            """.trimIndent()
        val response = json.decodeFromString<WhisperResponse>(jsonString)
        assertThat(response.text).isEqualTo("Hello world")
        assertThat(response.language).isEqualTo("chinese")
        assertThat(response.duration).isEqualTo(3.45)
        assertThat(response.segments).hasSize(1)
        assertThat(response.segments?.first()?.text).isEqualTo("Hello world")
    }

    @Test
    fun `should handle missing optional fields`() {
        val jsonString = """{"text": "test"}"""
        val response = json.decodeFromString<WhisperResponse>(jsonString)
        assertThat(response.task).isNull()
        assertThat(response.language).isNull()
        assertThat(response.duration).isNull()
        assertThat(response.segments).isNull()
    }
}
