package com.dermoai.feature.faq.data

import com.dermoai.core.data.preferences.UserPreferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Talks to the DeepSeek chat-completions API using the user's own API key
 * (stored on-device via [UserPreferencesDataStore]). The key is only ever sent
 * as the Authorization header and is never logged.
 */
@Singleton
class DeepSeekRepository @Inject constructor(
    private val prefs: UserPreferencesDataStore,
) : ChatRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val hasApiKey: Flow<Boolean> = prefs.deepSeekApiKey.map { !it.isNullOrBlank() }

    override suspend fun sendChat(messages: List<ChatMessage>): ChatMessage {
        val key = prefs.deepSeekApiKey.first()?.trim().orEmpty()
        if (key.isEmpty()) throw ChatException(ChatErrorKind.MISSING_KEY)

        val model = prefs.deepSeekModel.first().ifBlank { UserPreferencesDataStore.DEFAULT_DEEPSEEK_MODEL }
        val body = ChatCompletionRequest(
            model = model,
            messages = SYSTEM_MESSAGE + messages.map { ChatMessageDto(it.role.name.lowercase(), it.content) },
        )

        val request = Request.Builder()
            .url(CHAT_API_ENDPOINT)
            .header("Authorization", "Bearer $key")
            .post(json.encodeToString(ChatCompletionRequest.serializer(), body).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute()
            } catch (e: IOException) {
                throw ChatException(ChatErrorKind.NETWORK)
            }
        }
        // Read the body on IO too — body.string() blocks until the whole stream
        // arrives (up to the 90s read timeout) and must not run on the main thread.
        val (code, bodyText) = withContext(Dispatchers.IO) {
            response.use { resp -> resp.code to (resp.body?.string().orEmpty()) }
        }
        if (code !in 200..299) throw ChatException(chatErrorKindForStatus(code))
        val reply = parseChatResponse(bodyText, json)
        if (reply.isBlank()) throw ChatException(ChatErrorKind.EMPTY_RESPONSE)
        return ChatMessage(Role.ASSISTANT, reply)
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        val SYSTEM_MESSAGE = listOf(
            ChatMessageDto(role = "system", content = DERMOAI_SYSTEM_PROMPT),
        )
    }
}
