package com.siyehua.egnlishstudy.data.wordform

import android.content.Context
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
                if (cached.phonetic.isNullOrBlank()) {
                    database.deleteWordPronunciation(normalized)
                } else {
                    return@withContext cached.copy(word = word)
                }
            }

            val response = apiClient.resolveWordPronunciation(word)
            if (!response.phonetic.isNullOrBlank()) {
                database.upsertWordPronunciation(response)
            } else {
                database.deleteWordPronunciation(response.normalized)
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
