package com.voxpen.app.ime

import com.voxpen.app.billing.ProStatusResolver
import com.voxpen.app.billing.UsageLimiter
import com.voxpen.app.data.local.ApiKeyManager
import com.voxpen.app.data.local.PreferencesManager
import com.voxpen.app.data.repository.DictionaryRepository
import com.voxpen.app.domain.usecase.EditTextUseCase
import com.voxpen.app.domain.usecase.RefineTextUseCase
import com.voxpen.app.domain.usecase.TranscribeAudioUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface VoxPenIMEEntryPoint {
    fun transcribeAudioUseCase(): TranscribeAudioUseCase

    fun refineTextUseCase(): RefineTextUseCase

    fun editTextUseCase(): EditTextUseCase

    fun apiKeyManager(): ApiKeyManager

    fun preferencesManager(): PreferencesManager

    fun dictionaryRepository(): DictionaryRepository

    fun usageLimiter(): UsageLimiter

    fun proStatusResolver(): ProStatusResolver
}
