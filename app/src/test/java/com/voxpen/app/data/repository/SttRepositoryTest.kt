package com.voxpen.app.data.repository

import com.google.common.truth.Truth.assertThat
import com.voxpen.app.data.model.SttLanguage
import com.voxpen.app.data.remote.GroqApi
import com.voxpen.app.data.remote.SttApiFactory
import com.voxpen.app.data.remote.WhisperResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.RequestBody
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

class SttRepositoryTest {
    private val groqApi: GroqApi = mockk()
    private lateinit var sttApiFactory: SttApiFactory
    private lateinit var repository: SttRepository

    @BeforeEach
    fun setUp() {
        sttApiFactory = mockk()
        repository = SttRepository(groqApi, sttApiFactory)
    }

    @Test
    fun `should return transcription text on successful API call`() =
        runTest {
            coEvery {
                groqApi.transcribe(any(), any(), any(), any(), any(), any())
            } returns WhisperResponse(text = "你好世界")

            val result =
                repository.transcribe(
                    wavBytes = ByteArray(100),
                    language = SttLanguage.Chinese,
                    apiKey = "test-key",
                )

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isEqualTo("你好世界")
        }

    @Test
    fun `should pass authorization header with Bearer prefix`() =
        runTest {
            val authSlot = slot<String>()
            coEvery {
                groqApi.transcribe(capture(authSlot), any(), any(), any(), any(), any())
            } returns WhisperResponse(text = "test")

            repository.transcribe(ByteArray(10), SttLanguage.Auto, "my-api-key")

            assertThat(authSlot.captured).isEqualTo("Bearer my-api-key")
        }

    @Test
    fun `should omit language parameter for auto-detect`() =
        runTest {
            val langSlot = slot<RequestBody?>()
            coEvery {
                groqApi.transcribe(any(), any(), any(), any(), captureNullable(langSlot), any())
            } returns WhisperResponse(text = "test")

            repository.transcribe(ByteArray(10), SttLanguage.Auto, "key")

            assertThat(langSlot.captured).isNull()
        }

    @Test
    fun `should pass language code for specific language`() =
        runTest {
            coEvery {
                groqApi.transcribe(any(), any(), any(), any(), any(), any())
            } returns WhisperResponse(text = "test")

            repository.transcribe(ByteArray(10), SttLanguage.Chinese, "key")

            coVerify {
                groqApi.transcribe(any(), any(), any(), any(), isNull(inverse = true), any())
            }
        }

    @Test
    fun `should return failure on IOException`() =
        runTest {
            coEvery {
                groqApi.transcribe(any(), any(), any(), any(), any(), any())
            } throws IOException("Network error")

            val result = repository.transcribe(ByteArray(10), SttLanguage.Auto, "key")

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()?.message).contains("Network error")
        }

    @Test
    fun `should return failure on empty API key`() =
        runTest {
            val result = repository.transcribe(ByteArray(10), SttLanguage.Auto, "")

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()?.message).contains("API key")
        }

    @Test
    fun `should use provided model name in API request`() =
        runTest {
            val modelSlot = slot<RequestBody>()
            coEvery {
                groqApi.transcribe(any(), any(), capture(modelSlot), any(), any(), any())
            } returns WhisperResponse(text = "test")

            repository.transcribe(
                wavBytes = ByteArray(10),
                language = SttLanguage.Auto,
                apiKey = "key",
                model = "whisper-large-v3",
            )

            val buffer = okio.Buffer()
            modelSlot.captured.writeTo(buffer)
            assertThat(buffer.readUtf8()).isEqualTo("whisper-large-v3")
        }

    @Test
    fun `should use custom API when customSttBaseUrl is provided`() =
        runTest {
            val customApi: GroqApi = mockk()
            every { sttApiFactory.createForCustom("https://my-whisper.example.com/") } returns customApi
            coEvery { customApi.transcribe(any(), any(), any(), any(), any(), any()) } returns
                WhisperResponse(text = "custom result")

            val result =
                repository.transcribe(
                    wavBytes = ByteArray(10),
                    language = SttLanguage.Auto,
                    apiKey = "key",
                    customSttBaseUrl = "https://my-whisper.example.com/",
                )

            assertThat(result.getOrNull()).isEqualTo("custom result")
            coVerify(exactly = 0) { groqApi.transcribe(any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun `should use Groq API when customSttBaseUrl is null`() =
        runTest {
            coEvery { groqApi.transcribe(any(), any(), any(), any(), any(), any()) } returns
                WhisperResponse(text = "groq result")

            val result =
                repository.transcribe(
                    wavBytes = ByteArray(10),
                    language = SttLanguage.Auto,
                    apiKey = "key",
                    customSttBaseUrl = null,
                )

            assertThat(result.getOrNull()).isEqualTo("groq result")
        }
}
