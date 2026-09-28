# 架构设计（SPEC）

本文说明整体分层、数据流、状态机与接口契约。**具体模块与功能的设计见
[`cells/`](cells/)，源码本身不写注释，以本文与 cells 为唯一说明来源。**

## 目录

- [模块地图](#模块地图)
- [导航](#导航)
- [分层：单例播放核心 + 纯 UI 订阅](#分层单例播放核心--纯-ui-订阅)
- [状态流](#状态流)
- [PlaybackBus](#playbackbus)
- [内容加载](#内容加载)
- [查词](#查词)
- [主题](#主题)
- [模块文档索引](#模块文档索引)

## 模块地图

```
Android/app/src/main/java/com/siyehua/egnlishstudy/
├── MainActivity.kt                 Navigation host；创建并注入 ViewModel
├── model/
│   └── ContentModels.kt            Content / Dialogue / DialogueLine / Article / Blog / News
├── data/
│   ├── FetchDataManager.kt         Remote fetch + local cache façade
│   ├── ContentCacheDatabase.kt     SQLite cache (contents, favourites, audio cache)
│   ├── TtsAudioManager.kt          Audio download / TTS / caching
│   ├── SentenceSplitter.kt         Splits article bodies into sentences
│   ├── LessonQueueHolder.kt        Home-screen lesson order handed to playback
│   ├── CaptionStyleStore.kt        Desktop-caption style + on/off preference
│   ├── ManualFavorite.kt           手动收藏共用逻辑（判定 / 翻译 / 写入）
│   ├── AppLog.kt                   轻量日志（写文件 + 轮转，供导出排查）
│   └── wordform/                   Word-form API client, models, repositories
├── capture/
│   ├── CaptureTextActivity.kt      PROCESS_TEXT / SEND 系统入口，静默加入单词本
│   └── WordLookupActivity.kt       WEB_SEARCH 系统入口，弹出释义对话框
├── playback/
│   ├── PlaybackCore.kt             播放核心单例：唯一 MediaPlayer、队列、当前课/当前句
│   ├── PlaybackBus.kt              核心 → 前台服务的单向通道（状态快照 + 命令）
│   ├── PlaybackNotificationService.kt  前台媒体服务、通知、MediaSession
│   └── DesktopCaptionOverlay.kt    悬浮字幕窗口（TYPE_APPLICATION_OVERLAY）
├── ui/
│   ├── ContentViewModel.kt         Home list state, filters, paging
│   ├── ContentAudioViewModel.kt    薄壳：转发命令 + 触发 PlaybackCore.init
│   ├── components/GlobalPlayerBar.kt   Shared player bar used by every screen
│   ├── screens/                    List, Detail, Favourites（含手动添加）, SentenceDetail, CaptionSettings
│   ├── wordinsight/                Word insight sheet + clickable reading text
│   └── theme/                      Color, Theme, Type
```

## 导航

`MainNavigation` (in `MainActivity.kt`) holds a `NavHost` with the destinations:

| Route | Screen |
| --- | --- |
| `list` | `ContentListScreen` (home) |
| `detail` | `ContentDetailScreen` |
| `favorites` | `FavoritesScreen` |
| `sentence` | `SentenceDetailScreen` |
| `word` | `WordInsightScreen` |
| `captionSettings` | `CaptionSettingsScreen` |

`selectedContent` / `selectedWord` / `selectedFavorite` are `remember`ed state
in `MainNavigation`; screens read them when they are (re)composed.

## 分层：单例播放核心 + 纯 UI 订阅

```
PlaybackCore（object，进程内唯一，持有唯一 MediaPlayer）
   ├── 唯一状态出口：uiState / currentLesson / currentSentenceEvent
   │                 captionOnFlow / loopingLessonFlow
   ├── 唯一命令入口：play / pause / resume / stop / next / prev / toggle*
   └── 唯一队列：setLessonQueue

UI（可多实例、可销毁重建，只订阅 + 发命令）
   ├── 首页 / 详情页 / 设置页   → 订阅核心，展示"正在播放的课"
   ├── 页面自己的标题           → 展示"用户正在浏览的课"
   ├── 悬浮字幕                 → 订阅核心的当前句
   └── 通知服务                 → 订阅 PlaybackBus（由核心发布）
```

规则：

1. **数据驱动 UI**：UI 不持有播放状态、不自己算进度，一切从核心读。
2. **谁播放谁提交**：想播哪一课，就把那一课交给核心。
   详情页点播放 → 播本页课程；首页点某课 → 播该课。
3. **允许"页面课 ≠ 播放课"**：两者语义不同，必须能同时正确表达。
4. **页面不跟随播放课自动切页**。
5. `PlaybackCore.init(application)` 在首个 ViewModel 创建时调用，之后进程内唯一。

`ContentAudioViewModel` 退化为薄壳：只转发命令并触发 `init`，不再持有播放状态。
`ContentViewModel` 保持页面级（列表数据），`WordInsightViewModel` 保持页面级。

## 状态流

```
PlaybackCore
   ├── uiState: StateFlow<AudioUiState>        Idle | Preparing | Playing | Paused | Error
   │       ├── lessonId、currentSentenceIndex(Int?)、position、duration
   │       ├── isLoopingSingle / isLoopingLesson / isMuted / isCaptionOn
   │       └── 被播放器条、详情页、PlaybackBus 消费
   ├── currentLesson: StateFlow<Content?>      正在播放的课
   ├── currentSentenceEvent: StateFlow<CurrentSentenceEvent?>
   │       当前句的唯一出口：lessonId + sentenceIndex + text；null 下标表示清空
   ├── captionOnFlow: StateFlow<Boolean>       字幕开关（含持久化偏好）
   └── loopingLessonFlow: StateFlow<Boolean>   整课循环开关
```

### PlaybackBus

核心与前台服务通过 `PlaybackBus` 单向通信（核心发布，服务消费）：

- `PlaybackBus.info: StateFlow<PlaybackInfo?>` — 服务据此渲染通知；`null` 表示关闭服务。
- `PlaybackBus.commands: SharedFlow<Command>` — `PAUSE / RESUME / STOP / NEXT /
  PREV / TOGGLE_LESSON_LOOP / TOGGLE_CAPTION`。
- `PlaybackBus.ownerId` — 记录发布者；核心是唯一发布者，恒为 `"core"`。

服务 → 核心方向使用显式 action（广播）：

- Notification buttons are `PendingIntent.getBroadcast` into a receiver
  registered by the service (`ACTION_PLAY/PAUSE/STOP/NEXT/PREV/TOGGLE_LOOP/
  TOGGLE_CAPTION`). Broadcasts are used instead of `startService` because they
  are not subject to background-service start restrictions.
- When the overlay permission is missing the service broadcasts
  `ACTION_NEED_OVERLAY_PERMISSION`; `MainActivity` listens and opens the system
  permission page.

## 内容加载

`FetchDataManager`:

1. `loadCachedContent()` — read everything from SQLite first (instant start).
2. `refreshRemoteContent()` — `POST /contents`, then upsert into the cache.
3. `queryCachedContent(types, levels, sources, limit, offset)` — filtered,
   paged reads for the home list.

Note: Android's `SQLiteQueryBuilder` only accepts the `offset, count` form of a
LIMIT clause; `count OFFSET offset` throws `IllegalArgumentException` on API ≤ 29.

## 查词

`WordInsightSheet` is fed by `WordInsightViewModel`, which calls the backend
through the repositories in `data/wordform/`:

- `/word-form` → headword, relation (e.g. *past tense of*), contraction expansion
- `/word-pronunciation` → IPA
- `/word-phonics` → syllable / phonics breakdown
- `/word-meaning` → Chinese meanings + sentence translation

A local-first resolver on the backend handles contractions and irregular forms
before any LLM fallback is used.

## 主题

`ui/theme/Color.kt` defines the palette (`StudyGreen`, `StudyMint`,
`StudyBackground`, `StudyDarkSurface`, …) and `Theme.kt` maps it to Material 3
light/dark schemes plus an 8 dp shape scale. Screens should read colours from
`MaterialTheme.colorScheme` (so dark mode works) and only use the raw `Study*`
colours for brand accents on the green header surfaces.

## 模块文档索引

| 模块 | 文档 |
| --- | --- |
| 播放引擎（播放器、队列、循环、连播） | [cells/playback.md](cells/playback.md) |
| 通知与前台服务 | [cells/notification.md](cells/notification.md) |
| 桌面悬浮字幕 | [cells/caption.md](cells/caption.md) |
| 全局播放器条 | [cells/player-bar.md](cells/player-bar.md) |
| 首页与课程列表 | [cells/home.md](cells/home.md) |
| 课程详情与逐句精听 | [cells/lesson-detail.md](cells/lesson-detail.md) |
| 查词与释义 | [cells/word-insight.md](cells/word-insight.md) |
| 收藏 | [cells/favourites.md](cells/favourites.md) |
| 内容加载与本地缓存 | [cells/content.md](cells/content.md) |
| 主题与视觉规范 | [cells/theme.md](cells/theme.md) |
| 日志 | [cells/logging.md](cells/logging.md) |
| 构建、发版与调试 | [cells/build.md](cells/build.md) |

## 约束

1. **源码不写注释。** 代码即说明；设计意图、为什么这样做、不要怎么做，全部写在 cells 文档里。
2. **改功能必须同步改文档。** 对应 cell 文档是唯一入口，过期即视为缺陷。
3. **坑要写下来。** 任何“看起来可以这样写但会坏”的地方，都必须在对应 cell 的「已知陷阱」中记录现象与后果。
