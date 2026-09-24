package com.siyehua.egnlishstudy.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.provider.Settings
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.media.app.NotificationCompat.MediaStyle
import com.siyehua.egnlishstudy.MainActivity
import com.siyehua.egnlishstudy.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 媒体播放前台服务：只负责通知栏 + 锁屏控制（MediaSession），
 * 真正的播放仍在 ViewModel 的 MediaPlayer 中，两者通过 [PlaybackBus] 通信。
 *
 * 通知提供 播放/暂停、停止 按钮；划掉通知 = 停止播放。
 */
class PlaybackNotificationService : Service() {

    private var session: MediaSessionCompat? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastNotifyKey = ""
    @Volatile private var captionEnabled = false
    private val captionStyleStore by lazy { com.siyehua.egnlishstudy.data.CaptionStyleStore(this) }
    @Volatile private var captionStyle = com.siyehua.egnlishstudy.data.CaptionStyle()

    override fun onBind(intent: Intent?): IBinder? = null

    private val actionReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_PLAY -> PlaybackBus.send(PlaybackBus.Command.RESUME)
                ACTION_PAUSE -> PlaybackBus.send(PlaybackBus.Command.PAUSE)
                ACTION_STOP -> PlaybackBus.send(PlaybackBus.Command.STOP)
                ACTION_NEXT -> PlaybackBus.send(PlaybackBus.Command.NEXT)
                ACTION_PREV -> PlaybackBus.send(PlaybackBus.Command.PREV)
                ACTION_TOGGLE_LOOP -> PlaybackBus.send(PlaybackBus.Command.TOGGLE_LESSON_LOOP)
                ACTION_TOGGLE_CAPTION -> toggleCaptionWithPref()
                ACTION_UPDATE_CAPTION_STYLE -> applyCaptionStyle()
            }
        }
    }

    /** 切换字幕开关并持久化偏好 + 立即显示/隐藏悬浮字幕 */
    private fun toggleCaptionWithPref() {
        captionEnabled = !captionEnabled
        captionStyleStore.setCaptionEnabled(captionEnabled)
        if (captionEnabled && !Settings.canDrawOverlays(this)) {
            captionEnabled = false
            captionStyleStore.setCaptionEnabled(false)
            sendBroadcast(Intent(ACTION_NEED_OVERLAY_PERMISSION).setPackage(packageName))
        } else if (captionEnabled) {
            PlaybackBus.info.value?.let {
                DesktopCaptionOverlay.show(this, it.subtitle.ifBlank { it.title }, captionStyle)
            }
        } else {
            DesktopCaptionOverlay.hide(this)
        }
        PlaybackBus.send(PlaybackBus.Command.TOGGLE_CAPTION)
    }

    /** 重新读取字幕样式并立即应用到已显示的悬浮字幕 */
    private fun applyCaptionStyle() {
        captionStyle = captionStyleStore.load()
        if (captionEnabled) {
            PlaybackBus.info.value?.let {
                DesktopCaptionOverlay.show(this, it.subtitle.ifBlank { it.title }, captionStyle)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // 启动时先读取用户保存的字幕样式与开关偏好
        captionStyle = captionStyleStore.load()
        captionEnabled = captionStyleStore.isCaptionEnabled()
        // 按钮点击用广播（不受后台服务启动限制，进程活着就一定收到）
        val filter = android.content.IntentFilter().apply {
            addAction(ACTION_PLAY); addAction(ACTION_PAUSE); addAction(ACTION_STOP)
            addAction(ACTION_NEXT); addAction(ACTION_PREV)
            addAction(ACTION_TOGGLE_LOOP); addAction(ACTION_TOGGLE_CAPTION)
            addAction(ACTION_UPDATE_CAPTION_STYLE)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(actionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(actionReceiver, filter)
        }
        session = MediaSessionCompat(this, "EnglishStudyPlayback").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = PlaybackBus.send(PlaybackBus.Command.RESUME)
                override fun onPause() = PlaybackBus.send(PlaybackBus.Command.PAUSE)
                override fun onStop() = PlaybackBus.send(PlaybackBus.Command.STOP)
                override fun onSkipToNext() = PlaybackBus.send(PlaybackBus.Command.NEXT)
                override fun onSkipToPrevious() = PlaybackBus.send(PlaybackBus.Command.PREV)
            })
            isActive = true
        }
        scope.launch {
            PlaybackBus.info.collect { info ->
                if (info == null) {
                    DesktopCaptionOverlay.hide(this@PlaybackNotificationService)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collect
                }
                updateSession(info)
                val key = "${info.title}|${info.isPlaying}|${info.isPaused}|${info.positionMs / 1000}|${info.loopSingle}"
                if (key != lastNotifyKey) {
                    lastNotifyKey = key
                    notificationManager().notify(NOTIFICATION_ID, buildNotification(info))
                }
                // 桌面悬浮字幕：实时跟随当前句子；字幕开关开启时显示（样式每次都读最新）
                if (captionEnabled) {
                    DesktopCaptionOverlay.show(
                        this@PlaybackNotificationService,
                        info.subtitle.ifBlank { info.title },
                        captionStyleStore.load()
                    )
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService 启动后必须立即进入前台
        startForegroundCompat(buildNotification(PlaybackBus.info.value ?: placeholderInfo()))
        when (intent?.action) {
            ACTION_PLAY -> PlaybackBus.send(PlaybackBus.Command.RESUME)
            ACTION_PAUSE -> PlaybackBus.send(PlaybackBus.Command.PAUSE)
            ACTION_STOP -> PlaybackBus.send(PlaybackBus.Command.STOP)
            ACTION_NEXT -> PlaybackBus.send(PlaybackBus.Command.NEXT)
            ACTION_PREV -> PlaybackBus.send(PlaybackBus.Command.PREV)
            ACTION_TOGGLE_LOOP -> PlaybackBus.send(PlaybackBus.Command.TOGGLE_LESSON_LOOP)
            ACTION_UPDATE_CAPTION_STYLE -> applyCaptionStyle()
            ACTION_TOGGLE_CAPTION -> toggleCaptionWithPref()
        }
        if (PlaybackBus.info.value == null) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        DesktopCaptionOverlay.hide(this@PlaybackNotificationService)
        scope.cancel()
        session?.release()
        session = null
        super.onDestroy()
    }

    private fun updateSession(info: PlaybackInfo) {
        val state = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY
                    or PlaybackStateCompat.ACTION_PAUSE
                    or PlaybackStateCompat.ACTION_STOP
                    or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(
                if (info.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                info.positionMs,
                if (info.isPlaying) 1f else 0f
            )
            .build()
        session?.setPlaybackState(state)
        session?.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, info.title)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, info.durationMs)
                .build()
        )
    }

    private fun buildNotification(info: PlaybackInfo): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val playPauseIntent = PendingIntent.getBroadcast(
            this, 1,
            Intent(ACTION_PLAY).setPackage(packageName).setAction(if (info.isPlaying) ACTION_PAUSE else ACTION_PLAY),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val nextIntent = PendingIntent.getBroadcast(
            this, 2,
            Intent(ACTION_NEXT).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val captionIntent = PendingIntent.getBroadcast(
            this, 6,
            Intent(ACTION_TOGGLE_CAPTION).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val prevIntent = PendingIntent.getBroadcast(
            this, 3,
            Intent(ACTION_PREV).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val loopIntent = PendingIntent.getBroadcast(
            this, 4,
            Intent(ACTION_TOGGLE_LOOP).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getBroadcast(
            this, 5,
            Intent(ACTION_STOP).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // 应用图标（自适应图标，取前景渲染成圆形底）
        val appIcon = runCatching {
            ContextCompat.getDrawable(this, R.mipmap.ic_launcher)?.toBitmap(108, 108)
        }.getOrNull()
        val statusText = when {
            info.isPaused -> "已暂停 · ${fmt(info.positionMs)}"
            info.loopSingle -> "单句循环 · ${fmt(info.positionMs)}"
            info.loopLesson -> "整课循环 · ${fmt(info.positionMs)}"
            else -> "正在播放 · ${fmt(info.positionMs)}"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_headset)
            .setLargeIcon(appIcon)
            .setContentTitle(info.title)
            .setContentText(info.subtitle.ifBlank { statusText })
            .setSubText(statusText)
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setOngoing(info.isPlaying)
            .setGroup(GROUP_KEY)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                if (info.loopLesson) android.R.drawable.ic_menu_rotate else android.R.drawable.ic_menu_upload,
                if (info.loopLesson) "循环·开" else "循环",
                loopIntent
            )
            .addAction(
                android.R.drawable.ic_media_previous,
                "上一课",
                prevIntent
            )
            .addAction(
                if (info.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (info.isPlaying) "暂停" else "播放",
                playPauseIntent
            )
            .addAction(
                android.R.drawable.ic_media_next,
                "下一课",
                nextIntent
            )
            .addAction(
                android.R.drawable.ic_menu_mylocation,
                if (captionEnabled) "字幕·开" else "字幕",
                captionIntent
            )
            .setStyle(
                MediaStyle()
                    .setMediaSession(session?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
        if (info.durationMs > 0) {
            builder.setProgress(info.durationMs.toInt(), info.positionMs.toInt(), false)
        }
        return builder.build()
    }

    private fun placeholderInfo() = PlaybackInfo(
        title = "英语听力",
        subtitle = "",
        isPlaying = false,
        isPaused = true,
        positionMs = 0,
        durationMs = 0,
        loopSingle = false
    )

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "音频播放",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "课程音频播放控制"
            setShowBadge(false)
        }
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    private fun fmt(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
    }

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 42
        private const val GROUP_KEY = "com.siyehua.egnlishstudy.playback.GROUP"
        const val ACTION_PLAY = "com.siyehua.egnlishstudy.playback.PLAY"
        const val ACTION_PAUSE = "com.siyehua.egnlishstudy.playback.PAUSE"
        const val ACTION_STOP = "com.siyehua.egnlishstudy.playback.STOP"
        const val ACTION_NEXT = "com.siyehua.egnlishstudy.playback.NEXT"
        const val ACTION_PREV = "com.siyehua.egnlishstudy.playback.PREV"
        const val ACTION_TOGGLE_LOOP = "com.siyehua.egnlishstudy.playback.TOGGLE_LOOP"
        const val ACTION_TOGGLE_CAPTION = "com.siyehua.egnlishstudy.playback.TOGGLE_CAPTION"
        const val ACTION_UPDATE_CAPTION_STYLE = "com.siyehua.egnlishstudy.playback.UPDATE_CAPTION_STYLE"
        const val ACTION_NEED_OVERLAY_PERMISSION = "com.siyehua.egnlishstudy.playback.NEED_OVERLAY_PERMISSION"
    }
}
