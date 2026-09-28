package com.siyehua.egnlishstudy.data.wordform

import android.content.Context
import com.siyehua.egnlishstudy.data.AppLog
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
            val tag = "meaning"
            database.loadWordMeaning(normalized = normalized, sentence = sentence)?.let { cached ->
                AppLog.log(tag, "HIT exact word=$normalized sent=${sentence.tagSent()}")
                return@withContext cached.copy(word = word)
            }

            database.loadLatestWordMeaningByWord(normalized)?.let { cached ->
                if (sentence.isNotBlank()) {
                    database.upsertWordMeaning(meaning = cached, sentence = sentence)
                }
                AppLog.log(tag, "HIT word-level word=$normalized sent=${sentence.tagSent()}")
                return@withContext cached.copy(word = word)
            }

            AppLog.log(tag, "MISS -> network word=$normalized sent=${sentence.tagSent()}")
            val response = apiClient.resolveWordMeaning(
                word = word,
                sentence = sentence
            )
            AppLog.log("meaning", "MISS -> network word=$normalized sent=${sentence.tagSent()}")
            if (response.found) {
                runCatching { database.upsertWordMeaning(meaning = response, sentence = sentence) }
            }
            response
        }
}


internal fun String.tagSent(): String = if (isBlank()) "\"\"" else "\"${take(28)}\""