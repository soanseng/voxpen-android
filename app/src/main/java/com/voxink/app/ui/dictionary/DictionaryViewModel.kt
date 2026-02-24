package com.voxink.app.ui.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxink.app.billing.BillingManager
import com.voxink.app.data.local.DictionaryEntry
import com.voxink.app.data.repository.DictionaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DictionaryViewModel
    @Inject
    constructor(
        private val repository: DictionaryRepository,
        private val billingManager: BillingManager,
    ) : ViewModel() {
        val entries: StateFlow<List<DictionaryEntry>> =
            repository.getAll()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        val count: StateFlow<Int> =
            repository.count()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

        val isPro: StateFlow<Boolean> =
            billingManager.proStatus
                .map { it.isPro }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

        val isLimitReached: StateFlow<Boolean> =
            combine(count, isPro) { c, pro ->
                !pro && c >= FREE_DICTIONARY_LIMIT
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

        private val _showDuplicateToast = MutableStateFlow(false)
        val showDuplicateToast: StateFlow<Boolean> = _showDuplicateToast.asStateFlow()

        fun addWord(word: String) {
            if (word.isBlank()) return
            viewModelScope.launch {
                if (isLimitReached.value) return@launch
                val result = repository.add(word)
                if (result == -1L) {
                    _showDuplicateToast.value = true
                }
            }
        }

        fun removeWord(entry: DictionaryEntry) {
            viewModelScope.launch {
                repository.remove(entry)
            }
        }

        fun dismissDuplicateToast() {
            _showDuplicateToast.value = false
        }

        companion object {
            const val FREE_DICTIONARY_LIMIT = 10
        }
    }
