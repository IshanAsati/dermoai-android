package com.dermoai.feature.faq

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dermoai.feature.faq.data.FaqEntry
import com.dermoai.feature.faq.data.FaqRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FaqViewModel @Inject constructor(
    private val repository: FaqRepository,
) : ViewModel() {

    private val _entries = MutableStateFlow<List<FaqEntry>>(emptyList())
    val entries: StateFlow<List<FaqEntry>> = _entries.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = false
            runCatching { repository.load() }
                .onSuccess { _entries.value = it }
                .onFailure { _error.value = true }
            _loading.value = false
        }
    }

    /** Token-based search over the loaded entries (see [FaqRepository.search]). */
    fun search(entries: List<FaqEntry>, query: String): List<FaqEntry> =
        repository.search(entries, query)
}
