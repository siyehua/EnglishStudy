package com.siyehua.egnlishstudy.data

import android.content.Context
import com.siyehua.egnlishstudy.data.wordform.WordMeaningRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ManualFavorite {

    const val LESSON_TITLE = "手动添加"

    fun looksLikeSingleWord(text: String): Boolean? {
        val value = text.trim()
        if (value.isEmpty()) return null
        return value.none { it.isWhitespace() } &&
            value.all { it.isLetter() || it == '\'' || it == '-' }
    }

    fun kindOf(text: String): String =
        if (looksLikeSingleWord(text) == true) "word" else "sentence"

    suspend fun translate(
        context: Context,
        text: String,
        meaningRepository: WordMeaningRepository = WordMeaningRepository(context)
    ): String = withContext(Dispatchers.IO) {
        val value = text.trim()
        if (value.isEmpty()) return@withContext ""
        if (looksLikeSingleWord(value) == true) {
            val response = meaningRepository.resolve(value, "")
            response.meanings
                .joinToString("；") { entry ->
                    if (entry.partOfSpeech.isBlank()) entry.meaning
                    else "${entry.partOfSpeech} ${entry.meaning}"
                }
                .ifBlank { response.sentenceChinese }
        } else {
            val response = meaningRepository.resolve(value.split(" ").first(), value)
            response.sentenceChinese
        }
    }

    suspend fun save(
        context: Context,
        text: String,
        translation: String
    ): Long = withContext(Dispatchers.IO) {
        val value = text.trim()
        ContentCacheDatabase(context.applicationContext).addFavorite(
            FavoriteRecord(
                kind = kindOf(value),
                text = value,
                audioUrl = null,
                contentId = "",
                translation = translation,
                lessonTitle = LESSON_TITLE
            )
        )
    }

    suspend fun add(context: Context, text: String): Long? = withContext(Dispatchers.IO) {
        val value = text.trim()
        if (value.isEmpty()) return@withContext null
        val translation = runCatching { translate(context, value) }.getOrDefault("")
        runCatching { save(context, value, translation) }.getOrNull()
    }
}
