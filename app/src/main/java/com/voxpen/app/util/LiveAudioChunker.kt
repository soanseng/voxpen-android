package com.voxpen.app.util

object LiveAudioChunker {
    private const val DEFAULT_SAMPLE_RATE = 16_000
    private const val DEFAULT_CHANNELS = 1
    private const val DEFAULT_BITS_PER_SAMPLE = 16
    const val CHUNK_DURATION_SECONDS = 60
    const val CHUNK_BYTES =
        DEFAULT_SAMPLE_RATE * DEFAULT_CHANNELS * (DEFAULT_BITS_PER_SAMPLE / 8) * CHUNK_DURATION_SECONDS

    fun chunkPcm(
        pcmData: ByteArray,
        maxChunkBytes: Int = CHUNK_BYTES,
        channels: Int = DEFAULT_CHANNELS,
        bitsPerSample: Int = DEFAULT_BITS_PER_SAMPLE,
    ): List<ByteArray> {
        if (pcmData.size <= maxChunkBytes) return listOf(pcmData)

        val frameSize = channels * bitsPerSample / 8
        val alignedMaxChunkBytes = (maxChunkBytes / frameSize) * frameSize
        require(alignedMaxChunkBytes > 0) { "maxChunkBytes must fit at least one audio frame" }

        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < pcmData.size) {
            val end = minOf(offset + alignedMaxChunkBytes, pcmData.size)
            chunks.add(pcmData.copyOfRange(offset, end))
            offset = end
        }
        return chunks
    }
}
