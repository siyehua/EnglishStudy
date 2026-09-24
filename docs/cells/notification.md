# 通知与前台服务

## 职责

- 让播放脱离 UI 存活：前台服务保证进程不被回收。
- 提供系统级控制入口：通知栏、锁屏、耳机线控、蓝牙。
- 作为悬浮字幕的宿主（悬浮窗由服务创建与销毁）。

## 涉及文件

| 文件 | 说明 |
| --- | --- |
| `playback/PlaybackNotificationService.kt` | 前台服务：通知、MediaSession、广播接收器、悬浮字幕控制 |
| `playback/PlaybackBus.kt` | 与服务通信的状态快照与命令 |
| `playback/DesktopCaptionOverlay.kt` | 悬浮字幕窗口（详见 `caption.md`） |

## 服务声明

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

<service
    android:name=".playback.PlaybackNotificationService"
    android:exported="false"
    android:foregroundServiceType="mediaPlayback" />
```

`MainActivity` 在 Android 13+ 上请求 `POST_NOTIFICATIONS`。

## 通信模型

服务与 ViewModel 同进程但解耦，两个方向各用一套机制。

### ViewModel → 服务：`PlaybackBus`

```kotlin
PlaybackBus.info: StateFlow<PlaybackInfo?>          // 服务据此渲染通知；null 表示关闭服务
PlaybackBus.commands: SharedFlow<Command>           // PAUSE/RESUME/STOP/NEXT/PREV/TOGGLE_*
PlaybackBus.ownerId: String?                        // 当前“播放所有者”标识
```

- 服务订阅 `info`，任何变化都重绘通知；`null` 时 `stopForeground()` + `stopSelf()`。
- ViewModel 订阅 `commands`，但**只有 `ownerId` 与自己相等的实例才执行**，避免多个页面实例抢答。
- ViewModel 只在“自己已经是 owner”时才发布 `null`，防止其他屏幕的实例误关掉正在播放的通知。

### 服务 → ViewModel：显式 Action

通知按钮用 `PendingIntent.getBroadcast` 发给服务内注册的 `BroadcastReceiver`：

| Action | 含义 |
| --- | --- |
| `ACTION_PLAY` / `ACTION_PAUSE` / `ACTION_STOP` | 播放控制 |
| `ACTION_NEXT` / `ACTION_PREV` | 上/下一课 |
| `ACTION_TOGGLE_LOOP` | 整课循环 |
| `ACTION_TOGGLE_CAPTION` | 悬浮字幕开关 |
| `ACTION_UPDATE_CAPTION_STYLE` | 样式变更后立即重绘字幕 |
| `ACTION_NEED_OVERLAY_PERMISSION` | 缺少悬浮窗权限，通知 App 去授权 |

接收器再把命令转成 `PlaybackBus.commands` 发给 ViewModel。

## 通知内容

标准 `MediaStyle` 通知，展示：

- 标题：课程名
- 正文：**当前播放的英文句子**
- 三个按钮：上一课 / 播放-暂停 / 下一课
- 进度条

`MediaSessionCompat` 同步注册 `PlaybackState`（`ACTION_PLAY|PAUSE|STOP|SKIP_TO_NEXT|SKIP_TO_PREVIOUS`），因此锁屏、耳机线控、蓝牙都能控制。

## 为什么通知里只有三个按钮

Android 13（API 33）起，**`targetSdk >= 33` 的应用，`MediaStyle` 通知里非标准的自定义 action 会被系统剥离**，只保留 `PlaybackState` 中声明的标准动作。

- 本项目 `targetSdk = 36` → 只能显示上一课/播放/下一课。
- 能显示五个自定义按钮的应用（如汽水音乐）是把 `targetSdk` 压在 30，走旧渲染规则。

结论：应用内专属控制（整课循环、字幕开关、暂停/播放、上下课）放在 **App 内的全局播放器条**；通知栏只承担系统标准控制。不要为了让通知多几个按钮而降 `targetSdk`。

## 已知陷阱

### 1. 通知按钮必须走广播，不要 `startService`

用 `startService` 做按钮的 `PendingIntent` 会受后台启动限制影响（尤其在 APP 退到后台时），点击可能无效。广播不受该限制，且进程存活时必定送达。

### 2. 已在前台的服务不要用 `startForegroundService` 再次拉起

在服务已经处于前台运行时，用 `startForegroundService()` 触发一次自定义 action（例如切换字幕），若该次 `onStartCommand` 没有紧接着调用 `startForeground()`，系统会抛：

```
android.app.RemoteServiceException: Context.startForegroundService() did not then call
Service.startForeground()
```

App 内触发服务 action 时使用 `Context.startService`（此时 APP 在前台，允许），并在 `onStartCommand` 开头始终 `startForeground(...)`。

### 3. 通知不是“每 40 ms 重绘一次”

进度每 40 ms 变化，若每次都 `notify()` 会被系统限流（`Package enqueue rate` 丢弃）。服务用 `lastNotifyKey`（标题+状态+秒级进度）去重，只在关键信息变化时更新。

### 4. 停止播放要同时关掉字幕

`info == null` 时除了 `stopForeground` 与 `stopSelf`，还必须 `DesktopCaptionOverlay.hide()`，否则字幕会残留在屏幕上。
