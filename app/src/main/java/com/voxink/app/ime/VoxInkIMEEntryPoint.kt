package com.voxink.app.ime

import com.voxink.app.data.local.ApiKeyManager
import com.voxink.app.data.local.PreferencesManager
import com.voxink.app.domain.usecase.TranscribeAudioUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface VoxInkIMEEntryPoint {
    fun transcribeAudioUseCase(): TranscribeAudioUseCase

    fun apiKeyManager(): ApiKeyManager

    fun preferencesManager(): PreferencesManager
}
