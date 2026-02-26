package com.voxpen.app.util

import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioChunker {
    private const val WAV_HEADER_SIZE = 44
    private const val DEFAULT_MAX_CHUNK_BYTES = 25 * 1024 * 1024 // 25MB

    fun chunk(
        data: ByteArray,
        maxChunkBytes: Int = DEFAULT_MAX_CHUNK_BYTES,
    ): List<ByteArray> {
        if (data.size <= maxChunkBytes) return listOf(data)
        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < data.size) {
            val end = minOf(offset + maxChunkBytes, data.size)
            chunks.add(data.copyOfRange(offset, end))
            offset = end
        }
        return chunks
    }

    fun chunkWav(
        wavBytes: ByteArray,
        maxChunkBytes: Int = DEFAULT_MAX_CHUNK_BYTES,
    ): List<ByteArray> {
        if (wavBytes.size <= maxChunkBytes) return listOf(wavBytes)
        if (!isWav(wavBytes)) return chunk(wavBytes, maxChunkBytes)

        val header = wavBytes.copyOfRange(0, WAV_HEADER_SIZE)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        // Extract audio parameters from WAV header
        buffer.position(22)
        val channels = buffer.short.toInt()
        val sampleRate = buffer.int
        buffer.position(34)
        val bitsPerSample = buffer.short.toInt()
        val bytesPerSample = channels * bitsPerSample / 8

        val pcmData = wavBytes.copyOfRange(WAV_HEADER_SIZE, wavBytes.size)
        val maxPcmPerChunk = maxChunkBytes - WAV_HEADER_SIZE

        // Align to sample boundaries
        val alignedMaxPcm = (maxPcmPerChunk / bytesPerSample) * bytesPerSample

        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < pcmData.size) {
            val end = minOf(offset + alignedMaxPcm, pcmData.size)
            val chunkPcm = pcmData.copyOfRange(offset, end)
            chunks.add(AudioEncoder.pcmToWav(chunkPcm, sampleRate, channels, bitsPerSample))
            offset = end
        }
        return chunks
    }

    fun isWav(data: ByteArray): Boolean {
        if (data.size < WAV_HEADER_SIZE) return false
        val riff = String(data, 0, 4, Charsets.US_ASCII)
        val wave = String(data, 8, 4, Charsets.US_ASCII)
        return riff == "RIFF" && wave == "WAVE"
    }
}
