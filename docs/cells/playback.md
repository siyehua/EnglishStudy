# 播放引擎

## 职责

- 拥有唯一的 `MediaPlayer`，管理播放列表、进度、暂停/继续、停止。
- 维护**课程队列**，实现上一课 / 下一课 / 整课循环 / 播完自动连播。
- 计算“当前播放到哪一句”，驱动高亮、通知栏副标题与悬浮字幕。
- 把播放状态发布给服务（通知）与 UI（播放器条）。

## 涉及文件

| 文件 | 说明 |
| --- | --- |
| `ui/ContentAudioViewModel.kt` | 全部播放逻辑与状态 |
| `playback/PlaybackBus.kt` | 与前台服务之间的状态/命令总线 |
| `data/TtsAudioManager.kt` | 音频下载、TTS 合成、文件缓存 |
| `ui/components/GlobalPlayerBar.kt` | UI 消费方 |
| `data/LessonQueueHolder.kt` | 课程顺序来源 |

## 作用域：Activity 级

`ContentAudioViewModel` 在 `MainActivity` 中通过 `viewModel(viewModelStoreOwner = activity)` 创建，**不是**页面级。这样：

- 从课程详情返回首页，播放**继续**，不会被销毁。
- 只有显式暂停/停止、系统媒体键、或进程结束才会停止。

> 不要把它改回页面级 `viewModel()`，否则返回首页就会静音；也不要在详情页的 `DisposableEffect.onDispose` 里调用 `stop()`。

## 状态

```
AudioUiState
├── Idle                       无播放
├── Preparing                  正在下载/准备音频
├── Playing(currentMillis, totalMillis, currentSentenceIndex,
│           isLoopingSingle, isLoopingLesson, isMuted, isCaptionOn)
├── Paused (同上)
└── Error(message)
```

ViewModel 额外暴露四个独立流，供 UI 与通知使用：

| Flow | 用途 |
| --- | --- |
| `uiState` | 播放器条、详情页、通知总线 |
| `currentLesson` | 当前课程（用于切课同步 UI） |
| `currentSentenceFlow` | 播放器条副标题（当前英文句子） |
| `captionOnFlow` | 字幕开关（含持久化偏好） |
| `loopingLessonFlow` | 整课循环开关 |

进度每 **40 ms** 轮询一次（`PROGRESS_UPDATE_INTERVAL_MS`）并发射 `Playing`，保证 UI / 通知 / 字幕同步。

## 三种播放来源

| 入口 | 行为 | `lessonPlayMode` |
| --- | --- | --- |
| `play(content)` / `playAll` | 整课音频（单条播放列表 + 时间区间） | `true` |
| `playFromLine(content, i)` | 从第 i 句起整课连播，边播边高亮 | `true` |
| `playSentence` / `playSegmentUrl` / `loopSegmentUrl` / `playSegment` / `playFavoriteUrl` | 单句或 TTS 片段 | `false` |

`lessonPlayMode` 决定播完后是否自动连播下一课——只有整课播放才会。

## 高亮与自动滚动

- `matchByTime` 打开时，用 `lineRanges`（每句的 start/end）把播放位置映射成句子下标；否则回退到 `highlightedSentenceIndex` 或播放列表下标。
- 详情页根据 `currentSentenceIndex` 高亮并**按需**滚动：只有当目标句不在可见项里、或被底部播放器遮挡时才滚动，避免打断用户手动浏览。

## 课程队列

```
LessonQueueHolder.items                ← 首页按当前筛选后的顺序写入
ContentAudioViewModel.setLessonQueue(items, currentId)
   → queueItems / queueIndex
```

- `playNextLesson()` / `playPrevLesson()` 在队列内前后移动，切换后 UI、通知、字幕同步。
- `playAt(index)` 更新 `queueIndex`、`currentLesson`，然后 `playAll(target)`。

## 播放结束的处理

`finishPlayback()` 是唯一的分支点：

```
整课播放结束
├── 整课循环开启 → 重播本课
├── 队列还有下一课 → 自动连播下一课
└── 都没有 → Idle（通知与服务随之关闭）
```

## 已知陷阱

### 1. `init` 块必须在 `_uiState` 声明之后

`viewModelScope` 使用 `Dispatchers.Main.immediate`，`launch` 会**在构造函数里同步执行**。如果 `init` 里启动的协程引用了还没初始化的属性（例如把 `init` 写在 `_uiState` 之前），会直接抛：

```
NullPointerException: Attempt to invoke interface method
'... MutableStateFlow.collect(...)' on a null object reference
```

所以依赖 `_uiState` 的初始化逻辑必须放在属性声明之后。

### 2. 每次快照都必须带上所有开关字段

`emitPlayingState()` 每 40 ms 覆盖一次状态。**任何遗漏的字段都会被“弹回默认值”**：例如漏带 `isMuted`，点击静音后 40 ms 就被重置，表现为“静音按钮点了没用”。同理适用于 `isLoopingLesson`、`isCaptionOn`。

### 3. 只有队列持有者才响应通知命令

通知/锁屏按钮发来的命令是全局的。为避免多个 ViewModel 实例（不同屏幕各自创建）抢答，发布状态时记录 `PlaybackBus.ownerId`，只有 `ownerId == 自己` 的实例才处理命令。

### 4. 只有“整课”播放才允许自动连播

单句循环、收藏夹片段播完后**不能**跳下一课。用 `lessonPlayMode` 区分；新增播放入口时记得设置它，否则要么不连播、要么乱连播。

### 5. 离开详情页不要停播

历史实现里有 `DisposableEffect { onDispose { stop() } }`，会让返回首页时音乐中断。已移除；如需在特定场景停止，请在业务逻辑里显式调用。
