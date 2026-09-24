# 收藏

## 职责

- 保存句子与单词两类收藏。
- 提供收藏列表，支持进入句子详情、单词详情与删除。
- 与课程详情页的收藏按钮联动（同一份数据）。

## 涉及文件

| 文件 | 说明 |
| --- | --- |
| `ui/screens/FavoritesScreen.kt` | 收藏列表 |
| `ui/screens/SentenceDetailScreen.kt` | 句子详情 |
| `ui/wordinsight/WordInsightScreen.kt` | 单词详情（全屏释义） |
| `data/ContentCacheDatabase.kt` | `favorite` 表读写 |

## 数据结构

`FavoriteRecord`：

| 字段 | 说明 |
| --- | --- |
| `kind` | `sentence` 或 `word` |
| `text` | 句子原文 / 单词 |
| `audioUrl` | 句子音频（服务端切好的片段） |
| `contentId` | 所属课程 id |
| `translation` | 中文翻译 |
| `lessonTitle` | 课程名（用于展示与定位） |
| `startTime` / `endTime` | 在原课音频中的区间 |

句子收藏的去重键是 `(kind, text, lessonTitle, start)`。

## 列表

- 句子与单词混合，按时间倒序。
- 每项可删除，删除后列表立即刷新。
- 从详情页进入收藏页，返回时列表会自动刷新。

## 句子详情

- 沉浸式头部：返回、课程名、**打开原文**、单句循环。
- 点击句子播放该片段。
- 下方「释义」区块展示中文翻译，可折叠。

## 已知陷阱

### 1. 旧数据缺少翻译

`translation` 是后加字段，老版本保存的收藏为空。首次打开这类句子详情时，用本地缓存的课程内容**回填翻译**，避免显示空白。

### 2. 收藏状态与详情页同步

详情页的收藏按钮通过“关键词 + 起始时间”匹配收藏记录来判定高亮状态；匹配逻辑要与写入时使用的字段一致，否则会出现“已收藏但按钮未高亮”。

### 3. 单词收藏不带音频

单词条目没有 `audioUrl`，播放走 TTS 回退路径。
