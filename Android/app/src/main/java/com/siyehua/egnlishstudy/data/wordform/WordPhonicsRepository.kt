package com.siyehua.egnlishstudy.data.wordform

import android.content.Context
import com.siyehua.egnlishstudy.data.ContentCacheDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WordPhonicsRepository(
    context: Context,
    private val apiClient: WordFormApiClient = WordFormApiClient()
) {
    private val database = ContentCacheDatabase(context.applicationContext)

    suspend fun resolve(word: String, sentence: String, ipa: String? = null): WordPhonicsResponse =
        withContext(Dispatchers.IO) {
            val normalized = word.normalizeWordSurface()
            database.loadWordPhonics(normalized = normalized, sentence = sentence, ipa = ipa)?.let { cached ->
                return@withContext cached.copy(word = word)
            }

            val response = apiClient.resolveWordPhonics(
                word = word,
                sentence = sentence,
                ipa = ipa
            )
            if (response.found) {
                database.upsertWordPhonics(phonics = response, sentence = sentence, ipa = ipa)
            }
            response
        }
}
