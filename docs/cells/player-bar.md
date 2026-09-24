# 全局播放器条

首页、课程详情、字幕设置三个页面共用的播放控制组件。

## 职责

- 展示当前课程与**正在播放的句子**。
- 提供五个控制：整课循环、上一课、播放/暂停、下一课、字幕开关。
- 展示播放进度。
- 在任意页面都能操作播放，无需回到详情页。

## 涉及文件

| 文件 | 说明 |
| --- | --- |
| `ui/components/GlobalPlayerBar.kt` | 组件本体 |
| `ui/ContentAudioViewModel.kt` | 状态与回调来源 |
| `ui/screens/ContentListScreen.kt` | 首页宿主 |
| `ui/screens/ContentDetailScreen.kt` | 详情页宿主 |
| `ui/screens/CaptionSettingsScreen.kt` | 设置页宿主 |

## 布局

```
┌───────────────────────────────────────────┐
│ 1: Difficult Customer                     │  标题 = 课程名
│ Yes, we are. Today we're going to be…     │  副标题 = 当前句子
│  🔁    ⏮    ▶/⏸    ⏭    📺               │  五个按钮
│  ▓▓▓▓▓░░░░░░░░░░░░░░░░░░░░░               │  进度条
└───────────────────────────────────────────┘
```

- 布局刻意与媒体通知保持一致：标题 / 副标题 / 一行五个按钮 / 进度条。
- 副标题优先显示 `currentSentenceFlow`（当前英文句子），无内容时回退为状态文案（如“点播放开始收听”）。
- 进度条仅在 `Playing` / `Paused` 时显示。

## 边缘延伸

- 背景**延伸到底部手势区**：宿主页面的 Scaffold 使用
  `contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)`，不预留底部 inset。
- 内容不侵入手势区：组件内部用 `navigationBarsPadding()` 把文字与按钮托起。
- 组件本身**没有外边距**，全宽贴边，只有上方两个圆角。

与标题栏的处理方式一致：背景铺满，内容避让。

## 宿主接入

首页与设置页把组件放在页面 `Column` 的最底部（LazyColumn 使用 `weight(1f)`，组件紧随其后）。接入时需要提供：

```kotlin
GlobalPlayerBar(
    audioState = ...,        // ViewModel.uiState
    content = ...,           // ViewModel.currentLesson
    subtitle = ...,          // ViewModel.currentSentenceFlow
    isLoopingLesson = ...,   // ViewModel.loopingLessonFlow
    isCaptionOn = ...,       // ViewModel.captionOnFlow
    onToggleLessonLoop = ...,
    onPlayPrevLesson = ...,
    onPlayNextLesson = ...,
    onTogglePlayback = ...,  // Idle 且无当前课程时回退到队列第一课
    onToggleCaption = ...
)
```

播放按钮需要处理“尚未选择任何课程”的情况：`content` 为空时取 `LessonQueueHolder.items.firstOrNull()` 作为起点。

## 已知陷阱

### 1. 不要用 `Scaffold.bottombar` 承载

`bottomBar` 会给内容施加高度约束，实测会导致组件下半部分（按钮行）被裁掉、无法点击。应放在页面主 `Column` 的末尾。

### 2. 组件内的按钮尺寸必须一致

五个按钮统一 `44.dp`、图标 `24.dp`，播放/暂停按钮不要放大——放大后与通知栏观感不一致，也不符合设计要求。

### 3. 副标题不是进度文案

副标题的语义是“当前正在播放的句子”，不要改回“正在播放 · 0:44 / 7:24”。进度由进度条承担。
