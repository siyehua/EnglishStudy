package com.siyehua.egnlishstudy.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.siyehua.egnlishstudy.model.Article
import com.siyehua.egnlishstudy.model.Blog
import com.siyehua.egnlishstudy.model.Content
import com.siyehua.egnlishstudy.model.ContentLevel
import com.siyehua.egnlishstudy.model.ContentType
import com.siyehua.egnlishstudy.model.Dialogue
import com.siyehua.egnlishstudy.model.DialogueLine
import com.siyehua.egnlishstudy.model.News
import com.siyehua.egnlishstudy.data.wordform.WordFormRelation
import com.siyehua.egnlishstudy.data.wordform.WordFormResponse
import com.siyehua.egnlishstudy.data.wordform.WordFormVariantGroup
import com.siyehua.egnlishstudy.data.wordform.WordMeaningEntry
import com.siyehua.egnlishstudy.data.wordform.WordMeaningResponse
import com.siyehua.egnlishstudy.data.wordform.WordPhonicsChunk
import com.siyehua.egnlishstudy.data.wordform.WordPhonicsResponse
import com.siyehua.egnlishstudy.data.wordform.WordPhonicsSegment
import com.siyehua.egnlishstudy.data.wordform.WordPronunciationResponse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

class ContentCacheDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_CONTENT (
                $COLUMN_ID TEXT PRIMARY KEY,
                $COLUMN_TITLE TEXT NOT NULL,
                $COLUMN_TYPE TEXT NOT NULL,
                $COLUMN_LEVEL TEXT NOT NULL,
                $COLUMN_BODY TEXT NOT NULL,
                $COLUMN_AUTHOR TEXT,
                $COLUMN_SOURCE TEXT,
                $COLUMN_DATE TEXT,
                $COLUMN_UPDATED_AT INTEGER NOT NULL,
                $COLUMN_CACHE_ORDER INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX index_content_type ON $TABLE_CONTENT($COLUMN_TYPE)"
        )
        db.execSQL(
            "CREATE INDEX index_content_level ON $TABLE_CONTENT($COLUMN_LEVEL)"
        )
        createTtsTable(db)
        createWordFormTable(db)
        createWordPronunciationTable(db)
        createWordMeaningTable(db)
        createWordPhonicsTable(db)
        createWordAudioTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                "ALTER TABLE $TABLE_CONTENT ADD COLUMN $COLUMN_LEVEL TEXT NOT NULL DEFAULT '${ContentLevel.B1.name}'"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_content_level ON $TABLE_CONTENT($COLUMN_LEVEL)"
            )
        }
        if (oldVersion < 3) {
            db.execSQL(
                "ALTER TABLE $TABLE_CONTENT ADD COLUMN $COLUMN_CACHE_ORDER INTEGER NOT NULL DEFAULT 0"
            )
        }
        if (oldVersion < 4) {
            createTtsTable(db)
        }
        if (oldVersion < 5) {
            createWordFormTable(db)
            createWordAudioTable(db)
        }
        if (oldVersion < 6) {
            createWordPronunciationTable(db)
        }
        if (oldVersion < 7) {
            createWordMeaningTable(db)
        }
        if (oldVersion < 8) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_WORD_MEANING_CACHE")
            createWordMeaningTable(db)
        }
        if (oldVersion < 9) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_WORD_MEANING_CACHE")
            createWordMeaningTable(db)
        }
        if (oldVersion < 10) {
            db.execSQL(
                "DELETE FROM $TABLE_WORD_PRONUNCIATION_CACHE " +
                    "WHERE $COLUMN_WORD_PRONUNCIATION_PHONETIC IS NULL " +
                    "OR TRIM($COLUMN_WORD_PRONUNCIATION_PHONETIC) = ''"
            )
        }
        if (oldVersion < 11) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_WORD_FORM_CACHE")
            createWordFormTable(db)
        }
        if (oldVersion < 12) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_WORD_FORM_CACHE")
            createWordFormTable(db)
        }
        if (oldVersion < 13) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_WORD_FORM_CACHE")
            createWordFormTable(db)
        }
        if (oldVersion < 14) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_WORD_FORM_CACHE")
            createWordFormTable(db)
        }
        if (oldVersion < 15) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_WORD_FORM_CACHE")
            createWordFormTable(db)
        }
        if (oldVersion < 16) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_WORD_FORM_CACHE")
            createWordFormTable(db)
        }
        if (oldVersion < 17) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_WORD_FORM_CACHE")
            createWordFormTable(db)
        }
        if (oldVersion < 18) {
            db.execSQL("DELETE FROM $TABLE_CONTENT")
        }
        if (oldVersion < 19) {
            db.execSQL("DELETE FROM $TABLE_TTS_AUDIO")
        }
        if (oldVersion < 20) {
            createWordPhonicsTable(db)
        }
        if (oldVersion < 21) {
            db.execSQL("DELETE FROM $TABLE_TTS_AUDIO")
        }
    }

    private fun createTtsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_TTS_AUDIO (
                $COLUMN_TTS_CONTENT_ID TEXT NOT NULL,
                $COLUMN_TTS_SENTENCE_INDEX INTEGER NOT NULL,
                $COLUMN_TTS_SENTENCE_TEXT TEXT NOT NULL,
                $COLUMN_TTS_SENTENCE_HASH TEXT NOT NULL,
                $COLUMN_TTS_FILE_PATH TEXT NOT NULL,
                $COLUMN_TTS_CREATED_AT INTEGER NOT NULL,
                PRIMARY KEY($COLUMN_TTS_CONTENT_ID, $COLUMN_TTS_SENTENCE_INDEX)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_tts_content_id ON $TABLE_TTS_AUDIO($COLUMN_TTS_CONTENT_ID)"
        )
    }

    private fun createWordFormTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_WORD_FORM_CACHE (
                $COLUMN_WORD_FORM_CACHE_KEY TEXT PRIMARY KEY,
                $COLUMN_WORD_FORM_NORMALIZED TEXT NOT NULL,
                $COLUMN_WORD_FORM_SURFACE TEXT NOT NULL,
                $COLUMN_WORD_FORM_HEADWORD TEXT NOT NULL,
                $COLUMN_WORD_FORM_PRONUNCIATION_TARGET TEXT NOT NULL,
                $COLUMN_WORD_FORM_RELATION_TYPE TEXT,
                $COLUMN_WORD_FORM_RELATION_TARGET TEXT,
                $COLUMN_WORD_FORM_RELATION_LABEL TEXT,
                $COLUMN_WORD_FORM_EXPANSION TEXT,
                $COLUMN_WORD_FORM_CURRENT_POS TEXT,
                $COLUMN_WORD_FORM_CURRENT_POS_LABEL TEXT NOT NULL DEFAULT '',
                $COLUMN_WORD_FORM_VARIANT_GROUPS_JSON TEXT NOT NULL DEFAULT '[]',
                $COLUMN_WORD_FORM_CONFIDENCE TEXT NOT NULL,
                $COLUMN_WORD_FORM_SOURCE TEXT NOT NULL,
                $COLUMN_WORD_FORM_UPDATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun createWordAudioTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_WORD_AUDIO_CACHE (
                $COLUMN_WORD_AUDIO_KEY TEXT PRIMARY KEY,
                $COLUMN_WORD_AUDIO_TEXT TEXT NOT NULL,
                $COLUMN_WORD_AUDIO_HASH TEXT NOT NULL,
                $COLUMN_WORD_AUDIO_FILE_PATH TEXT NOT NULL,
                $COLUMN_WORD_AUDIO_CREATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun createWordPronunciationTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_WORD_PRONUNCIATION_CACHE (
                $COLUMN_WORD_PRONUNCIATION_NORMALIZED TEXT PRIMARY KEY,
                $COLUMN_WORD_PRONUNCIATION_WORD TEXT NOT NULL,
                $COLUMN_WORD_PRONUNCIATION_PHONETIC TEXT,
                $COLUMN_WORD_PRONUNCIATION_AUDIO_URL TEXT,
                $COLUMN_WORD_PRONUNCIATION_SOURCE TEXT NOT NULL,
                $COLUMN_WORD_PRONUNCIATION_FOUND INTEGER NOT NULL,
                $COLUMN_WORD_PRONUNCIATION_UPDATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun createWordMeaningTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_WORD_MEANING_CACHE (
                $COLUMN_WORD_MEANING_CACHE_KEY TEXT PRIMARY KEY,
                $COLUMN_WORD_MEANING_NORMALIZED TEXT NOT NULL,
                $COLUMN_WORD_MEANING_WORD TEXT NOT NULL,
                $COLUMN_WORD_MEANING_SENTENCE_HASH TEXT NOT NULL,
                $COLUMN_WORD_MEANING_ITEMS_JSON TEXT NOT NULL,
                $COLUMN_WORD_MEANING_SENTENCE_CHINESE TEXT NOT NULL DEFAULT '',
                $COLUMN_WORD_MEANING_SOURCE TEXT NOT NULL,
                $COLUMN_WORD_MEANING_FOUND INTEGER NOT NULL,
                $COLUMN_WORD_MEANING_UPDATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun createWordPhonicsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_WORD_PHONICS_CACHE (
                $COLUMN_WORD_PHONICS_CACHE_KEY TEXT PRIMARY KEY,
                $COLUMN_WORD_PHONICS_NORMALIZED TEXT NOT NULL,
                $COLUMN_WORD_PHONICS_WORD TEXT NOT NULL,
                $COLUMN_WORD_PHONICS_SENTENCE_HASH TEXT NOT NULL,
                $COLUMN_WORD_PHONICS_IPA_KEY TEXT NOT NULL,
                $COLUMN_WORD_PHONICS_IPA TEXT,
                $COLUMN_WORD_PHONICS_PHONEMES_JSON TEXT NOT NULL DEFAULT '[]',
                $COLUMN_WORD_PHONICS_SEGMENTS_JSON TEXT NOT NULL DEFAULT '[]',
                $COLUMN_WORD_PHONICS_CHUNKS_JSON TEXT NOT NULL DEFAULT '[]',
                $COLUMN_WORD_PHONICS_NOTE TEXT NOT NULL DEFAULT '',
                $COLUMN_WORD_PHONICS_VERIFICATION TEXT NOT NULL DEFAULT '',
                $COLUMN_WORD_PHONICS_SOURCE TEXT NOT NULL,
                $COLUMN_WORD_PHONICS_FOUND INTEGER NOT NULL,
                $COLUMN_WORD_PHONICS_MESSAGE TEXT NOT NULL DEFAULT '',
                $COLUMN_WORD_PHONICS_UPDATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    fun replaceAll(content: List<Content>) {
        writableDatabase.use { db ->
            db.beginTransaction()
            try {
                db.delete(TABLE_CONTENT, null, null)
                content.forEachIndexed { index, item ->
                    db.insertWithOnConflict(
                        TABLE_CONTENT,
                        null,
                        item.toContentValues(index),
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    fun upsertAll(content: List<Content>) {
        writableDatabase.use { db ->
            db.beginTransaction()
            try {
                content.forEachIndexed { index, item ->
                    db.insertWithOnConflict(
                        TABLE_CONTENT,
                        null,
                        item.toContentValues(index),
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    fun queryContent(
        types: Set<ContentType>,
        levels: Set<ContentLevel>,
        sources: Set<String>,
        limit: Int,
        offset: Int
    ): List<Content> =
        readableDatabase.use { db ->
            val query = buildFilterQuery(types, levels, sources)
            db.query(
                TABLE_CONTENT,
                null,
                query.selection,
                query.selectionArgs,
                null,
                null,
                "$COLUMN_CACHE_ORDER ASC, $COLUMN_UPDATED_AT DESC",
                "${limit.coerceAtLeast(1)} OFFSET ${offset.coerceAtLeast(0)}"
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        cursor.toContent()?.let(::add)
                    }
                }
            }
        }

    fun countContent(types: Set<ContentType>, levels: Set<ContentLevel>, sources: Set<String>): Int =
        readableDatabase.use { db ->
            val query = buildFilterQuery(types, levels, sources)
            db.rawQuery(
                "SELECT COUNT(*) FROM $TABLE_CONTENT${query.whereClauseForRawQuery()}",
                query.selectionArgs
            ).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
        }

    fun countByType(levels: Set<ContentLevel>, sources: Set<String>): Map<ContentType, Int> =
        countByColumn(
            column = COLUMN_TYPE,
            types = emptySet(),
            levels = levels,
            sources = sources
        ).mapNotNullKeys { value ->
            runCatching { ContentType.valueOf(value) }.getOrNull()
        }

    fun countByLevel(types: Set<ContentType>, sources: Set<String>): Map<ContentLevel, Int> =
        countByColumn(
            column = COLUMN_LEVEL,
            types = types,
            levels = emptySet(),
            sources = sources
        ).mapNotNullKeys { value ->
            runCatching { ContentLevel.valueOf(value) }.getOrNull()
        }

    fun countBySource(types: Set<ContentType>, levels: Set<ContentLevel>): Map<String, Int> =
        countByColumn(
            column = COLUMN_SOURCE,
            types = types,
            levels = levels,
            sources = emptySet()
        )

    fun loadAll(): List<Content> =
        readableDatabase.use { db ->
            db.query(
                TABLE_CONTENT,
                null,
                null,
                null,
                null,
                null,
                "$COLUMN_CACHE_ORDER ASC, $COLUMN_UPDATED_AT DESC"
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        cursor.toContent()?.let(::add)
                    }
                }
            }
        }

    fun isEmpty(): Boolean =
        readableDatabase.use { db ->
            db.rawQuery("SELECT COUNT(*) FROM $TABLE_CONTENT", null).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0) == 0
            }
        }

    fun loadTtsAudio(contentId: String): List<TtsAudioRecord> =
        readableDatabase.use { db ->
            db.query(
                TABLE_TTS_AUDIO,
                null,
                "$COLUMN_TTS_CONTENT_ID = ?",
                arrayOf(contentId),
                null,
                null,
                "$COLUMN_TTS_SENTENCE_INDEX ASC"
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            TtsAudioRecord(
                                contentId = cursor.getString(cursor.columnIndex(COLUMN_TTS_CONTENT_ID)),
                                sentenceIndex = cursor.getInt(cursor.columnIndex(COLUMN_TTS_SENTENCE_INDEX)),
                                sentenceText = cursor.getString(cursor.columnIndex(COLUMN_TTS_SENTENCE_TEXT)),
                                sentenceHash = cursor.getString(cursor.columnIndex(COLUMN_TTS_SENTENCE_HASH)),
                                filePath = cursor.getString(cursor.columnIndex(COLUMN_TTS_FILE_PATH))
                            )
                        )
                    }
                }
            }
        }

    fun upsertTtsAudio(record: TtsAudioRecord) {
        writableDatabase.use { db ->
            db.insertWithOnConflict(
                TABLE_TTS_AUDIO,
                null,
                ContentValues().apply {
                    put(COLUMN_TTS_CONTENT_ID, record.contentId)
                    put(COLUMN_TTS_SENTENCE_INDEX, record.sentenceIndex)
                    put(COLUMN_TTS_SENTENCE_TEXT, record.sentenceText)
                    put(COLUMN_TTS_SENTENCE_HASH, record.sentenceHash)
                    put(COLUMN_TTS_FILE_PATH, record.filePath)
                    put(COLUMN_TTS_CREATED_AT, System.currentTimeMillis())
                },
                SQLiteDatabase.CONFLICT_REPLACE
            )
        }
    }

    fun deleteTtsAudio(contentId: String) {
        writableDatabase.use { db ->
            db.delete(
                TABLE_TTS_AUDIO,
                "$COLUMN_TTS_CONTENT_ID = ?",
                arrayOf(contentId)
            )
        }
    }

    fun loadWordForm(normalized: String): WordFormResponse? =
        loadWordForm(normalized = normalized, sentence = "")

    fun loadWordForm(normalized: String, sentence: String): WordFormResponse? =
        readableDatabase.use { db ->
            db.query(
                TABLE_WORD_FORM_CACHE,
                null,
                "$COLUMN_WORD_FORM_CACHE_KEY = ?",
                arrayOf(wordFormCacheKey(normalized, sentence)),
                null,
                null,
                null,
                "1"
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@use null

                val relationType = cursor.getNullableString(COLUMN_WORD_FORM_RELATION_TYPE)
                val relationTarget = cursor.getNullableString(COLUMN_WORD_FORM_RELATION_TARGET)
                val relationLabel = cursor.getNullableString(COLUMN_WORD_FORM_RELATION_LABEL)
                val variantGroups = runCatching {
                    json.decodeFromString<List<WordFormVariantGroup>>(
                        cursor.getNullableString(COLUMN_WORD_FORM_VARIANT_GROUPS_JSON).orEmpty()
                    )
                }.getOrDefault(emptyList())
                WordFormResponse(
                    surface = cursor.getString(cursor.columnIndex(COLUMN_WORD_FORM_SURFACE)),
                    normalized = cursor.getString(cursor.columnIndex(COLUMN_WORD_FORM_NORMALIZED)),
                    headword = cursor.getString(cursor.columnIndex(COLUMN_WORD_FORM_HEADWORD)),
                    pronunciationTarget = cursor.getString(
                        cursor.columnIndex(COLUMN_WORD_FORM_PRONUNCIATION_TARGET)
                    ),
                    relation = if (
                        relationType != null &&
                        relationTarget != null &&
                        relationLabel != null
                    ) {
                        WordFormRelation(
                            type = relationType,
                            target = relationTarget,
                            label = relationLabel
                        )
                    } else {
                        null
                    },
                    expansion = cursor.getNullableString(COLUMN_WORD_FORM_EXPANSION),
                    currentPartOfSpeech = cursor.getNullableString(COLUMN_WORD_FORM_CURRENT_POS),
                    currentPartOfSpeechLabel = cursor.getNullableString(
                        COLUMN_WORD_FORM_CURRENT_POS_LABEL
                    ).orEmpty(),
                    variantGroups = variantGroups,
                    confidence = cursor.getString(cursor.columnIndex(COLUMN_WORD_FORM_CONFIDENCE)),
                    source = cursor.getString(cursor.columnIndex(COLUMN_WORD_FORM_SOURCE))
                )
            }
        }

    fun upsertWordForm(wordForm: WordFormResponse) {
        upsertWordForm(wordForm = wordForm, sentence = "")
    }

    fun upsertWordForm(wordForm: WordFormResponse, sentence: String) {
        writableDatabase.use { db ->
            db.insertWithOnConflict(
                TABLE_WORD_FORM_CACHE,
                null,
                ContentValues().apply {
                    put(COLUMN_WORD_FORM_CACHE_KEY, wordFormCacheKey(wordForm.normalized, sentence))
                    put(COLUMN_WORD_FORM_NORMALIZED, wordForm.normalized)
                    put(COLUMN_WORD_FORM_SURFACE, wordForm.surface)
                    put(COLUMN_WORD_FORM_HEADWORD, wordForm.headword)
                    put(COLUMN_WORD_FORM_PRONUNCIATION_TARGET, wordForm.pronunciationTarget)
                    put(COLUMN_WORD_FORM_RELATION_TYPE, wordForm.relation?.type)
                    put(COLUMN_WORD_FORM_RELATION_TARGET, wordForm.relation?.target)
                    put(COLUMN_WORD_FORM_RELATION_LABEL, wordForm.relation?.label)
                    put(COLUMN_WORD_FORM_EXPANSION, wordForm.expansion)
                    put(COLUMN_WORD_FORM_CURRENT_POS, wordForm.currentPartOfSpeech)
                    put(COLUMN_WORD_FORM_CURRENT_POS_LABEL, wordForm.currentPartOfSpeechLabel)
                    put(COLUMN_WORD_FORM_VARIANT_GROUPS_JSON, json.encodeToString(wordForm.variantGroups))
                    put(COLUMN_WORD_FORM_CONFIDENCE, wordForm.confidence)
                    put(COLUMN_WORD_FORM_SOURCE, wordForm.source)
                    put(COLUMN_WORD_FORM_UPDATED_AT, System.currentTimeMillis())
                },
                SQLiteDatabase.CONFLICT_REPLACE
            )
        }
    }

    fun loadWordPronunciation(normalized: String): WordPronunciationResponse? =
        readableDatabase.use { db ->
            db.query(
                TABLE_WORD_PRONUNCIATION_CACHE,
                null,
                "$COLUMN_WORD_PRONUNCIATION_NORMALIZED = ?",
                arrayOf(normalized),
                null,
                null,
                null,
                "1"
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@use null

                WordPronunciationResponse(
                    word = cursor.getString(cursor.columnIndex(COLUMN_WORD_PRONUNCIATION_WORD)),
                    normalized = cursor.getString(cursor.columnIndex(COLUMN_WORD_PRONUNCIATION_NORMALIZED)),
                    phonetic = cursor.getNullableString(COLUMN_WORD_PRONUNCIATION_PHONETIC),
                    audioUrl = cursor.getNullableString(COLUMN_WORD_PRONUNCIATION_AUDIO_URL),
                    source = cursor.getString(cursor.columnIndex(COLUMN_WORD_PRONUNCIATION_SOURCE)),
                    found = cursor.getInt(cursor.columnIndex(COLUMN_WORD_PRONUNCIATION_FOUND)) == 1
                )
            }
        }

    fun upsertWordPronunciation(pronunciation: WordPronunciationResponse) {
        writableDatabase.use { db ->
            if (pronunciation.phonetic.isNullOrBlank()) {
                deleteWordPronunciation(db, pronunciation.normalized)
                return@use
            }

            db.insertWithOnConflict(
                TABLE_WORD_PRONUNCIATION_CACHE,
                null,
                ContentValues().apply {
                    put(COLUMN_WORD_PRONUNCIATION_NORMALIZED, pronunciation.normalized)
                    put(COLUMN_WORD_PRONUNCIATION_WORD, pronunciation.word)
                    put(COLUMN_WORD_PRONUNCIATION_PHONETIC, pronunciation.phonetic)
                    put(COLUMN_WORD_PRONUNCIATION_AUDIO_URL, pronunciation.audioUrl)
                    put(COLUMN_WORD_PRONUNCIATION_SOURCE, pronunciation.source)
                    put(COLUMN_WORD_PRONUNCIATION_FOUND, if (pronunciation.found) 1 else 0)
                    put(COLUMN_WORD_PRONUNCIATION_UPDATED_AT, System.currentTimeMillis())
                },
                SQLiteDatabase.CONFLICT_REPLACE
            )
        }
    }

    fun deleteWordPronunciation(normalized: String) {
        writableDatabase.use { db ->
            deleteWordPronunciation(db, normalized)
        }
    }

    private fun deleteWordPronunciation(db: SQLiteDatabase, normalized: String) {
        db.delete(
            TABLE_WORD_PRONUNCIATION_CACHE,
            "$COLUMN_WORD_PRONUNCIATION_NORMALIZED = ?",
            arrayOf(normalized)
        )
    }

    fun loadWordMeaning(normalized: String): WordMeaningResponse? =
        loadWordMeaning(normalized = normalized, sentence = "")

    fun loadWordMeaning(normalized: String, sentence: String): WordMeaningResponse? =
        readableDatabase.use { db ->
            db.query(
                TABLE_WORD_MEANING_CACHE,
                null,
                "$COLUMN_WORD_MEANING_CACHE_KEY = ?",
                arrayOf(wordMeaningCacheKey(normalized, sentence)),
                null,
                null,
                null,
                "1"
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@use null

                val meanings = runCatching {
                    json.decodeFromString<List<WordMeaningEntry>>(
                        cursor.getString(cursor.columnIndex(COLUMN_WORD_MEANING_ITEMS_JSON))
                    )
                }.getOrDefault(emptyList())
                WordMeaningResponse(
                    word = cursor.getString(cursor.columnIndex(COLUMN_WORD_MEANING_WORD)),
                    normalized = cursor.getString(cursor.columnIndex(COLUMN_WORD_MEANING_NORMALIZED)),
                    meanings = meanings,
                    source = cursor.getString(cursor.columnIndex(COLUMN_WORD_MEANING_SOURCE)),
                    found = cursor.getInt(cursor.columnIndex(COLUMN_WORD_MEANING_FOUND)) == 1,
                    sentenceChinese = cursor.getNullableString(COLUMN_WORD_MEANING_SENTENCE_CHINESE).orEmpty()
                )
            }
        }

    fun upsertWordMeaning(meaning: WordMeaningResponse) {
        upsertWordMeaning(meaning = meaning, sentence = "")
    }

    fun upsertWordMeaning(meaning: WordMeaningResponse, sentence: String) {
        writableDatabase.use { db ->
            db.insertWithOnConflict(
                TABLE_WORD_MEANING_CACHE,
                null,
                ContentValues().apply {
                    put(COLUMN_WORD_MEANING_CACHE_KEY, wordMeaningCacheKey(meaning.normalized, sentence))
                    put(COLUMN_WORD_MEANING_NORMALIZED, meaning.normalized)
                    put(COLUMN_WORD_MEANING_WORD, meaning.word)
                    put(COLUMN_WORD_MEANING_SENTENCE_HASH, stableHash(sentence.normalizeCacheSentence()))
                    put(COLUMN_WORD_MEANING_ITEMS_JSON, json.encodeToString(meaning.meanings))
                    put(COLUMN_WORD_MEANING_SENTENCE_CHINESE, meaning.sentenceChinese)
                    put(COLUMN_WORD_MEANING_SOURCE, meaning.source)
                    put(COLUMN_WORD_MEANING_FOUND, if (meaning.found) 1 else 0)
                    put(COLUMN_WORD_MEANING_UPDATED_AT, System.currentTimeMillis())
                },
                SQLiteDatabase.CONFLICT_REPLACE
            )
        }
    }

    fun loadWordPhonics(normalized: String, sentence: String, ipa: String?): WordPhonicsResponse? =
        readableDatabase.use { db ->
            db.query(
                TABLE_WORD_PHONICS_CACHE,
                null,
                "$COLUMN_WORD_PHONICS_CACHE_KEY = ?",
                arrayOf(wordPhonicsCacheKey(normalized, sentence, ipa)),
                null,
                null,
                null,
                "1"
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@use null

                val phonemes = runCatching {
                    json.decodeFromString<List<String>>(
                        cursor.getString(cursor.columnIndex(COLUMN_WORD_PHONICS_PHONEMES_JSON))
                    )
                }.getOrDefault(emptyList())
                val segments = runCatching {
                    json.decodeFromString<List<WordPhonicsSegment>>(
                        cursor.getString(cursor.columnIndex(COLUMN_WORD_PHONICS_SEGMENTS_JSON))
                    )
                }.getOrDefault(emptyList())
                val chunks = runCatching {
                    json.decodeFromString<List<WordPhonicsChunk>>(
                        cursor.getString(cursor.columnIndex(COLUMN_WORD_PHONICS_CHUNKS_JSON))
                    )
                }.getOrDefault(emptyList())

                WordPhonicsResponse(
                    word = cursor.getString(cursor.columnIndex(COLUMN_WORD_PHONICS_WORD)),
                    normalized = cursor.getString(cursor.columnIndex(COLUMN_WORD_PHONICS_NORMALIZED)),
                    ipa = cursor.getNullableString(COLUMN_WORD_PHONICS_IPA),
                    phonemes = phonemes,
                    segments = segments,
                    chunks = chunks,
                    note = cursor.getString(cursor.columnIndex(COLUMN_WORD_PHONICS_NOTE)),
                    verification = cursor.getString(cursor.columnIndex(COLUMN_WORD_PHONICS_VERIFICATION)),
                    source = cursor.getString(cursor.columnIndex(COLUMN_WORD_PHONICS_SOURCE)),
                    found = cursor.getInt(cursor.columnIndex(COLUMN_WORD_PHONICS_FOUND)) == 1,
                    message = cursor.getString(cursor.columnIndex(COLUMN_WORD_PHONICS_MESSAGE))
                )
            }
        }

    fun upsertWordPhonics(phonics: WordPhonicsResponse, sentence: String, ipa: String?) {
        writableDatabase.use { db ->
            db.insertWithOnConflict(
                TABLE_WORD_PHONICS_CACHE,
                null,
                ContentValues().apply {
                    put(COLUMN_WORD_PHONICS_CACHE_KEY, wordPhonicsCacheKey(phonics.normalized, sentence, ipa))
                    put(COLUMN_WORD_PHONICS_NORMALIZED, phonics.normalized)
                    put(COLUMN_WORD_PHONICS_WORD, phonics.word)
                    put(COLUMN_WORD_PHONICS_SENTENCE_HASH, stableHash(sentence.normalizeCacheSentence()))
                    put(COLUMN_WORD_PHONICS_IPA_KEY, ipa.normalizeCacheIpa())
                    put(COLUMN_WORD_PHONICS_IPA, phonics.ipa)
                    put(COLUMN_WORD_PHONICS_PHONEMES_JSON, json.encodeToString(phonics.phonemes))
                    put(COLUMN_WORD_PHONICS_SEGMENTS_JSON, json.encodeToString(phonics.segments))
                    put(COLUMN_WORD_PHONICS_CHUNKS_JSON, json.encodeToString(phonics.chunks))
                    put(COLUMN_WORD_PHONICS_NOTE, phonics.note)
                    put(COLUMN_WORD_PHONICS_VERIFICATION, phonics.verification)
                    put(COLUMN_WORD_PHONICS_SOURCE, phonics.source)
                    put(COLUMN_WORD_PHONICS_FOUND, if (phonics.found) 1 else 0)
                    put(COLUMN_WORD_PHONICS_MESSAGE, phonics.message)
                    put(COLUMN_WORD_PHONICS_UPDATED_AT, System.currentTimeMillis())
                },
                SQLiteDatabase.CONFLICT_REPLACE
            )
        }
    }

    fun loadWordAudio(wordKey: String): WordAudioRecord? =
        readableDatabase.use { db ->
            db.query(
                TABLE_WORD_AUDIO_CACHE,
                null,
                "$COLUMN_WORD_AUDIO_KEY = ?",
                arrayOf(wordKey),
                null,
                null,
                null,
                "1"
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@use null

                WordAudioRecord(
                    wordKey = cursor.getString(cursor.columnIndex(COLUMN_WORD_AUDIO_KEY)),
                    wordText = cursor.getString(cursor.columnIndex(COLUMN_WORD_AUDIO_TEXT)),
                    wordHash = cursor.getString(cursor.columnIndex(COLUMN_WORD_AUDIO_HASH)),
                    filePath = cursor.getString(cursor.columnIndex(COLUMN_WORD_AUDIO_FILE_PATH))
                )
            }
        }

    fun upsertWordAudio(record: WordAudioRecord) {
        writableDatabase.use { db ->
            db.insertWithOnConflict(
                TABLE_WORD_AUDIO_CACHE,
                null,
                ContentValues().apply {
                    put(COLUMN_WORD_AUDIO_KEY, record.wordKey)
                    put(COLUMN_WORD_AUDIO_TEXT, record.wordText)
                    put(COLUMN_WORD_AUDIO_HASH, record.wordHash)
                    put(COLUMN_WORD_AUDIO_FILE_PATH, record.filePath)
                    put(COLUMN_WORD_AUDIO_CREATED_AT, System.currentTimeMillis())
                },
                SQLiteDatabase.CONFLICT_REPLACE
            )
        }
    }

    private fun countByColumn(
        column: String,
        types: Set<ContentType>,
        levels: Set<ContentLevel>,
        sources: Set<String>
    ): Map<String, Int> =
        readableDatabase.use { db ->
            val query = buildFilterQuery(types, levels, sources)
            db.rawQuery(
                "SELECT $column, COUNT(*) FROM $TABLE_CONTENT${query.whereClauseForRawQuery()} GROUP BY $column",
                query.selectionArgs
            ).use { cursor ->
                buildMap {
                    while (cursor.moveToNext()) {
                        val value = cursor.getNullableString(column)?.trim().orEmpty()
                        if (value.isNotBlank()) {
                            put(value, cursor.getInt(1))
                        }
                    }
                }
            }
        }

    private inline fun <K, V, R> Map<K, V>.mapNotNullKeys(transform: (K) -> R?): Map<R, V> =
        buildMap {
            this@mapNotNullKeys.forEach { (key, value) ->
                transform(key)?.let { put(it, value) }
            }
        }

    private fun Content.toContentValues(cacheOrder: Int): ContentValues =
        ContentValues().apply {
            put(COLUMN_ID, id)
            put(COLUMN_TITLE, title)
            put(COLUMN_TYPE, type.name)
            put(COLUMN_LEVEL, level.name)
            put(COLUMN_UPDATED_AT, System.currentTimeMillis())
            put(COLUMN_CACHE_ORDER, cacheOrder)
            when (this@toContentValues) {
                is Article -> {
                    put(COLUMN_BODY, content)
                    put(COLUMN_AUTHOR, author)
                    put(COLUMN_SOURCE, sourceName.ifBlank { source.ifBlank { author } })
                    put(COLUMN_DATE, date)
                }

                is Blog -> {
                    put(COLUMN_BODY, content)
                    put(COLUMN_AUTHOR, author)
                    put(COLUMN_SOURCE, sourceName.ifBlank { source.ifBlank { author } })
                    put(COLUMN_DATE, date)
                }

                is News -> {
                    put(COLUMN_BODY, content)
                    put(COLUMN_SOURCE, sourceName.ifBlank { source })
                    put(COLUMN_DATE, date)
                }

                is Dialogue -> {
                    put(COLUMN_BODY, lines.joinToString(LINE_SEPARATOR) { line ->
                        "${line.speaker}$SPEAKER_SEPARATOR${line.text}"
                    })
                    put(COLUMN_SOURCE, sourceName.ifBlank { source })
                    put(COLUMN_DATE, date)
                }
            }
        }

    private fun buildFilterQuery(
        types: Set<ContentType>,
        levels: Set<ContentLevel>,
        sources: Set<String>
    ): FilterQuery {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()

        if (types.isNotEmpty()) {
            clauses += "$COLUMN_TYPE IN (${types.joinToString(",") { "?" }})"
            args += types.map { it.name }
        }

        if (levels.isNotEmpty()) {
            clauses += "$COLUMN_LEVEL IN (${levels.joinToString(",") { "?" }})"
            args += levels.map { it.name }
        }

        if (sources.isNotEmpty()) {
            clauses += "$COLUMN_SOURCE IN (${sources.joinToString(",") { "?" }})"
            args += sources
        }

        return FilterQuery(
            selection = clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND "),
            selectionArgs = args.toTypedArray()
        )
    }

    private data class FilterQuery(
        val selection: String?,
        val selectionArgs: Array<String>
    ) {
        fun whereClauseForRawQuery(): String =
            selection?.let { " WHERE $it" }.orEmpty()
    }

    private fun android.database.Cursor.toContent(): Content? {
        val id = getString(columnIndex(COLUMN_ID))
        val title = getString(columnIndex(COLUMN_TITLE))
        val type = runCatching {
            ContentType.valueOf(getString(columnIndex(COLUMN_TYPE)))
        }.getOrNull() ?: return null
        val level = runCatching {
            ContentLevel.valueOf(getNullableString(COLUMN_LEVEL) ?: ContentLevel.B1.name)
        }.getOrNull() ?: ContentLevel.B1
        val body = getString(columnIndex(COLUMN_BODY))
        val author = getNullableString(COLUMN_AUTHOR)
        val source = getNullableString(COLUMN_SOURCE)
        val date = getNullableString(COLUMN_DATE).orEmpty()

        return when (type) {
            ContentType.ARTICLE -> Article(
                articleTitle = title,
                content = body,
                author = author ?: "Unknown",
                date = date,
                source = source ?: author ?: "Unknown",
                contentLevel = level,
                contentId = id
            )

            ContentType.BLOG -> Blog(
                blogTitle = title,
                content = body,
                author = author ?: "Unknown",
                date = date,
                source = source ?: author ?: "Unknown",
                contentLevel = level,
                contentId = id
            )

            ContentType.NEWS -> News(
                newsTitle = title,
                content = body,
                source = source ?: "Unknown",
                date = date,
                contentLevel = level,
                contentId = id
            )

            ContentType.DIALOGUE -> Dialogue(
                dialogueTitle = title,
                lines = body.split(LINE_SEPARATOR)
                    .filter { it.isNotBlank() }
                    .map { line ->
                        val parts = line.split(SPEAKER_SEPARATOR, limit = 2)
                        DialogueLine(
                            speaker = parts.firstOrNull().orEmpty(),
                            text = parts.getOrElse(1) { "" }
                        )
                    },
                date = date,
                source = source.orEmpty(),
                contentLevel = level,
                contentId = id
            )
        }
    }

    private fun android.database.Cursor.columnIndex(column: String): Int =
        getColumnIndexOrThrow(column)

    private fun android.database.Cursor.getNullableString(column: String): String? {
        val index = columnIndex(column)
        return if (isNull(index)) null else getString(index)
    }

    private fun wordMeaningCacheKey(normalized: String, sentence: String): String =
        "${normalized.trim().lowercase()}|${stableHash(sentence.normalizeCacheSentence())}"

    private fun wordFormCacheKey(normalized: String, sentence: String): String =
        "${normalized.trim().lowercase()}|${stableHash(sentence.normalizeCacheSentence())}"

    private fun wordPhonicsCacheKey(normalized: String, sentence: String, ipa: String?): String =
        "${normalized.trim().lowercase()}|${stableHash(sentence.normalizeCacheSentence())}|${ipa.normalizeCacheIpa()}"

    private fun String.normalizeCacheSentence(): String =
        trim().replace(Regex("\\s+"), " ")

    private fun String?.normalizeCacheIpa(): String =
        orEmpty().trim().replace(Regex("\\s+"), "")

    private fun stableHash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    companion object {
        private const val DATABASE_NAME = "english_study_cache.db"
        private const val DATABASE_VERSION = 21

        private const val TABLE_CONTENT = "content_cache"
        private const val COLUMN_ID = "id"
        private const val COLUMN_TITLE = "title"
        private const val COLUMN_TYPE = "type"
        private const val COLUMN_LEVEL = "level"
        private const val COLUMN_BODY = "body"
        private const val COLUMN_AUTHOR = "author"
        private const val COLUMN_SOURCE = "source"
        private const val COLUMN_DATE = "date"
        private const val COLUMN_UPDATED_AT = "updated_at"
        private const val COLUMN_CACHE_ORDER = "cache_order"

        private const val TABLE_TTS_AUDIO = "tts_audio_cache"
        private const val COLUMN_TTS_CONTENT_ID = "content_id"
        private const val COLUMN_TTS_SENTENCE_INDEX = "sentence_index"
        private const val COLUMN_TTS_SENTENCE_TEXT = "sentence_text"
        private const val COLUMN_TTS_SENTENCE_HASH = "sentence_hash"
        private const val COLUMN_TTS_FILE_PATH = "file_path"
        private const val COLUMN_TTS_CREATED_AT = "created_at"

        private const val TABLE_WORD_FORM_CACHE = "word_form_cache"
        private const val COLUMN_WORD_FORM_CACHE_KEY = "cache_key"
        private const val COLUMN_WORD_FORM_NORMALIZED = "normalized"
        private const val COLUMN_WORD_FORM_SURFACE = "surface"
        private const val COLUMN_WORD_FORM_HEADWORD = "headword"
        private const val COLUMN_WORD_FORM_PRONUNCIATION_TARGET = "pronunciation_target"
        private const val COLUMN_WORD_FORM_RELATION_TYPE = "relation_type"
        private const val COLUMN_WORD_FORM_RELATION_TARGET = "relation_target"
        private const val COLUMN_WORD_FORM_RELATION_LABEL = "relation_label"
        private const val COLUMN_WORD_FORM_EXPANSION = "expansion"
        private const val COLUMN_WORD_FORM_CURRENT_POS = "current_part_of_speech"
        private const val COLUMN_WORD_FORM_CURRENT_POS_LABEL = "current_part_of_speech_label"
        private const val COLUMN_WORD_FORM_VARIANT_GROUPS_JSON = "variant_groups_json"
        private const val COLUMN_WORD_FORM_CONFIDENCE = "confidence"
        private const val COLUMN_WORD_FORM_SOURCE = "source"
        private const val COLUMN_WORD_FORM_UPDATED_AT = "updated_at"

        private const val TABLE_WORD_PRONUNCIATION_CACHE = "word_pronunciation_cache"
        private const val COLUMN_WORD_PRONUNCIATION_NORMALIZED = "normalized"
        private const val COLUMN_WORD_PRONUNCIATION_WORD = "word"
        private const val COLUMN_WORD_PRONUNCIATION_PHONETIC = "phonetic"
        private const val COLUMN_WORD_PRONUNCIATION_AUDIO_URL = "audio_url"
        private const val COLUMN_WORD_PRONUNCIATION_SOURCE = "source"
        private const val COLUMN_WORD_PRONUNCIATION_FOUND = "found"
        private const val COLUMN_WORD_PRONUNCIATION_UPDATED_AT = "updated_at"

        private const val TABLE_WORD_MEANING_CACHE = "word_meaning_cache"
        private const val COLUMN_WORD_MEANING_CACHE_KEY = "cache_key"
        private const val COLUMN_WORD_MEANING_NORMALIZED = "normalized"
        private const val COLUMN_WORD_MEANING_WORD = "word"
        private const val COLUMN_WORD_MEANING_SENTENCE_HASH = "sentence_hash"
        private const val COLUMN_WORD_MEANING_ITEMS_JSON = "items_json"
        private const val COLUMN_WORD_MEANING_SENTENCE_CHINESE = "sentence_chinese"
        private const val COLUMN_WORD_MEANING_SOURCE = "source"
        private const val COLUMN_WORD_MEANING_FOUND = "found"
        private const val COLUMN_WORD_MEANING_UPDATED_AT = "updated_at"

        private const val TABLE_WORD_PHONICS_CACHE = "word_phonics_cache"
        private const val COLUMN_WORD_PHONICS_CACHE_KEY = "cache_key"
        private const val COLUMN_WORD_PHONICS_NORMALIZED = "normalized"
        private const val COLUMN_WORD_PHONICS_WORD = "word"
        private const val COLUMN_WORD_PHONICS_SENTENCE_HASH = "sentence_hash"
        private const val COLUMN_WORD_PHONICS_IPA_KEY = "ipa_key"
        private const val COLUMN_WORD_PHONICS_IPA = "ipa"
        private const val COLUMN_WORD_PHONICS_PHONEMES_JSON = "phonemes_json"
        private const val COLUMN_WORD_PHONICS_SEGMENTS_JSON = "segments_json"
        private const val COLUMN_WORD_PHONICS_CHUNKS_JSON = "chunks_json"
        private const val COLUMN_WORD_PHONICS_NOTE = "note"
        private const val COLUMN_WORD_PHONICS_VERIFICATION = "verification"
        private const val COLUMN_WORD_PHONICS_SOURCE = "source"
        private const val COLUMN_WORD_PHONICS_FOUND = "found"
        private const val COLUMN_WORD_PHONICS_MESSAGE = "message"
        private const val COLUMN_WORD_PHONICS_UPDATED_AT = "updated_at"

        private const val TABLE_WORD_AUDIO_CACHE = "word_audio_cache"
        private const val COLUMN_WORD_AUDIO_KEY = "word_key"
        private const val COLUMN_WORD_AUDIO_TEXT = "word_text"
        private const val COLUMN_WORD_AUDIO_HASH = "word_hash"
        private const val COLUMN_WORD_AUDIO_FILE_PATH = "file_path"
        private const val COLUMN_WORD_AUDIO_CREATED_AT = "created_at"

        private const val LINE_SEPARATOR = "\n---LINE---\n"
        private const val SPEAKER_SEPARATOR = "::"

        private val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}

data class TtsAudioRecord(
    val contentId: String,
    val sentenceIndex: Int,
    val sentenceText: String,
    val sentenceHash: String,
    val filePath: String
)

data class WordAudioRecord(
    val wordKey: String,
    val wordText: String,
    val wordHash: String,
    val filePath: String
)
