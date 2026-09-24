package com.siyehua.egnlishstudy.playback

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 播放状态快照：由 ViewModel 播放时发布，通知服务消费后渲染媒体通知。
 */
data class PlaybackInfo(
    val title: String,
    /** 当前正在播放/高亮的句子文本，通知栏展示用 */
    val subtitle: String,
    val isPlaying: Boolean,
    val isPaused: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val loopSingle: Boolean,
    /** 整课循环开关（通知栏循环按钮） */
    val loopLesson: Boolean = false
)


/**
 * ViewModel（播放控制）与通知服务之间的轻量总线。
 *
 * 只允许「当前正在播放的那个 ViewModel 实例」发布状态/响应命令，
 * 避免多个屏幕各自创建的 ViewModel 实例互相干扰。
 */
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
