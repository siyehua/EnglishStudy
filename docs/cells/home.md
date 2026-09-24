# 首页与课程列表

## 职责

- 展示课程列表，支持按类型 / 等级 / 来源筛选。
- 提供分页加载与下拉刷新。
- 顶部提供菜单入口（收藏、字幕设置）。
- 承载全局播放器条。

## 涉及文件

| 文件 | 说明 |
| --- | --- |
| `ui/screens/ContentListScreen.kt` | 首页 UI 与状态消费 |
| `ui/ContentViewModel.kt` | 列表状态、筛选、分页 |
| `data/FetchDataManager.kt` | 数据来源 |
| `data/LessonQueueHolder.kt` | 把展示顺序交给播放模块 |

## 顶部区域

从左到右：

| 控件 | 作用 |
| --- | --- |
| Focus filters | 类型 / 等级 / 来源筛选 |
| Refresh | 重新拉取远端内容 |
| **☰ 菜单** | 下拉菜单：**我的收藏** / **字幕设置** |

菜单必须在最右侧。菜单项是进入收藏与字幕设置的**唯一入口**。

头部会随列表滚动折叠（`collapseFraction`），折叠后高度与字号收缩。

## 列表

- `LazyColumn`，`weight(1f)` 占据剩余高度。
- 每项展示：类型图标、等级徽章、来源、标题、正文预览、时长、Start 按钮。
- 底部有分页加载中的进度指示。
- 列表下方（`Column` 末尾）是全局播放器条。

## 队列同步

列表数据变化时把**当前展示顺序**写入播放模块：

```kotlin
LaunchedEffect(uiState) {
    (uiState as? ContentUiState.Success)?.let { state ->
        LessonQueueHolder.items = state.content
        onSyncLessonQueue(state.content, playerContent?.id)
    }
}
```

这样“下一课”就是列表里的下一篇，且筛选后顺序随之变化。

## 已知陷阱

### 1. 菜单按钮位置

曾经把菜单放在最左侧（收藏图标的位置），与既有交互习惯不符。正确顺序是筛选 → 刷新 → 菜单。

### 2. 列表底部内边距

因为播放器条在 `Column` 里而不是悬浮覆盖，列表的 `contentPadding.bottom` 不需要为播放器预留大空间；不要再用 `bottomBar` 承载播放器（会被裁切，见 `player-bar.md`）。

### 3. 订阅但未传参

曾经出现“字幕设置菜单点了没反应”，原因是 `onOpenCaptionSettings` 参数在 `ContentListScreen` 有默认空实现，`MainActivity` 未传入。新增回调时务必在 `MainActivity` 里接上并检查权限分支。
