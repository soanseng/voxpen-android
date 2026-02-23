package com.voxink.app.billing

import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageLimiter
    @Inject
    constructor() {
        private var usage = DailyUsage(date = LocalDate.now())
        private var bonusVoiceInputs: Int = 0

        val currentUsage: DailyUsage
            get() {
                resetIfNewDay(LocalDate.now())
                return usage
            }

        fun canUseVoiceInput(): Boolean {
            resetIfNewDay(LocalDate.now())
            return usage.voiceInputCount < FREE_VOICE_INPUT_LIMIT + bonusVoiceInputs
        }

        fun canUseRefinement(): Boolean {
            resetIfNewDay(LocalDate.now())
            return usage.refinementCount < FREE_REFINEMENT_LIMIT
        }

        fun canUseFileTranscription(): Boolean {
            resetIfNewDay(LocalDate.now())
            return usage.fileTranscriptionCount < FREE_FILE_TRANSCRIPTION_LIMIT
        }

        fun incrementVoiceInput() {
            resetIfNewDay(LocalDate.now())
            usage = usage.copy(voiceInputCount = usage.voiceInputCount + 1)
        }

        fun incrementRefinement() {
            resetIfNewDay(LocalDate.now())
            usage = usage.copy(refinementCount = usage.refinementCount + 1)
        }

        fun incrementFileTranscription() {
            resetIfNewDay(LocalDate.now())
            usage = usage.copy(fileTranscriptionCount = usage.fileTranscriptionCount + 1)
        }

        fun addBonusVoiceInputs(count: Int) {
            bonusVoiceInputs += count
        }

        fun remainingVoiceInputs(): Int {
            resetIfNewDay(LocalDate.now())
            return (FREE_VOICE_INPUT_LIMIT + bonusVoiceInputs - usage.voiceInputCount).coerceAtLeast(0)
        }

        fun remainingRefinements(): Int {
            resetIfNewDay(LocalDate.now())
            return (FREE_REFINEMENT_LIMIT - usage.refinementCount).coerceAtLeast(0)
        }

        fun remainingFileTranscriptions(): Int {
            resetIfNewDay(LocalDate.now())
            return (FREE_FILE_TRANSCRIPTION_LIMIT - usage.fileTranscriptionCount).coerceAtLeast(0)
        }

        fun resetIfNewDay(today: LocalDate) {
            if (usage.date != today) {
                usage = DailyUsage(date = today)
                bonusVoiceInputs = 0
            }
        }

        companion object {
            const val FREE_VOICE_INPUT_LIMIT = 20
            const val FREE_REFINEMENT_LIMIT = 5
            const val FREE_FILE_TRANSCRIPTION_LIMIT = 2
            const val REWARDED_AD_BONUS = 5
        }
    }
