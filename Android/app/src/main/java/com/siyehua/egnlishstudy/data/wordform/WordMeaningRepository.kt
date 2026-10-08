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

    suspend fun resolve(
        word: String,
        sentence: String,
        allowWordLevelFallback: Boolean = true
    ): WordMeaningResponse =
        withContext(Dispatchers.IO) {
            val normalized = word.normalizeWordSurface()
            val tag = "meaning"
            database.loadWordMeaning(normalized = normalized, sentence = sentence)?.let { cached ->
                AppLog.log(tag, "HIT exact word=$normalized sent=${sentence.tagSent()}")
                return@withContext cached.copy(word = word)
            }

            if (allowWordLevelFallback) {
                database.loadLatestWordMeaningByWord(normalized)?.let { cached ->
                    AppLog.log(tag, "HIT word-level word=$normalized sent=${sentence.tagSent()}")
                    // sentenceChinese 是整句翻译，与具体句子绑定，不能随词级缓存跨句子复用。
                    // 这里只复用单词释义（meanings），整句翻译清空，避免张冠李戴。
                    return@withContext cached.copy(word = word, sentenceChinese = "")
                }
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