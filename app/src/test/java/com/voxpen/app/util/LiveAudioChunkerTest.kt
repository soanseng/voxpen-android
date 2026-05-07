package com.voxpen.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LiveAudioChunkerTest {
    @Test
    fun `short pcm stays as one chunk`() {
        val pcm = ByteArray(1_000) { it.toByte() }

        assertThat(LiveAudioChunker.chunkPcm(pcm)).containsExactly(pcm)
    }

    @Test
    fun `long pcm is split without boxing bytes`() {
        val pcm = ByteArray(LiveAudioChunker.CHUNK_BYTES + 128) { it.toByte() }

        val chunks = LiveAudioChunker.chunkPcm(pcm)

        assertThat(chunks).hasSize(2)
        assertThat(chunks[0]).hasLength(LiveAudioChunker.CHUNK_BYTES)
        assertThat(chunks[1]).hasLength(128)
    }

    @Test
    fun `chunks align to audio frame size`() {
        val pcm = ByteArray(11)

        val chunks = LiveAudioChunker.chunkPcm(pcm, maxChunkBytes = 5, channels = 1, bitsPerSample = 16)

        assertThat(chunks.map { it.size }).containsExactly(4, 4, 3).inOrder()
    }
}
