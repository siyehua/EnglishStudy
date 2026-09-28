package com.siyehua.egnlishstudy.data.wordform

import android.content.Context
import com.siyehua.egnlishstudy.data.AppLog
import com.siyehua.egnlishstudy.data.ContentCacheDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WordPronunciationRepository(
    context: Context,
    private val apiClient: WordFormApiClient = WordFormApiClient()
) {
    private val database = ContentCacheDatabase(context.applicationContext)

    suspend fun resolve(word: String): WordPronunciationResponse =
        withContext(Dispatchers.IO) {
            val normalized = word.normalizeWordSurface()
            database.loadWordPronunciation(normalized)?.let { cached ->
                if (!cached.phonetic.isNullOrBlank()) {
                    AppLog.log("pronunciation", "HIT word=$normalized ipa=${cached.phonetic}")
                    return@withContext cached.copy(word = word)
                }
                val updatedAt = database.loadWordPronunciationUpdatedAt(normalized) ?: 0L
                if (System.currentTimeMillis() - updatedAt < NEGATIVE_TTL_MS) {
                    AppLog.log("pronunciation", "HIT negative word=$normalized")
                    return@withContext cached.copy(word = word)
                }
                database.deleteWordPronunciation(normalized)
            }

            AppLog.log("pronunciation", "MISS -> network word=$normalized")
            val response = runCatching { apiClient.resolveWordPronunciation(word) }.getOrElse { error ->
                AppLog.log("pronunciation", "FAILED word=$normalized " + error.message)
                return@withContext WordPronunciationResponse(
                    word = word,
                    normalized = normalized,
                    phonetic = null,
                    audioUrl = null,
                    source = "unavailable",
                    found = false
                )
            }
            if (!response.phonetic.isNullOrBlank()) {
                runCatching { database.upsertWordPronunciation(response) }
            } else {
                runCatching { database.upsertWordPronunciation(response) }
            }
            response
        }
}

internal fun String.normalizeWordSurface(): String =
    trim()
        .lowercase()
        .replace('\u2019', '\'')
        .let { normalized ->
            Regex("[a-z]+(?:['-][a-z]+)*")
                .find(normalized)
                ?.value
                .orEmpty()
        }

private const val NEGATIVE_TTL_MS = 10 * 60 * 1000L
