package com.voxink.app.billing

import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageLimiter
    @Inject
    constructor() {
        private var usage = DailyUsage(date = LocalDate.now())

        val currentUsage: DailyUsage
            get() {
                resetIfNewDay(LocalDate.now())
                return usage
            }

        fun canUseVoiceInput(): Boolean {
            resetIfNewDay(LocalDate.now())
            return usage.voiceInputCount < FREE_VOICE_INPUT_LIMIT
        }

        fun canUseRefinement(): Boolean {
            resetIfNewDay(LocalDate.now())
            return usage.refinementCount < FREE_REFINEMENT_LIMIT
        }

        fun canTranscribeFile(durationSeconds: Int): Boolean {
            resetIfNewDay(LocalDate.now())
            return usage.fileTranscriptionSeconds + durationSeconds <= FREE_FILE_TRANSCRIPTION_DURATION
        }

        fun incrementVoiceInput() {
            resetIfNewDay(LocalDate.now())
            usage = usage.copy(voiceInputCount = usage.voiceInputCount + 1)
        }

        fun incrementRefinement() {
            resetIfNewDay(LocalDate.now())
            usage = usage.copy(refinementCount = usage.refinementCount + 1)
        }

        fun addFileTranscriptionDuration(seconds: Int) {
            resetIfNewDay(LocalDate.now())
            usage = usage.copy(fileTranscriptionSeconds = usage.fileTranscriptionSeconds + seconds)
        }

        fun remainingVoiceInputs(): Int {
            resetIfNewDay(LocalDate.now())
            return (FREE_VOICE_INPUT_LIMIT - usage.voiceInputCount).coerceAtLeast(0)
        }

        fun remainingRefinements(): Int {
            resetIfNewDay(LocalDate.now())
            return (FREE_REFINEMENT_LIMIT - usage.refinementCount).coerceAtLeast(0)
        }

        fun remainingFileTranscriptionSeconds(): Int {
            resetIfNewDay(LocalDate.now())
            return (FREE_FILE_TRANSCRIPTION_DURATION - usage.fileTranscriptionSeconds).coerceAtLeast(0)
        }

        fun resetIfNewDay(today: LocalDate) {
            if (usage.date != today) {
                usage = DailyUsage(date = today)
            }
        }

        companion object {
            const val FREE_VOICE_INPUT_LIMIT = 15
            const val FREE_REFINEMENT_LIMIT = 3
            const val FREE_FILE_TRANSCRIPTION_DURATION = 300 // 5 minutes in seconds
        }
    }
