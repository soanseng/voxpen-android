package com.voxpen.app.data.local

import android.content.Context
import com.voxpen.app.domain.usecase.TranscribeAudioUseCase
import com.voxpen.app.util.AudioEncoder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val recordingsDir: File
            get() = File(context.filesDir, RECORDINGS_DIR).apply { mkdirs() }

        fun saveLiveRecording(pcmData: ByteArray): String {
            val file = File(recordingsDir, "live-${UUID.randomUUID()}.wav")
            val wavBytes =
                AudioEncoder.pcmToWav(
                    pcmData,
                    TranscribeAudioUseCase.SAMPLE_RATE,
                    TranscribeAudioUseCase.CHANNELS,
                    TranscribeAudioUseCase.BITS_PER_SAMPLE,
                )
            file.writeBytes(wavBytes)
            return file.absolutePath
        }

        fun read(path: String): ByteArray = File(path).readBytes()

        fun exists(path: String): Boolean = File(path).exists()

        fun delete(path: String?) {
            if (path.isNullOrBlank()) return
            File(path).delete()
        }

        fun listRecordingPaths(): List<String> =
            recordingsDir.listFiles()
                ?.filter { it.isFile }
                ?.map { it.absolutePath }
                ?: emptyList()

        companion object {
            private const val RECORDINGS_DIR = "recordings"
        }
    }
