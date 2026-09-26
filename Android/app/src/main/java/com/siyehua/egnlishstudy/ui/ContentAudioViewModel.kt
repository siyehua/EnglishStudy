package com.siyehua.egnlishstudy.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.siyehua.egnlishstudy.model.Content
import com.siyehua.egnlishstudy.playback.PlaybackCore

class ContentAudioViewModel(application: Application) : AndroidViewModel(application) {
    init {
        PlaybackCore.init(application)
    }

    fun playAll(content: Content) = PlaybackCore.playAll(content)
    fun togglePlayback(content: Content) = PlaybackCore.togglePlayback(content)
    fun playSentence(sentence: String, sentenceIndex: Int) = PlaybackCore.playSentence(sentence, sentenceIndex)
    fun playSegmentUrl(url: String, fallbackText: String, sentenceIndex: Int, title: String? = null, sentence: String? = null) =
        PlaybackCore.playSegmentUrl(url, fallbackText, sentenceIndex, title, sentence)
    fun playFavoriteUrl(url: String) = PlaybackCore.playFavoriteUrl(url)
    fun loopSegmentUrl(url: String, sentenceIndex: Int, title: String? = null, sentence: String? = null) =
        PlaybackCore.loopSegmentUrl(url, sentenceIndex, title, sentence)
    fun playFromLine(content: Content, lineIndex: Int) = PlaybackCore.playFromLine(content, lineIndex)
    fun playNextLesson() = PlaybackCore.playNextLesson()
    fun playPrevLesson() = PlaybackCore.playPrevLesson()
    fun setLessonQueue(items: List<Content>, currentId: String) = PlaybackCore.setLessonQueue(items, currentId)
    fun toggleLessonLoop() = PlaybackCore.toggleLessonLoop()
    fun toggleCaptionOverlay() = PlaybackCore.toggleCaptionOverlay()
    fun toggleMute() = PlaybackCore.toggleMute()
    fun stop() = PlaybackCore.stop()
}
