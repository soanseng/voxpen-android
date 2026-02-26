package com.voxpen.app.util

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioEncoder {
    private const val WAV_HEADER_SIZE = 44

    fun pcmToWav(
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val totalSize = WAV_HEADER_SIZE + dataSize

        val buffer = ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(totalSize - 8) // file size - 8
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))

        // fmt sub-chunk
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16) // sub-chunk size (PCM)
        buffer.putShort(1) // audio format (PCM = 1)
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())

        // data sub-chunk
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)

        val output = ByteArrayOutputStream(totalSize)
        output.write(buffer.array())
        output.write(pcmData)
        return output.toByteArray()
    }
}
