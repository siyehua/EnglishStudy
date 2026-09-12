package com.siyehua.egnlishstudy.data

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.siyehua.egnlishstudy.data.wordform.WordFormApiClient
import com.siyehua.egnlishstudy.model.Article
import com.siyehua.egnlishstudy.model.Blog
import com.siyehua.egnlishstudy.model.Content
import com.siyehua.egnlishstudy.model.Dialogue
import com.siyehua.egnlishstudy.model.News
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class TtsAudioManager(context: Context) {
    private val appContext = context.applicationContext
    private val database = ContentCacheDatabase(appContext)
    private val audioDir = File(appContext.filesDir, AUDIO_DIR_NAME).apply { mkdirs() }
    private val wordAudioDir = File(appContext.filesDir, WORD_AUDIO_DIR_NAME).apply { mkdirs() }

    suspend fun ensureAudioForContent(content: Content): TtsAudioResult = withContext(Dispatchers.IO) {
        val sentences = content.ttsText()
            .splitIntoSentences()
            .take(MAX_SENTENCES_PER_CONTENT)
        if (sentences.isEmpty()) {
            return@withContext TtsAudioResult.Failure("No readable sentences found for this content.")
        }

        val existingRecords = database.loadTtsAudio(content.id)
            .associateBy { it.sentenceIndex }
        val expectedHashes = sentences.map(::stableHash)
        val cacheMatches = existingRecords.size == sentences.size &&
            expectedHashes.withIndex().all { (index, hash) ->
                existingRecords[index]?.sentenceHash == hash &&
                    existingRecords[index]?.sentenceText == sentences[index] &&
                    File(existingRecords[index]?.filePath.orEmpty()).exists()
            }
        if (!cacheMatches) {
            deleteContentAudioFiles(content.id)
            database.deleteTtsAudio(content.id)
            Log.i(TAG, "Rebuilding TTS cache for content=${content.id} title=${content.title} sentences=${sentences.size}")
        }

        val records = MutableList<TtsAudioRecord?>(sentences.size) { null }
        val missingSentences = mutableListOf<IndexedSentence>()
        sentences.forEachIndexed { index, sentence ->
            val hash = expectedHashes[index]
            val cached = if (cacheMatches) existingRecords[index] else null
            if (cached != null && cached.sentenceHash == hash && File(cached.filePath).exists()) {
                records[index] = cached
                Log.i(TAG, "Reusing TTS content=${content.id} index=$index hash=$hash text=${sentence.logSnippet()}")
            } else {
                missingSentences += IndexedSentence(
                    index = index,
                    text = sentence,
                    hash = hash
                )
            }
        }

        if (missingSentences.isEmpty()) {
            return@withContext TtsAudioResult.Success(records.toAudioUris())
        }

        var lastError: String? = null
        missingSentences.forEach { missing ->
            when (val result = synthesizeAndCache(content.id, missing.index, missing.text, missing.hash)) {
                is SentenceAudioResult.Success -> records[missing.index] = result.record
                is SentenceAudioResult.Failure -> {
                    lastError = result.message
                }
            }
        }

        if (records.all { it != null }) {
            TtsAudioResult.Success(records.toAudioUris())
        } else {
            val generatedCount = records.count { it != null }
            TtsAudioResult.Failure(
                lastError ?: "TTS audio generation is incomplete: $generatedCount/${sentences.size} sentence files are available."
            )
        }
    }

    fun loadCachedAudioUris(content: Content): List<Uri> =
        database.loadTtsAudio(content.id)
            .filter { File(it.filePath).exists() }
            .sortedBy { it.sentenceIndex }
            .map { Uri.fromFile(File(it.filePath)) }

    suspend fun ensureAudioForWord(word: String, audioUrl: String? = null): WordAudioResult = withContext(Dispatchers.IO) {
        val normalized = word.trim().lowercase().replace('\u2019', '\'')
        if (normalized.isBlank()) {
            return@withContext WordAudioResult.Failure("No word is available for pronunciation.")
        }

        val source = audioUrl?.takeIf { it.isNotBlank() } ?: normalized
        val hash = stableHash(source)
        val wordKey = stableWordKey(normalized, source)
        val cached = database.loadWordAudio(wordKey)
        if (cached != null && cached.wordHash == hash && File(cached.filePath).exists()) {
            return@withContext WordAudioResult.Success(Uri.fromFile(File(cached.filePath)))
        }

        if (!audioUrl.isNullOrBlank()) {
            return@withContext downloadAndCacheWordAudio(
                normalized = normalized,
                audioUrl = audioUrl,
                wordKey = wordKey,
                hash = hash
            )
        }

        runCatching {
            val audioBytes = requestAudio(
                text = normalized,
                instruction = "Read this English word clearly once, at a natural learning pace."
            )
            val file = File(wordAudioDir, "${wordKey}_$hash.wav")
            file.writeBytes(audioBytes)
            WordAudioRecord(
                wordKey = wordKey,
                wordText = normalized,
                wordHash = hash,
                filePath = file.absolutePath
            ).also(database::upsertWordAudio)
        }.onFailure {
            Log.w(TAG, "Failed to synthesize TTS for word=$normalized", it)
        }.fold(
            onSuccess = { WordAudioResult.Success(Uri.fromFile(File(it.filePath))) },
            onFailure = { WordAudioResult.Failure(it.message ?: "Word pronunciation request failed.") }
        )
    }

    suspend fun ensureAudioForText(text: String): WordAudioResult = withContext(Dispatchers.IO) {
        val normalized = text.trim().replace(Regex("\\s+"), " ")
        if (normalized.isBlank()) {
            return@withContext WordAudioResult.Failure("No text is available for pronunciation.")
        }

        val hash = stableHash(normalized)
        val textKey = "text_" + stableHash(normalized)
        val cached = database.loadWordAudio(textKey)
        if (cached != null && cached.wordHash == hash && File(cached.filePath).exists()) {
            return@withContext WordAudioResult.Success(Uri.fromFile(File(cached.filePath)))
        }

        runCatching {
            val audioBytes = requestAudio(
                text = normalized,
                instruction = "Read this English sentence clearly, at a natural learning pace."
            )
            val file = File(wordAudioDir, "${textKey}_$hash.wav")
            file.writeBytes(audioBytes)
            WordAudioRecord(
                wordKey = textKey,
                wordText = normalized,
                wordHash = hash,
                filePath = file.absolutePath
            ).also(database::upsertWordAudio)
        }.onFailure {
            Log.w(TAG, "Failed to synthesize TTS for text=$normalized", it)
        }.fold(
            onSuccess = { WordAudioResult.Success(Uri.fromFile(File(it.filePath))) },
            onFailure = { WordAudioResult.Failure(it.message ?: "Sentence pronunciation request failed.") }
        )
    }

    private fun downloadAndCacheWordAudio(
        normalized: String,
        audioUrl: String,
        wordKey: String,
        hash: String
    ): WordAudioResult =
        runCatching {
            val bytes = downloadAudio(audioUrl)
            val extension = audioUrl.substringBefore('?')
                .substringAfterLast('.', "mp3")
                .takeIf { it.length in 2..5 }
                ?: "mp3"
            val file = File(wordAudioDir, "${wordKey}_$hash.$extension")
            file.writeBytes(bytes)
            WordAudioRecord(
                wordKey = wordKey,
                wordText = normalized,
                wordHash = hash,
                filePath = file.absolutePath
            ).also(database::upsertWordAudio)
        }.fold(
            onSuccess = { WordAudioResult.Success(Uri.fromFile(File(it.filePath))) },
            onFailure = { WordAudioResult.Failure(it.message ?: "Dictionary audio download failed.") }
        )

    suspend fun ensureRealAudioForContent(content: Content): WordAudioResult =
        ensureContentAudio(content.audioUrl, "content")

    private suspend fun ensureContentAudio(audioUrl: String?, keyPrefix: String): WordAudioResult = withContext(Dispatchers.IO) {
        val url = audioUrl?.takeIf { it.isNotBlank() }
            ?: return@withContext WordAudioResult.Failure("No audio is available for this lesson.")

        val hash = stableHash(url)
        val key = "${keyPrefix}_" + stableHash(url)
        val cached = database.loadWordAudio(key)
        if (cached != null && cached.wordHash == hash && File(cached.filePath).exists()) {
            return@withContext WordAudioResult.Success(Uri.fromFile(File(cached.filePath)))
        }

        runCatching {
            val bytes = downloadAudio(url)
            val extension = url.substringBefore('?')
                .substringAfterLast('.', "mp3")
                .takeIf { it.length in 2..5 }
                ?: "mp3"
            val file = File(wordAudioDir, "${key}_$hash.$extension")
            file.writeBytes(bytes)
            WordAudioRecord(
                wordKey = key,
                wordText = url,
                wordHash = hash,
                filePath = file.absolutePath
            ).also(database::upsertWordAudio)
        }.fold(
            onSuccess = { WordAudioResult.Success(Uri.fromFile(File(it.filePath))) },
            onFailure = { WordAudioResult.Failure(it.message ?: "Audio download failed.") }
        )
    }

    fun playUri(uri: Uri, onCompletion: () -> Unit = {}, onError: (String) -> Unit = {}): MediaPlayer? =
        runCatching {
            MediaPlayer.create(appContext, uri)?.apply {
                setOnCompletionListener {
                    release()
                    onCompletion()
                }
                setOnErrorListener { player, _, _ ->
                    player.release()
                    onError("Audio playback failed.")
                    true
                }
                start()
            }
        }.fold(
            onSuccess = { it },
            onFailure = {
                onError(it.message ?: "Audio playback failed.")
                null
            }
        )

    private fun synthesizeAndCache(
        contentId: String,
        sentenceIndex: Int,
        sentence: String,
        sentenceHash: String
    ): SentenceAudioResult =
        runCatching {
            Log.i(TAG, "Requesting TTS content=$contentId index=$sentenceIndex hash=$sentenceHash text=${sentence.logSnippet()}")
            val audioBytes = requestAudio(
                text = sentence,
                instruction = "Read this English learning text clearly, at a natural pace, with a neutral teaching tone."
            )
            val file = File(audioDir, "${contentId}_${sentenceIndex}_$sentenceHash.wav")
            file.writeBytes(audioBytes)
            TtsAudioRecord(
                contentId = contentId,
                sentenceIndex = sentenceIndex,
                sentenceText = sentence,
                sentenceHash = sentenceHash,
                filePath = file.absolutePath
            ).also(database::upsertTtsAudio)
        }.onFailure {
            Log.w(TAG, "Failed to synthesize TTS for content=$contentId sentence=$sentenceIndex", it)
        }.fold(
            onSuccess = { SentenceAudioResult.Success(it) },
            onFailure = { SentenceAudioResult.Failure(it.message ?: "TTS request failed.") }
        )

    private fun requestAudio(
        text: String,
        instruction: String
    ): ByteArray {
        val connection = (URL(BACKEND_TTS_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = REQUEST_TIMEOUT_MS
            readTimeout = REQUEST_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "audio/wav, audio/*, */*")
        }

        val payload = JSONObject()
            .put("text", text)
            .put("instruction", instruction)

        return try {
            connection.outputStream.bufferedWriter().use { writer ->
                writer.write(payload.toString())
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val responseBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("Backend TTS request failed: HTTP $responseCode $responseBody")
            }

            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadAudio(audioUrl: String): ByteArray {
        val connection = (URL(audioUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = REQUEST_TIMEOUT_MS
            readTimeout = REQUEST_TIMEOUT_MS
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "audio/mpeg, audio/wav, audio/*, */*")
        }

        return try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException("Dictionary audio request failed: HTTP $responseCode")
            }
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun Content.ttsText(): String =
        when (this) {
            is Article -> content
            is Blog -> content
            is News -> content
            is Dialogue -> lines.joinToString("\n") { "${it.speaker}: ${it.text}" }
        }

    private fun String.splitIntoSentences(): List<String> =
        SentenceSplitter.split(this, minLength = MIN_SENTENCE_LENGTH)

    private fun String.logSnippet(): String =
        replace(Regex("\\s+"), " ")
            .let { if (it.length <= LOG_TEXT_LIMIT) it else it.take(LOG_TEXT_LIMIT) + "..." }

    private fun stableHash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(HASH_LENGTH)
    }

    private fun stableWordKey(word: String, source: String): String =
        "word_" + stableHash("$word|$source")

    private fun deleteContentAudioFiles(contentId: String) {
        audioDir.listFiles { file -> file.name.startsWith("${contentId}_") }
            ?.forEach { file -> runCatching { file.delete() } }
    }

    private fun List<TtsAudioRecord?>.toAudioUris(): List<Uri> =
        filterNotNull()
            .sortedBy { it.sentenceIndex }
            .map { Uri.fromFile(File(it.filePath)) }

    companion object {
        private const val TAG = "TtsAudioManager"
        private const val BACKEND_TTS_URL = "${WordFormApiClient.DEFAULT_BASE_URL}/tts-audio"
        private const val AUDIO_DIR_NAME = "tts_audio"
        private const val WORD_AUDIO_DIR_NAME = "word_tts_audio"
        private const val REQUEST_TIMEOUT_MS = 60_000
        private const val MAX_SENTENCES_PER_CONTENT = 80
        private const val MIN_SENTENCE_LENGTH = 2
        private const val HASH_LENGTH = 16
        private const val LOG_TEXT_LIMIT = 180
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android) EnglishStudy/1.0"
    }
}

private data class IndexedSentence(
    val index: Int,
    val text: String,
    val hash: String
)

sealed class TtsAudioResult {
    data class Success(val uris: List<Uri>) : TtsAudioResult()
    data class Failure(val message: String) : TtsAudioResult()
}

sealed class WordAudioResult {
    data class Success(val uri: Uri) : WordAudioResult()
    data class Failure(val message: String) : WordAudioResult()
}

private sealed class SentenceAudioResult {
    data class Success(val record: TtsAudioRecord) : SentenceAudioResult()
    data class Failure(val message: String) : SentenceAudioResult()
}
