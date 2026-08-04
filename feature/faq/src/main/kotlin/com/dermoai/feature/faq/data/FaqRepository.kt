package com.dermoai.feature.faq.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class FaqContent(
    val version: Int = 1,
    val entries: List<FaqEntry> = emptyList(),
)

@Serializable
data class FaqEntry(
    val id: String,
    val category: String,
    val question: String,
    val answer: String,
    val keywords: List<String> = emptyList(),
)

/**
 * Loads the curated FAQ bundle shipped as an asset and provides search.
 * Content is bundled (offline, medically reviewable) — no network needed.
 */
@Singleton
class FaqRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var cache: List<FaqEntry>? = null

    suspend fun load(): List<FaqEntry> = withContext(Dispatchers.IO) {
        cache ?: run {
            val raw = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            json.decodeFromString<FaqContent>(raw).entries.also { cache = it }
        }
    }

    /**
     * Token-based search: every whitespace-separated token must appear in the
     * question, answer, or keywords (case-insensitive).
     */
    fun search(entries: List<FaqEntry>, query: String): List<FaqEntry> {
        val tokens = query.trim().lowercase().split(WHITESPACE).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return entries
        return entries.filter { entry ->
            val haystack = buildString {
                append(entry.question.lowercase())
                append(' ')
                append(entry.answer.lowercase())
                entry.keywords.forEach { append(' ').append(it.lowercase()) }
            }
            tokens.all { it in haystack }
        }
    }

    companion object {
        const val ASSET_PATH = "faq/faq_content.json"
        private val WHITESPACE = Regex("\\s+")
    }
}
