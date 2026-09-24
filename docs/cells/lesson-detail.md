# 课程详情与逐句精听

## 职责

- 展示一课的全部台词，一行一句。
- 点击句子从该句开始整课连播，并高亮/滚动到当前句。
- 每句提供三个动作：收藏、单句循环、翻译。
- 双击单词打开释义面板。

## 涉及文件

| 文件 | 说明 |
| --- | --- |
| `ui/screens/ContentDetailScreen.kt` | 详情页、句子卡片、动作按钮 |
| `ui/ContentAudioViewModel.kt` | 播放与高亮 |
| `ui/wordinsight/ClickableReadingText.kt` | 可点击单词的富文本 |
| `ui/wordinsight/WordInsightSheet.kt` | 释义面板 |

## 句子卡片

```
┌───────────────────────────────────────┐
│ Did you fix my car? I'm still …       │  ← 英文原文
│ 你修好我的车了吗？我还在修。            │  ← 可选译文（同一气泡内）
└───────────────────────────────────────┘
  ❤️ 收藏   🔁 循环   🌐 翻译
```

- 译文与原文在**同一个气泡内**换行显示，不是独立的第二张卡片。
- 三个按钮只在“当前选中句”上出现；选中句由播放进度驱动（点击与自动播放共用同一个状态）。
- 播放推进到下一句时，上一句的按钮与译文**自动收起**。

## 选中状态

页面只有一个 `activeLineIndex`，**只由"当前句事件"驱动**（点击本身也会立刻产生事件）：

```kotlin
LaunchedEffect(sentenceEvent) {
    val event = sentenceEvent ?: return@LaunchedEffect
    if (event.lessonId != content.id) return@LaunchedEffect   // 不是本课，忽略
    activeLineIndex = event.sentenceIndex                      // null 即清空
}
```

规则：

- 事件必须带 `lessonId`，与 `content.id` 不一致时**完全忽略**（既不选中也不清空）；
- `sentenceIndex == null`（切课、加载中、出错）→ 清空选中；
- 高亮下标同样按课号过滤：`audioState.currentSentenceIndexFor(content.id)`，不匹配时视为无下标；
- 因此不会出现“手动点过的那句一直挂着按钮”，也不会出现“切课时选中句来回跳”。

### 页面是否跟随播放课

进入页面时记录当时的播放课，只有**播放课发生变更**（下一课/上一课/自动连播）才切换页面内容：

```kotlin
var followedLessonId by remember(content.id) { mutableStateOf(activeLesson?.id) }
LaunchedEffect(activeLesson?.id) {
    val lesson = activeLesson ?: return@LaunchedEffect
    if (lesson.id == followedLessonId) return@LaunchedEffect
    followedLessonId = lesson.id
    if (lesson.id != content.id) onUpdateContent(lesson)
}
```

打开一门**非播放中**的课程时，页面必须保持不变；无条件跟随会把用户正在看的课强行换掉。

## 翻译按钮

- 译文可见性 = `选中该句 && 用户点开翻译 && 该句有译文`，纯派生状态，不使用 `LaunchedEffect`。
- 再次点击收起；选中句变化时自动收起。

## 自动滚动

```
目标句不可见，或被底部播放器遮挡 → animateScrollToItem(itemIndex)
否则不动
```

- 列表第 0 项是 “Dialogue practice” 标题，句子 `i` 在列表中的下标是 `i + 1`。滚动时不要漏掉这个偏移。
- 只在必要时滚动，避免打断用户手动浏览。

## 已知陷阱

### 1. 播放器条副标题也要按课号过滤

详情页的播放器条在显示"当前句"时要确认事件属于本课
（`sentenceEvent?.takeIf { it.lessonId == content.id }`），否则会出现"页面是第 1 课、
副标题却是第 5 课的句子"。首页与设置页的播放器条是全局的，显示正在播放的课程即可。

### 2. 列表项偏移

忘记 `+1` 会滚到上一句，表现为“滚过去了但看不到正在播的那句”。

### 3. 按钮显示条件

按钮可见性必须只依赖唯一的选中状态。曾经的实现里 `isActive` 同时判断“手动点击”和“正在播放”两个状态，导致两句同时显示按钮。

### 4. 译文渲染位置

译文必须渲染在原文同一个 `Surface` 内部；放在 `Surface` 之外会变成“气泡下面另起一块”，不符合设计。

### 5. 后台图标语义

`使用 Material Icons`：收藏 `Favorite` / `FavoriteBorder`，循环 `Repeat`，翻译 `GTranslate`（该版本图标库没有 `AutoMirrored.Translate`）。
