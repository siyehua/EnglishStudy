# Playback, Notification And Lessons

## Player bar (in-app)

`ui/components/GlobalPlayerBar.kt` is a single composable rendered by the home
screen, the lesson detail screen and the caption settings screen.

```
┌───────────────────────────────────────────┐
│ 1: Difficult Customer                     │  title
│ Yes, we are. Today we're going to be…     │  subtitle = current sentence
│  🔁    ⏮    ▶/⏸    ⏭    📺               │  five buttons
│  ▓▓▓▓▓░░░░░░░░░░░░░░░░░░░░░               │  progress
└───────────────────────────────────────────┘
```

Rules:

- The background extends to the bottom of the screen, **including the gesture
  navigation area**; the content uses `navigationBarsPadding()` so it stays
  above it. The host screens therefore pass
  `contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)`.
- There is no outer margin: the bar is full-bleed, with only the top corners
  rounded.
- The subtitle shows `currentSentenceFlow` (the line currently being played).
  When no line is available it falls back to a status string.

## Audio model

`ContentAudioViewModel` owns a single `MediaPlayer` and a playlist of `Uri`s:

- **Lesson playback** loads the full lesson audio into a one-item playlist and
  keeps `segmentStartMs` / `segmentEndMs` to know where the requested range
  begins and ends. `matchByTime` plus `lineRanges` map the playback position to
  the highlighted line.
- **Sentence clips** load a single already-cut file; `segmentEndMs` is used to
  stop exactly at the end.
- **TTS fallback** is used when no real audio is available or a download fails:
  each sentence becomes its own playlist entry and playback advances through the
  list.

Progress is polled every 40 ms (`PROGRESS_UPDATE_INTERVAL_MS`) to emit
`AudioUiState.Playing` with the latest position, so the UI, the notification and
the caption stay in sync.

## Lesson queue

`LessonQueueHolder.items` is filled by the home screen with the lesson list in
the order currently displayed (after filters). The ViewModel mirrors it via
`setLessonQueue(items, currentId)` and keeps a `queueIndex`.

- `playNextLesson()` / `playPrevLesson()` move within the queue.
- `finishPlayback()` implements the end-of-lesson behaviour:
  - lesson loop **on** → replay the same lesson;
  - lesson loop **off** → auto-advance to the next lesson in the queue;
  - end of queue → go to `Idle`.
- Only whole-lesson playback auto-advances; single-sentence loops and clips do
  not (`lessonPlayMode` distinguishes them).

## Background playback and leaving the detail screen

- The audio ViewModel is **Activity-scoped**, so navigating back to the home
  screen does not destroy it and playback continues.
- The detail screen must not stop audio when it is disposed; the previous
  `DisposableEffect { onDispose { stop() } }` was removed for this reason.
- Stopping is explicit only: the play/pause button, the notification, a system
  media key, or the process going away.

## Foreground service and notification

`PlaybackNotificationService` is a `foregroundServiceType="mediaPlayback"`
service. It:

- publishes a `MediaStyle` notification (title, current sentence, progress,
  prev / play-pause / next);
- registers a `MediaSessionCompat` with a `PlaybackState` carrying
  `ACTION_PLAY|PAUSE|STOP|SKIP_TO_NEXT|SKIP_TO_PREVIOUS`, so lock-screen,
  headset and Bluetooth controls work;
- stops itself and cancels the notification when `PlaybackBus.info` becomes
  `null`.

Required manifest entries:

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

`MainActivity` requests `POST_NOTIFICATIONS` on Android 13+.

### Why the notification has only the standard buttons

Android 13 changed how `MediaStyle` notifications are rendered for apps with
`targetSdk >= 33`: **non-standard actions are stripped**, and only actions
present in the `PlaybackState` are shown. Apps that still show five custom
buttons (e.g. 汽水音乐) ship `targetSdk = 30`.

Therefore this app keeps the notification to the three standard buttons
(previous / play-pause / next) and surfaces the app-specific controls in the
in-app `GlobalPlayerBar` instead. Lowering `targetSdk` to keep custom buttons is
deliberately avoided — see `docs/BUILD.md`.

### Notification button plumbing

- The service registers a `BroadcastReceiver` for
  `ACTION_PLAY / ACTION_PAUSE / ACTION_STOP / ACTION_NEXT / ACTION_PREV /
  ACTION_TOGGLE_LOOP / ACTION_TOGGLE_CAPTION / ACTION_UPDATE_CAPTION_STYLE`.
- `PendingIntent.getBroadcast` is used for the notification buttons (immune to
  background-service-start restrictions; no need to call `startForeground`).
- Custom actions that the in-app UI triggers (e.g. toggling the caption) use
  `Context.startService` while the service is already in the foreground; using
  `startForegroundService` there would crash with *"did not then call
  Service.startForeground()"*.

## Media controls handled

| Source | Result |
| --- | --- |
| In-app player bar | play/pause, prev/next lesson, lesson loop, caption toggle |
| Notification | previous / play-pause / next |
| Lock screen / headset / Bluetooth | play, pause, stop, skip next/prev |
| Leaving the detail screen | keeps playing |
| Leaving the app (process alive) | keeps playing; notification stays |
| Explicit stop / pause | only then does playback stop |
