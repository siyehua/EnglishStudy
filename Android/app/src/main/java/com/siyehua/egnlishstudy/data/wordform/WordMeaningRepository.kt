package com.siyehua.egnlishstudy.data.wordform

import android.content.Context
import com.siyehua.egnlishstudy.data.ContentCacheDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WordMeaningRepository(
    context: Context,
    private val apiClient: WordFormApiClient = WordFormApiClient()
) {
    private val database = ContentCacheDatabase(context.applicationContext)

    suspend fun resolve(word: String, sentence: String): WordMeaningResponse =
        withContext(Dispatchers.IO) {
            val normalized = word.normalizeWordSurface()
            database.loadWordMeaning(normalized = normalized, sentence = sentence)?.let { cached ->
                return@withContext cached.copy(word = word)
            }

            val response = apiClient.resolveWordMeaning(
                word = word,
                sentence = sentence
            )
            if (response.found) {
                database.upsertWordMeaning(meaning = response, sentence = sentence)
            }
            response
        }
}
