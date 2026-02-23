package com.voxink.app.data.remote

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class ChatCompletionTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `should serialize ChatMessage with role and content`() {
        val message = ChatMessage(role = "system", content = "You are a helper")
        val encoded = json.encodeToString(message)
        assertThat(encoded).contains("\"role\":\"system\"")
        assertThat(encoded).contains("\"content\":\"You are a helper\"")
    }

    @Test
    fun `should serialize ChatCompletionRequest with all fields`() {
        val request =
            ChatCompletionRequest(
                model = "llama-3.3-70b-versatile",
                messages = listOf(ChatMessage("user", "hello")),
                temperature = 0.3,
                maxTokens = 2048,
            )
        val encoded = json.encodeToString(request)
        assertThat(encoded).contains("\"model\":\"llama-3.3-70b-versatile\"")
        assertThat(encoded).contains("\"temperature\":0.3")
        assertThat(encoded).contains("\"max_tokens\":2048")
    }

    @Test
    fun `should deserialize ChatCompletionResponse`() {
        val responseJson =
            """
            {
                "id": "chatcmpl-123",
                "object": "chat.completion",
                "choices": [
                    {
                        "index": 0,
                        "message": {
                            "role": "assistant",
                            "content": "Refined text here"
                        },
                        "finish_reason": "stop"
                    }
                ]
            }
            """.trimIndent()
        val response = json.decodeFromString<ChatCompletionResponse>(responseJson)
        assertThat(response.id).isEqualTo("chatcmpl-123")
        assertThat(response.choices).hasSize(1)
        assertThat(response.choices[0].message.content).isEqualTo("Refined text here")
    }

    @Test
    fun `should handle multiple choices in response`() {
        val responseJson =
            """
            {
                "id": "chatcmpl-456",
                "choices": [
                    {"index": 0, "message": {"role": "assistant", "content": "First"}},
                    {"index": 1, "message": {"role": "assistant", "content": "Second"}}
                ]
            }
            """.trimIndent()
        val response = json.decodeFromString<ChatCompletionResponse>(responseJson)
        assertThat(response.choices).hasSize(2)
        assertThat(response.choices[0].message.content).isEqualTo("First")
    }
}
