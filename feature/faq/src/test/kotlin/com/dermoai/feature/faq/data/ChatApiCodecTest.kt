package com.dermoai.feature.faq.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatApiCodecTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parseChatResponse extracts assistant content`() {
        val body = """
            {"id":"chatcmpl-1","choices":[{"index":0,"message":{"role":"assistant","content":"Wear SPF 30 daily."},"finish_reason":"stop"}],"usage":{}}
        """.trimIndent()
        assertEquals("Wear SPF 30 daily.", parseChatResponse(body, json))
    }

    @Test
    fun `parseChatResponse tolerates extra fields and missing usage`() {
        val body = """{"id":"x","object":"chat.completion","choices":[{"message":{"role":"assistant","content":"Hi"},"index":0}]}"""
        assertEquals("Hi", parseChatResponse(body, json))
    }

    @Test
    fun `parseChatResponse returns empty for no choices`() {
        assertTrue(parseChatResponse("""{"choices":[]}""", json).isEmpty())
    }

    @Test
    fun `parseChatResponse returns empty for null message`() {
        assertTrue(parseChatResponse("""{"choices":[{"message":null}]}""", json).isEmpty())
    }

    @Test
    fun `status maps to error kinds`() {
        assertEquals(ChatErrorKind.INVALID_KEY, chatErrorKindForStatus(401))
        assertEquals(ChatErrorKind.INVALID_KEY, chatErrorKindForStatus(403))
        assertEquals(ChatErrorKind.INSUFFICIENT_BALANCE, chatErrorKindForStatus(402))
        assertEquals(ChatErrorKind.RATE_LIMITED, chatErrorKindForStatus(429))
        assertEquals(ChatErrorKind.SERVER, chatErrorKindForStatus(500))
        assertEquals(ChatErrorKind.SERVER, chatErrorKindForStatus(503))
        assertEquals(ChatErrorKind.UNKNOWN, chatErrorKindForStatus(400))
        assertEquals(ChatErrorKind.UNKNOWN, chatErrorKindForStatus(418))
    }

    @Test
    fun `request serializes with expected model and roles`() {
        val body = ChatCompletionRequest(
            model = "deepseek-chat",
            messages = listOf(
                ChatMessageDto("system", "sys"),
                ChatMessageDto("user", "hello"),
            ),
        )
        // The repository uses encodeDefaults=true so stream/max_tokens are explicit.
        val encoded = Json { encodeDefaults = true }.encodeToString(ChatCompletionRequest.serializer(), body)
        assertTrue(encoded.contains("\"model\":\"deepseek-chat\""))
        assertTrue(encoded.contains("\"role\":\"user\""))
        assertTrue(encoded.contains("\"stream\":false"))
        assertTrue(encoded.contains("\"max_tokens\":1024"))
    }

    @Test
    fun `system prompt knows the app, privacy, and dermatology`() {
        // Normalize whitespace: the raw string has line breaks mid-phrase.
        val prompt = DERMOAI_SYSTEM_PROMPT.replace(Regex("\\s+"), " ").lowercase()
        // App identity & features
        assertTrue(prompt.contains("dermoai skin-health app"))
        assertTrue(prompt.contains("severity tier"))
        assertTrue(prompt.contains("skin profile"))
        assertTrue(prompt.contains("timeline"))
        assertTrue(prompt.contains("skinnmind") || prompt.contains("check-in"))
        assertTrue(prompt.contains("doctor report") || prompt.contains("pdf"))
        assertTrue(prompt.contains("find a dermatologist"))
        // Privacy: on-device storage, nothing uploaded
        assertTrue(prompt.contains("stored only on the user's device"))
        assertTrue(prompt.contains("nothing is uploaded"))
        // Medical safety rails
        assertTrue(prompt.contains("not a doctor"))
        assertTrue(prompt.contains("abcde"))
        assertTrue(prompt.contains("consult a dermatologist") || prompt.contains("see a dermatologist"))
        // Style guidance
        assertTrue(prompt.contains("do not invent facts"))
    }

    @Test
    fun `system prompt fits well within the model context`() {
        // Even the longest conceivable conversation history is dominated by the
        // prompt; assert it stays small enough to leave room for 50+ exchanges.
        assertTrue(DERMOAI_SYSTEM_PROMPT.length < 8_000)
    }
}
