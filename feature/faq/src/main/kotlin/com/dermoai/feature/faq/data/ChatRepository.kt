package com.dermoai.feature.faq.data

import kotlinx.coroutines.flow.Flow

/**
 * Backend-agnostic interface for the AI assistant. Implementations talk to a
 * chat-completions API (DeepSeek by default); unit tests use a fake.
 */
interface ChatRepository {

    /** Whether a user-supplied API key is currently configured. */
    val hasApiKey: Flow<Boolean>

    /**
     * Sends the full conversation history and returns the assistant's reply.
     * @throws ChatException with a user-facing [ChatErrorKind] on failure.
     */
    suspend fun sendChat(messages: List<ChatMessage>): ChatMessage
}
