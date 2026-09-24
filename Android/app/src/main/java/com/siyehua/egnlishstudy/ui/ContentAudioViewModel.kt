package com.siyehua.egnlishstudy.ui

import android.app.Application
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.siyehua.egnlishstudy.data.TtsAudioManager
import com.siyehua.egnlishstudy.data.TtsAudioResult
import com.siyehua.egnlishstudy.data.WordAudioResult
import com.siyehua.egnlishstudy.model.Content
import com.siyehua.egnlishstudy.model.Dialogue
import com.siyehua.egnlishstudy.playback.PlaybackBus
import com.siyehua.egnlishstudy.playback.PlaybackInfo
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
    private val ownerId = "vm-${System.identityHashCode(this)}"
    private var currentTitle = "英语听力"
    /** 当前正在播放/高亮的句子文本（通知栏 subtitle 用） */
    private var currentSentence = ""
    private val _currentSentenceFlow = MutableStateFlow("")
    /** 当前正在播放的句子（全局播放器条副标题用） */
    val currentSentenceFlow: StateFlow<String> = _currentSentenceFlow.asStateFlow()
    /** 课程队列：支持通知栏上一课/下一课 + 整课播完自动连播 */
    private var queueItems: List<Content> = emptyList()
    private var queueIndex = -1
    /** 当前播放是否为"整课"模式（决定播完后是否自动连播下一课） */
    private var lessonPlayMode = false
    /** 整课循环：播完本课后重新播放本课（通知栏循环按钮控制） */
    private var loopLesson = false
    private val _loopingLesson = MutableStateFlow(false)
    /** 整课循环开关状态（全局播放器条 UI 用） */
    val loopingLessonFlow: StateFlow<Boolean> = _loopingLesson.asStateFlow()
    private var isMuted = false
    private var captionOn = false
    private val _captionOn = MutableStateFlow(false)
    /** 字幕开关（记住偏好，UI 按钮高亮用） */
    val captionOnFlow: StateFlow<Boolean> = _captionOn.asStateFlow()
    private val captionPrefs by lazy {
        com.siyehua.egnlishstudy.data.CaptionStyleStore(getApplication())
    }
    private val _currentLesson = MutableStateFlow<Content?>(null)
    val currentLesson: StateFlow<Content?> = _currentLesson.asStateFlow()
    /** 整课播放时缓存各行文本，通知栏 subtitle 随高亮句动态更新 */
    private var activeDialogueLines: List<String> = emptyList()

    private fun sentenceForIndex(index: Int): String {
        activeDialogueLines.getOrNull(index)?.let { return it.take(80) }
        return currentSentence
    }

    private val _uiState = MutableStateFlow<AudioUiState>(AudioUiState.Idle)
    val uiState: StateFlow<AudioUiState> = _uiState.asStateFlow()

    init {
        // 记住用户上次的字幕开关偏好（UI 按钮高亮与服务保持一致）
        captionOn = captionPrefs.isCaptionEnabled()
        _captionOn.value = captionOn
        // 把播放状态同步给通知服务（Idle/Preparing/Error → 收起通知）
        // 注意：本 init 必须位于 _uiState 声明之后（Main.immediate 会同步执行 collect）
        viewModelScope.launch {
            _uiState.collect { state -> publishIfOwned(state) }
        }
        // 响应通知/锁屏按钮：暂停、继续、停止
        viewModelScope.launch {
            PlaybackBus.commands.collect { cmd ->
                if (PlaybackBus.ownerId != ownerId) return@collect
                when (cmd) {
                    PlaybackBus.Command.PAUSE -> pause()
                    PlaybackBus.Command.RESUME -> resume()
                    PlaybackBus.Command.STOP -> stop()
                    PlaybackBus.Command.NEXT -> playNextLesson()
                    PlaybackBus.Command.PREV -> playPrevLesson()
                    PlaybackBus.Command.TOGGLE_LESSON_LOOP -> toggleLessonLoop()
                    PlaybackBus.Command.TOGGLE_CAPTION -> {} // 字幕开关在服务内直接处理
                }
            }
        }
    }

    private fun publishIfOwned(state: AudioUiState) {
        val info = when (state) {
            is AudioUiState.Playing -> PlaybackInfo(
                title = currentTitle,
                subtitle = sentenceForIndex(state.currentSentenceIndex),
                isPlaying = true,
                isPaused = false,
                positionMs = state.currentMillis,
                durationMs = state.totalMillis,
                loopSingle = state.isLoopingSingle,
                loopLesson = state.isLoopingLesson
            )
            is AudioUiState.Paused -> PlaybackInfo(
                title = currentTitle,
                subtitle = sentenceForIndex(state.currentSentenceIndex),
                isPlaying = false,
                isPaused = true,
                positionMs = state.currentMillis,
                durationMs = state.totalMillis,
                loopSingle = state.isLoopingSingle,
                loopLesson = state.isLoopingLesson
            )
            else -> null
        }
        // 同步当前播放句子给全局播放器条
        if (state is AudioUiState.Playing || state is AudioUiState.Paused) {
            val idx = when (state) {
                is AudioUiState.Playing -> state.currentSentenceIndex
                is AudioUiState.Paused -> state.currentSentenceIndex
                else -> 0
            }
            _currentSentenceFlow.value = sentenceForIndex(idx)
        } else if (state is AudioUiState.Idle) {
            _currentSentenceFlow.value = ""
        }
        // 空状态只在「通知当前归我管」时才发布，避免其他屏幕的 ViewModel 实例误清掉正在播放的通知
        if (info != null || PlaybackBus.ownerId == ownerId) {
            if (info != null) ensurePlaybackService()
            PlaybackBus.publish(ownerId, info)
        }
    }

    private fun ensurePlaybackService() {
        val app = getApplication<Application>()
        ContextCompat.startForegroundService(
            app,
            Intent(app, com.siyehua.egnlishstudy.playback.PlaybackNotificationService::class.java)
        )
    }

    private var mediaPlayer: MediaPlayer? = null
    private var playlist: List<Uri> = emptyList()
    private var playlistDurations: List<Int> = emptyList()
    private var totalDurationMillis = 0L
    private var playlistIndex = 0
    private var highlightedSentenceIndex: Int? = null
    private var prepareJob: Job? = null
    private var progressJob: Job? = null
    private var segmentStartMs = 0L
    private var segmentEndMs = 0L
    // When playing the whole lesson, highlight the line whose time range
    // contains the current playback position.
    private var matchByTime = false
    private var lineRanges: List<Pair<Double, Double>> = emptyList()
    private var loopSingle = false

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
        lessonPlayMode = true
        _currentLesson.value = content
        highlightedSentenceIndex = null
        currentTitle = content.title
        // 整课播放时通知栏 subtitle 取当前高亮句（由 activeSentenceIndex 动态查）
        currentSentence = ""
        activeDialogueLines = (content as? Dialogue)?.lines?.map { it.text }.orEmpty()
        if (!content.audioUrl.isNullOrBlank()) {
            playRealAudio(content)
        } else {
            playTtsAudio(content)
        }
    }

    private fun playRealAudio(content: Content) {
        prepareJob = viewModelScope.launch {
            _uiState.value = AudioUiState.Preparing
            when (val result = ttsAudioManager.ensureRealAudioForContent(content)) {
                is WordAudioResult.Success -> {
                    playlist = listOf(result.uri)
                    playlistDurations = listOf(readDurationMillis(result.uri))
                    totalDurationMillis = playlistDurations.sumOf { it.toLong() }
                    playlistIndex = 0
                    segmentStartMs = (content.audioStart * 1000).toLong().coerceAtLeast(0)
                    segmentEndMs = (content.audioEnd * 1000).toLong().coerceAtLeast(0)
                    playCurrent()
                }

                is WordAudioResult.Failure -> {
                    // Real audio unavailable, fall back to synthesized TTS.
                    playTtsAudio(content)
                }
            }
        }
    }

    private fun playTtsAudio(content: Content) {
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
        lessonPlayMode = false
        currentSentence = target.take(80)

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

    /** Play a sentence by seeking into the lesson's full audio. */
    fun playSegment(
        content: Content,
        startSec: Double,
        endSec: Double,
        fallbackText: String,
        sentenceIndex: Int
    ) {
        currentTitle = content.title
        lessonPlayMode = false
        val audioUrl = content.audioUrl
        if (audioUrl.isNullOrBlank() || endSec <= startSec) {
            playSentence(fallbackText, sentenceIndex)
            return
        }

        stop(resetState = false)
        highlightedSentenceIndex = sentenceIndex
        prepareJob = viewModelScope.launch {
            _uiState.value = AudioUiState.Preparing
            when (val result = ttsAudioManager.ensureRealAudioForContent(content)) {
                is WordAudioResult.Success -> {
                    playlist = listOf(result.uri)
                    playlistDurations = listOf(readDurationMillis(result.uri))
                    totalDurationMillis = playlistDurations.sumOf { it.toLong() }
                    playlistIndex = 0
                    segmentStartMs = (startSec * 1000).toLong().coerceAtLeast(0)
                    segmentEndMs = (endSec * 1000).toLong().coerceAtLeast(0)
                    playCurrent()
                }

                is WordAudioResult.Failure -> playSentence(fallbackText, 0)
            }
        }
    }

    /** Play a favourite's stored audio clip. */
    fun playFavoriteUrl(url: String) {
        playSegmentUrl(url, "", 0)
    }

    /** Loop a single backend-cut sentence clip until stopped. */
    fun loopSegmentUrl(url: String, sentenceIndex: Int, title: String? = null, sentence: String? = null) {
        title?.let { currentTitle = it }
        sentence?.let { currentSentence = it.take(80) }
        lessonPlayMode = false
        matchByTime = false
        lineRanges = emptyList()
        stop(resetState = false)
        highlightedSentenceIndex = sentenceIndex
        prepareJob = viewModelScope.launch {
            _uiState.value = AudioUiState.Preparing
            when (val result = ttsAudioManager.ensureRemoteAudio(url)) {
                is WordAudioResult.Success -> {
                    playlist = listOf(result.uri)
                    playlistDurations = listOf(readDurationMillis(result.uri))
                    totalDurationMillis = playlistDurations.sumOf { it.toLong() }
                    playlistIndex = 0
                    segmentStartMs = 0L
                    segmentEndMs = 0L
                    loopSingle = true
                    playCurrent()
                }

                is WordAudioResult.Failure ->
                    _uiState.value = AudioUiState.Error(result.message)
            }
        }
    }

    /**
     * 设置课程队列（来自列表页当前展示顺序）。
     * 通知栏上一课/下一课与整课自动连播都基于这个队列。
     */
    fun setLessonQueue(items: List<Content>, currentId: String) {
        if (queueItems.map { it.id } == items.map { it.id } &&
            queueItems.getOrNull(queueIndex)?.id == currentId
        ) return
        queueItems = items
        queueIndex = items.indexOfFirst { it.id == currentId }
        if (queueIndex >= 0) _currentLesson.value = items[queueIndex]
    }

    /** 桌面悬浮字幕开关（底部播放器"字幕"按钮） */
    fun toggleCaptionOverlay() {
        captionOn = !captionOn
        captionPrefs.setCaptionEnabled(captionOn)
        _captionOn.value = captionOn
        // 直接启动服务的 toggle action（服务负责悬浮窗增删）
        // 服务此时已在前台运行(播放中)，用普通 startService 即可，避免 startForegroundService
        // 触发 "did not then call startForeground" 崩溃
        val app = getApplication<Application>()
        app.startService(
            Intent(app, com.siyehua.egnlishstudy.playback.PlaybackNotificationService::class.java)
                .setAction(com.siyehua.egnlishstudy.playback.PlaybackNotificationService.ACTION_TOGGLE_CAPTION)
        )
        when (val s = _uiState.value) {
            is AudioUiState.Playing -> _uiState.value = s.copy(isCaptionOn = captionOn)
            is AudioUiState.Paused -> _uiState.value = s.copy(isCaptionOn = captionOn)
            else -> {}
        }
    }

    /** 静音/取消静音（底部播放器音量按钮） */
    fun toggleMute() {
        android.util.Log.d("CAPTION_DBG", "toggleMute -> isMuted=" + (!isMuted))
        isMuted = !isMuted
        mediaPlayer?.setVolume(if (isMuted) 0f else 1f, if (isMuted) 0f else 1f)
        when (val s = _uiState.value) {
            is AudioUiState.Playing -> _uiState.value = s.copy(isMuted = isMuted)
            is AudioUiState.Paused -> _uiState.value = s.copy(isMuted = isMuted)
            else -> {}
        }
        publishIfOwned(_uiState.value)
    }

    /** 整课循环开关（通知栏循环按钮）：开 → 播完本课重播本课；关 → 播完自动连播下一课 */
    fun toggleLessonLoop() {
        loopLesson = !loopLesson
        _loopingLesson.value = loopLesson
        // 立即把状态刷到通知（保持当前播放/暂停不变）
        when (val s = _uiState.value) {
            is AudioUiState.Playing -> _uiState.value = s.copy(isLoopingLesson = loopLesson)
            is AudioUiState.Paused -> _uiState.value = s.copy(isLoopingLesson = loopLesson)
            else -> {}
        }
        publishIfOwned(_uiState.value)
    }

    fun playNextLesson() {
        val next = queueItems.getOrNull(queueIndex + 1) ?: return
        playAt(queueIndex + 1)
    }

    fun playPrevLesson() {
        val prev = queueItems.getOrNull(queueIndex - 1) ?: return
        playAt(queueIndex - 1)
    }

    private fun playAt(index: Int) {
        val target = queueItems.getOrNull(index) ?: return
        queueIndex = index
        _currentLesson.value = target
        playAll(target)
    }

    /** Play the whole lesson audio and highlight lines as playback advances. */
    fun playAll(content: Content) {
        loopSingle = false
        lineRanges = (content as? Dialogue)
            ?.lines
            ?.map { it.start to it.end }
            .orEmpty()
        matchByTime = lineRanges.isNotEmpty()
        play(content)
    }

    /**
     * Play the whole lesson audio starting at [lineIndex]: seek there and keep
     * playing (no per-sentence clipping, no auto-stop), highlighting lines as
     * playback advances.
     */
    fun playFromLine(content: Content, lineIndex: Int) {
        loopSingle = false
        lessonPlayMode = true
        currentTitle = content.title
        val lines = (content as? Dialogue)?.lines.orEmpty()
        activeDialogueLines = lines.map { it.text }
        currentSentence = lines.getOrNull(lineIndex)?.text.orEmpty().take(80)
        lineRanges = lines.map { it.start to it.end }
        matchByTime = lineRanges.isNotEmpty()
        val startSec = lines.getOrNull(lineIndex)?.start ?: 0.0
        val audioUrl = content.audioUrl
        if (audioUrl.isNullOrBlank()) {
            playSentence(lines.getOrNull(lineIndex)?.text.orEmpty(), lineIndex)
            return
        }

        stop(resetState = false)
        highlightedSentenceIndex = lineIndex
        prepareJob = viewModelScope.launch {
            _uiState.value = AudioUiState.Preparing
            when (val result = ttsAudioManager.ensureRealAudioForContent(content)) {
                is WordAudioResult.Success -> {
                    playlist = listOf(result.uri)
                    playlistDurations = listOf(readDurationMillis(result.uri))
                    totalDurationMillis = playlistDurations.sumOf { it.toLong() }
                    playlistIndex = 0
                    segmentStartMs = (startSec * 1000).toLong().coerceAtLeast(0)
                    segmentEndMs = 0L
                    playCurrent()
                }

                is WordAudioResult.Failure ->
                    playSentence(lines.getOrNull(lineIndex)?.text.orEmpty(), lineIndex)
            }
        }
    }

    /** Play a backend-cut sentence clip (no seeking involved). */
    fun playSegmentUrl(url: String, fallbackText: String, sentenceIndex: Int, title: String? = null, sentence: String? = null) {
        title?.let { currentTitle = it }
        sentence?.let { currentSentence = it.take(80) }
        lessonPlayMode = false
        matchByTime = false
        lineRanges = emptyList()
        loopSingle = false
        stop(resetState = false)
        highlightedSentenceIndex = sentenceIndex
        prepareJob = viewModelScope.launch {
            _uiState.value = AudioUiState.Preparing
            when (val result = ttsAudioManager.ensureRemoteAudio(url)) {
                is WordAudioResult.Success -> {
                    playlist = listOf(result.uri)
                    playlistDurations = listOf(readDurationMillis(result.uri))
                    totalDurationMillis = playlistDurations.sumOf { it.toLong() }
                    playlistIndex = 0
                    segmentStartMs = 0L
                    segmentEndMs = 0L
                    playCurrent()
                }

                is WordAudioResult.Failure -> playSentence(fallbackText, sentenceIndex)
            }
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
        segmentStartMs = 0L
        segmentEndMs = 0L
        loopSingle = false
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
            currentSentenceIndex = activeSentenceIndex(),
            isLoopingSingle = loopSingle,
            isLoopingLesson = loopLesson,
            isMuted = isMuted,
            isCaptionOn = captionOn
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
            isLooping = loopSingle
            if (segmentStartMs > 0) {
                val target = segmentStartMs.coerceAtMost(duration.toLong())
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    // Precise seek to the exact sentence start (not the nearest keyframe).
                    seekTo(target, android.media.MediaPlayer.SEEK_CLOSEST)
                } else {
                    seekTo(target.toInt())
                }
            }
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
        val wasFullLesson = lessonPlayMode
        val wasLoopLesson = loopLesson
        stop(resetState = false)
        // 整课播完 → 循环本课 或 自动连播下一课
        if (wasFullLesson && wasLoopLesson) {
            val current = _currentLesson.value
            if (current != null) {
                playAll(current)
                return
            }
        }
        if (wasFullLesson) {
            val next = queueItems.getOrNull(queueIndex + 1)
            if (next != null) {
                playAt(queueIndex + 1)
                return
            }
        }
        _uiState.value = AudioUiState.Idle
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressJob = viewModelScope.launch {
            while (true) {
                if (segmentEndMs > 0 && currentPlaybackMillis() >= segmentEndMs - 15) {
                    finishPlayback()
                    break
                }
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
            currentSentenceIndex = activeSentenceIndex(),
            isLoopingSingle = loopSingle,
            isLoopingLesson = loopLesson,
            isMuted = isMuted,
            isCaptionOn = captionOn
        )
    }

    private fun activeSentenceIndex(): Int {
        if (matchByTime && lineRanges.isNotEmpty()) {
            val seconds = currentPlaybackMillis() / 1000.0
            val matched = lineRanges.indexOfFirst { seconds >= it.first && seconds < it.second }
            if (matched >= 0) return matched
        }
        return highlightedSentenceIndex ?: playlistIndex
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
        private const val PROGRESS_UPDATE_INTERVAL_MS = 40L
    }
}

sealed class AudioUiState {
    object Idle : AudioUiState()
    object Preparing : AudioUiState()
    data class Playing(
        val currentMillis: Long,
        val totalMillis: Long,
        val currentSentenceIndex: Int,
        val isLoopingSingle: Boolean = false,
        val isLoopingLesson: Boolean = false,
        val isMuted: Boolean = false,
        val isCaptionOn: Boolean = false
    ) : AudioUiState()
    data class Paused(
        val currentMillis: Long,
        val totalMillis: Long,
        val currentSentenceIndex: Int,
        val isLoopingSingle: Boolean = false,
        val isLoopingLesson: Boolean = false,
        val isMuted: Boolean = false,
        val isCaptionOn: Boolean = false
    ) : AudioUiState()
    data class Error(val message: String) : AudioUiState()
}
