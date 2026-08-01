package com.dermoai.feature.wellness

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dermoai.core.database.dao.JournalEntryDao
import com.dermoai.core.database.entity.JournalEntryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class JournalViewModel @Inject constructor(
    private val dao: JournalEntryDao,
) : ViewModel() {
    private val _entries = MutableStateFlow<List<JournalEntryEntity>>(emptyList())
    val entries: StateFlow<List<JournalEntryEntity>> = _entries.asStateFlow()

    fun load(userId: String) {
        viewModelScope.launch { dao.observeByUserId(userId).collect { _entries.value = it } }
    }

    fun save(userId: String, title: String, body: String, mood: Int) {
        viewModelScope.launch {
            dao.upsert(JournalEntryEntity(
                id = "journal_${UUID.randomUUID()}", userId = userId,
                title = title, body = body, mood = mood,
                createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis(),
            ))
        }
    }

    fun delete(id: String) { viewModelScope.launch { dao.deleteById(id) } }
}
