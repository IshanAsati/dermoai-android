package com.dermoai.feature.faq

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dermoai.feature.faq.data.ChatErrorKind
import com.dermoai.feature.faq.data.ChatException
import com.dermoai.feature.faq.data.ChatMessage
import com.dermoai.feature.faq.data.ChatRepository
import com.dermoai.feature.faq.data.Role
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI-facing error summary for the chat. */
data class ChatUiError(val kind: ChatErrorKind)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val _error = MutableStateFlow<ChatUiError?>(null)
    val error: StateFlow<ChatUiError?> = _error.asStateFlow()

    private val _hasApiKey = MutableStateFlow(false)
    val hasApiKey: StateFlow<Boolean> = _hasApiKey.asStateFlow()

    private var lastUserText: String? = null

    init {
        viewModelScope.launch { repository.hasApiKey.collect { _hasApiKey.value = it } }
    }

    /** Sends the user's text and appends the assistant's reply. */
    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _sending.value) return
        lastUserText = trimmed
        viewModelScope.launch {
            _sending.value = true
            _error.value = null
            _messages.value = _messages.value + ChatMessage(Role.USER, trimmed)
            try {
                val reply = repository.sendChat(_messages.value)
                _messages.value = _messages.value + reply
            } catch (e: ChatException) {
                _error.value = ChatUiError(e.kind)
            } catch (e: Exception) {
                _error.value = ChatUiError(ChatErrorKind.UNKNOWN)
            }
            _sending.value = false
        }
    }

    /** Re-sends the last user message after an error. */
    fun retryLast() {
        lastUserText?.let { send(it) }
    }

    fun clear() {
        _messages.value = emptyList()
        _error.value = null
        lastUserText = null
    }
}
