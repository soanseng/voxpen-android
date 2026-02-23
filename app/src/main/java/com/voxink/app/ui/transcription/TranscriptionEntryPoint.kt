package com.voxink.app.ui.transcription

import com.voxink.app.data.local.ApiKeyManager
import com.voxink.app.domain.usecase.TranscribeFileUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TranscriptionEntryPoint {
    fun transcribeFileUseCase(): TranscribeFileUseCase

    fun apiKeyManager(): ApiKeyManager
}
