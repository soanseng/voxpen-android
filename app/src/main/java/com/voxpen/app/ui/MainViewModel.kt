package com.voxpen.app.ui

import androidx.lifecycle.ViewModel
import com.voxpen.app.data.local.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        preferencesManager: PreferencesManager,
    ) : ViewModel() {
        val onboardingCompleted: Flow<Boolean> = preferencesManager.onboardingCompletedFlow
    }
