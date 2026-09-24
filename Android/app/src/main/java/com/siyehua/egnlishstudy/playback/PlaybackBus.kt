package com.siyehua.egnlishstudy.playback

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackInfo(
    val title: String,

    val subtitle: String,
    val isPlaying: Boolean,
    val isPaused: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val loopSingle: Boolean,

    val loopLesson: Boolean = false
)

object PlaybackBus {
    enum class Command { PAUSE, RESUME, STOP, NEXT, PREV, TOGGLE_LESSON_LOOP, TOGGLE_CAPTION }

    private val _info = MutableStateFlow<PlaybackInfo?>(null)
    val info: StateFlow<PlaybackInfo?> = _info.asStateFlow()

    private val _commands = MutableSharedFlow<Command>(extraBufferCapacity = 8)
    val commands: SharedFlow<Command> = _commands.asSharedFlow()

    @Volatile
    var ownerId: String? = null
        private set

    fun publish(publisherId: String, info: PlaybackInfo?) {
        ownerId = publisherId
        _info.value = info
    }

    fun send(command: Command) {
        _commands.tryEmit(command)
    }
}
