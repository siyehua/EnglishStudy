# 收藏

## 职责

- 保存句子与单词两类收藏。
- 提供收藏列表，支持进入句子详情、单词详情与删除。
- 与课程详情页的收藏按钮联动（同一份数据）。
- 支持在收藏夹内**手动添加**单词或句子（自动翻译后按类型入库）。

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
- 标题栏右侧有「＋」按钮，用于手动添加。

## 手动添加

点「＋」弹出输入框，输入单词或句子后自动翻译并按类型收藏。

**类型判定**：无空白字符且全部为字母、`'`、`-` → 单词；否则 → 句子。

| 类型 | 翻译来源 | 写入字段 |
| --- | --- | --- |
| 单词 | `POST /word-meaning`（`word = 输入`，`sentence = ""`），把 `meanings` 拼成"词性 释义；…" | `kind = "word"`，`text = 单词` |
| 句子 | `POST /word-meaning`（`word = 首词`，`sentence = 输入`），取 `sentenceChinese` | `kind = "sentence"`，`text = 原句` |

两种类型都写入：

| 字段 | 值 |
| --- | --- |
| `audioUrl` | `null`（没有原课音频片段） |
| `contentId` | `""` |
| `lessonTitle` | `手动添加` |
| `startTime` / `endTime` | `0.0` |

翻译在提交后进行，期间按钮显示「翻译中」，失败时在弹窗内提示且**不写入**收藏
（`翻译失败` / `没有获取到翻译` / `保存失败` 三种提示）。

### 手动收藏的发音

手动添加没有 `audioUrl`，发音改走后端 TTS：

- **句子**：`SentenceDetailScreen` 在没有 `clipUrl` 时调用
  `PlaybackCore.playSentence(text, 0)`，它内部用 `TtsAudioManager.ensureAudioForText` 合成；
- **单词**：进入单词详情页，由 `WordInsightViewModel` 走
  `ensureAudioForWord` 合成读音。

也就是说"有原课音频就播原声，没有就用 TTS"，两条路径共用同一套缓存。

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

### 4. 手动收藏必须有兜底发音

手动添加的条目 `audioUrl = null`。句子详情若仍按"必须有 `clipUrl` 才播放"实现，
点句子会毫无反应。正确做法是 `clipUrl == null` 时回退到
`PlaybackCore.playSentence(text, 0)`（TTS）。

### 5. 手动收藏的课名是占位符

`lessonTitle = "手动添加"`、`contentId = ""`，所以：

- 列表副标题显示"手动添加"而不是课程名；
- 句子详情里没有"打开原文"的跳转目标；
- 老收藏的"按原课回填翻译"逻辑不会命中（`contentId` 为空时跳过），
  翻译只在添加时写入一次。
