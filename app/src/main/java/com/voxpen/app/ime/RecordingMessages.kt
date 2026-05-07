package com.voxpen.app.ime

interface RecordingMessages {
    fun apiKeyNotConfigured(): String
    fun recordingTooShort(): String
    fun recordingTooQuiet(): String
    fun transcriptionFailed(message: String?): String

    object English : RecordingMessages {
        override fun apiKeyNotConfigured(): String = "API key not configured"
        override fun recordingTooShort(): String = "Recording was too short. Try speaking for a little longer."
        override fun recordingTooQuiet(): String = "Recording was too quiet. Try speaking closer to the microphone."
        override fun transcriptionFailed(message: String?): String = message ?: "Transcription failed"
    }
}
