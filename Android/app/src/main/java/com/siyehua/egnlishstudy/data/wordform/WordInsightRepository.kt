package com.siyehua.egnlishstudy.data.wordform

import android.content.Context
import com.siyehua.egnlishstudy.data.AppLog
import com.siyehua.egnlishstudy.data.ContentCacheDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WordInsightRepository(
    context: Context,
    private val apiClient: WordFormApiClient = WordFormApiClient()
) {
    private val database = ContentCacheDatabase(context.applicationContext)

    suspend fun resolve(word: String, sentence: String): WordInsightResponse? =
        withContext(Dispatchers.IO) {
            val normalized = word.normalizeWordSurface()
            if (normalized.isBlank()) return@withContext null

            val cachedPronunciation = database.loadWordPronunciation(normalized)
            val cachedForm = database.loadWordForm(normalized, sentence)
            val cachedMeaning = database.loadWordMeaning(normalized, sentence)
            val cachedPhonics = database.loadWordPhonics(
                normalized = normalized,
                sentence = sentence,
                ipa = cachedPronunciation?.phonetic
            )

            if (
                cachedPronunciation?.phonetic != null &&
                cachedForm != null &&
                cachedMeaning != null &&
                cachedPhonics != null
            ) {
                AppLog.log("insight", "aggregate from local cache word=$normalized")
                return@withContext WordInsightResponse(
                    wordForm = cachedForm.copy(surface = word),
                    pronunciation = cachedPronunciation.copy(word = word),
                    phonics = cachedPhonics.copy(word = word),
                    meaning = cachedMeaning.copy(word = word)
                )
            }

            val response = runCatching {
                apiClient.resolveWordInsight(word = normalized, sentence = sentence)
            }.getOrElse { error ->
                AppLog.log("insight", "aggregate request failed word=$normalized ${error.message}")
                return@withContext null
            }

            runCatching {
                database.upsertWordPronunciation(response.pronunciation)
                database.upsertWordForm(response.wordForm, sentence)
                if (response.meaning.found) {
                    database.upsertWordMeaning(response.meaning, sentence)
                }
                if (response.phonics.found) {
                    database.upsertWordPhonics(
                        phonics = response.phonics,
                        sentence = sentence,
                        ipa = response.pronunciation.phonetic
                    )
                }
            }
            response
        }
}
