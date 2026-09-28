package com.siyehua.egnlishstudy.data.wordform

import android.content.Context
import com.siyehua.egnlishstudy.data.AppLog
import com.siyehua.egnlishstudy.data.ContentCacheDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WordFormRepository(
    context: Context,
    private val apiClient: WordFormApiClient = WordFormApiClient()
) {
    private val database = ContentCacheDatabase(context.applicationContext)

    suspend fun resolve(surface: String, sentence: String): WordFormResponse =
        withContext(Dispatchers.IO) {
            val normalized = surface.normalizeWordSurface()
            database.loadWordForm(normalized, sentence)?.let { cached ->
                AppLog.log("wordform", "HIT exact word=$normalized sent=${sentence.tagSent()}")
                return@withContext cached.copy(surface = surface)
            }

            database.loadLatestWordFormByWord(normalized)?.let { cached ->
                if (sentence.isNotBlank()) database.upsertWordForm(cached, sentence)
                AppLog.log("wordform", "HIT word-level word=$normalized sent=${sentence.tagSent()}")
                return@withContext cached.copy(surface = surface)
            }

            AppLog.log("wordform", "MISS -> network word=$normalized sent=${sentence.tagSent()}")
            val response = apiClient.resolveWordForm(
                surface = surface,
                sentence = sentence
            )
            database.upsertWordForm(response, sentence)
            response
        }
}
