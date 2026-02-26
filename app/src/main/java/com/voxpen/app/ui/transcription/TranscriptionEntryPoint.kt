package com.voxpen.app.ui.transcription

import com.voxpen.app.data.local.ApiKeyManager
import com.voxpen.app.data.local.PreferencesManager
import com.voxpen.app.data.repository.DictionaryRepository
import com.voxpen.app.domain.usecase.TranscribeFileUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TranscriptionEntryPoint {
    fun transcribeFileUseCase(): TranscribeFileUseCase

    fun apiKeyManager(): ApiKeyManager

    fun preferencesManager(): PreferencesManager

    fun dictionaryRepository(): DictionaryRepository
}
