package com.voxpen.app.billing

import java.time.LocalDate

data class DailyUsage(
    val date: LocalDate,
    val voiceInputCount: Int = 0,
    val refinementCount: Int = 0,
    val fileTranscriptionSeconds: Int = 0,
)
