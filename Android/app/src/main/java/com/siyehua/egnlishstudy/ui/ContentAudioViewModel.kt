package com.siyehua.egnlishstudy.ui

import android.app.Application
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.siyehua.egnlishstudy.data.TtsAudioManager
import com.siyehua.egnlishstudy.data.TtsAudioResult
import com.siyehua.egnlishstudy.data.WordAudioResult
import com.siyehua.egnlishstudy.model.Content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContentAudioViewModel(application: Application) : AndroidViewModel(application) {
    private val ttsAudioManager = TtsAudioManager(application)

    private val _uiState = MutableStateFlow<AudioUiState>(AudioUiState.Idle)
    val uiState: StateFlow<AudioUiState> = _uiState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var playlist: List<Uri> = emptyList()
    private var playlistDurations: List<Int> = emptyList()
    private var totalDurationMillis = 0L
    private var playlistIndex = 0
    private var highlightedSentenceIndex: Int? = null
    private var prepareJob: Job? = null
    private var progressJob: Job? = null

    fun togglePlayback(content: Content) {
        when (_uiState.value) {
            is AudioUiState.Playing -> pause()
            is AudioUiState.Paused -> resume()
            is AudioUiState.Preparing -> Unit
            else -> play(content)
        }
    }

    private fun play(content: Content) {
        stop(resetState = false)
        highlightedSentenceIndex = null
        prepareJob = viewModelScope.launch {
            _uiState.value = AudioUiState.Preparing
            val audioUris = when (val result = ttsAudioManager.ensureAudioForContent(content)) {
                is TtsAudioResult.Success -> result.uris
                is TtsAudioResult.Failure -> {
                    _uiState.value = AudioUiState.Error(result.message)
                    return@launch
                }
            }

            if (audioUris.isEmpty()) {
                _uiState.value = AudioUiState.Error("Audio is not available for this article.")
                return@launch
            }

            val durations = withContext(Dispatchers.IO) {
                audioUris.map(::readDurationMillis)
            }
            playlist = audioUris
            playlistDurations = durations
            totalDurationMillis = durations.sumOf { it.toLong() }
            playlistIndex = 0
            playCurrent()
        }
    }

    fun playSentence(sentence: String, sentenceIndex: Int) {
        val target = sentence.trim()
        if (target.isBlank()) return

        stop(resetState = false)
        highlightedSentenceIndex = sentenceIndex
        prepareJob = viewModelScope.launch {
            _uiState.value = AudioUiState.Preparing
            val audioUri = when (val result = ttsAudioManager.ensureAudioForText(target)) {
                is WordAudioResult.Success -> result.uri
                is WordAudioResult.Failure -> {
                    _uiState.value = AudioUiState.Error(result.message)
                    return@launch
                }
            }

            playlist = listOf(audioUri)
            playlistDurations = listOf(readDurationMillis(audioUri))
            totalDurationMillis = playlistDurations.sumOf { it.toLong() }
            playlistIndex = 0
            playCurrent()
        }
    }

    fun stop() {
        stop(resetState = true)
    }

    private fun stop(resetState: Boolean) {
        prepareJob?.cancel()
        prepareJob = null
        stopProgressUpdates()
        mediaPlayer?.setOnCompletionListener(null)
        runCatching { mediaPlayer?.stop() }
        mediaPlayer?.release()
        mediaPlayer = null
        playlist = emptyList()
        playlistDurations = emptyList()
        totalDurationMillis = 0L
        playlistIndex = 0
        highlightedSentenceIndex = null
        if (resetState) {
            _uiState.value = AudioUiState.Idle
        }
    }

    private fun pause() {
        val player = mediaPlayer ?: return
        runCatching { player.pause() }
        stopProgressUpdates()
        _uiState.value = AudioUiState.Paused(
            currentMillis = currentPlaybackMillis(),
            totalMillis = totalDurationMillis,
            currentSentenceIndex = highlightedSentenceIndex ?: playlistIndex
        )
    }

    private fun resume() {
        val player = mediaPlayer ?: run {
            _uiState.value = AudioUiState.Idle
            return
        }
        runCatching { player.start() }
            .onSuccess {
                emitPlayingState()
                startProgressUpdates()
            }
            .onFailure {
                _uiState.value = AudioUiState.Error(it.message ?: "Audio playback failed.")
            }
    }

    private fun playCurrent() {
        val uri = playlist.getOrNull(playlistIndex)
        if (uri == null) {
            finishPlayback()
            return
        }

        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(getApplication(), uri)?.apply {
            val measuredDuration = duration
            if (measuredDuration > 0 && playlistDurations.getOrElse(playlistIndex) { 0 } <= 0) {
                playlistDurations = playlistDurations.toMutableList().also {
                    it[playlistIndex] = measuredDuration
                }
                totalDurationMillis = playlistDurations.sumOf { it.toLong() }
            }
            setOnCompletionListener { advancePlaylist() }
            start()
        }
        if (mediaPlayer == null) {
            _uiState.value = AudioUiState.Error("Audio playback failed.")
            return
        }
        emitPlayingState()
        startProgressUpdates()
    }

    private fun advancePlaylist() {
        stopProgressUpdates()
        mediaPlayer?.release()
        mediaPlayer = null
        playlistIndex += 1
        playCurrent()
    }

    private fun finishPlayback() {
        stop(resetState = false)
        _uiState.value = AudioUiState.Idle
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressJob = viewModelScope.launch {
            while (true) {
                emitPlayingState()
                delay(PROGRESS_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun emitPlayingState() {
        _uiState.value = AudioUiState.Playing(
            currentMillis = currentPlaybackMillis(),
            totalMillis = totalDurationMillis,
            currentSentenceIndex = highlightedSentenceIndex ?: playlistIndex
        )
    }

    private fun currentPlaybackMillis(): Long {
        val previous = playlistDurations
            .take(playlistIndex)
            .sumOf { it.toLong() }
        val current = runCatching { mediaPlayer?.currentPosition ?: 0 }
            .getOrDefault(0)
            .coerceAtLeast(0)
            .toLong()
        val total = totalDurationMillis
        return if (total > 0) {
            (previous + current).coerceIn(0L, total)
        } else {
            previous + current
        }
    }

    private fun readDurationMillis(uri: Uri): Int {
        val metadataDuration = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(getApplication(), uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toIntOrNull()
                    ?: 0
            } finally {
                retriever.release()
            }
        }.getOrDefault(0)
        if (metadataDuration > 0) return metadataDuration

        return runCatching {
            val player = MediaPlayer.create(getApplication(), uri) ?: return@runCatching 0
            val duration = player.duration
            player.release()
            duration
        }.getOrDefault(0)
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    companion object {
        private const val PROGRESS_UPDATE_INTERVAL_MS = 250L
    }
}

sealed class AudioUiState {
    object Idle : AudioUiState()
    object Preparing : AudioUiState()
    data class Playing(
        val currentMillis: Long,
        val totalMillis: Long,
        val currentSentenceIndex: Int
    ) : AudioUiState()
    data class Paused(
        val currentMillis: Long,
        val totalMillis: Long,
        val currentSentenceIndex: Int
    ) : AudioUiState()
    data class Error(val message: String) : AudioUiState()
}
