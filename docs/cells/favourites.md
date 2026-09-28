# 收藏

## 职责

- 保存句子与单词两类收藏。
- 提供收藏列表，支持进入句子详情、单词详情与删除。
- 与课程详情页的收藏按钮联动（同一份数据）。
- 支持在收藏夹内**手动添加**单词或句子（自动翻译后按类型入库）。
- 支持在**任意 App** 里长按选中文字或分享文字，直接加入单词本（系统级入口）。

## 涉及文件

| 文件 | 说明 |
| --- | --- |
| `ui/screens/FavoritesScreen.kt` | 收藏列表 + 手动添加弹窗 |
| `capture/CaptureTextActivity.kt` | 系统级静默入口：`PROCESS_TEXT` / `SEND` |
| `capture/WordLookupActivity.kt` | 系统级查词入口：`WEB_SEARCH`，弹出释义对话框 |
| `data/ManualFavorite.kt` | 手动收藏的共用逻辑（判定 / 翻译 / 写入） |
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

## 系统级入口（任意 App）

选中文字后，系统选择菜单里会出现「**添加到单词本**」；分享菜单里也会出现本应用。
两个入口都指向 `CaptureTextActivity`。

```xml
<activity android:name=".capture.CaptureTextActivity"
    android:exported="true"
    android:label="@string/capture_add_to_wordbook"
    android:theme="@style/Theme.TranslucentNoDisplay"
    android:noHistory="true"
    android:excludeFromRecents="true">
    <intent-filter>
        <action android:name="android.intent.action.PROCESS_TEXT" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/plain" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/plain" />
    </intent-filter>
</activity>
```

取文本：`PROCESS_TEXT` → `EXTRA_PROCESS_TEXT`；`SEND` → `EXTRA_TEXT`。

流程与行为：

1. 立即 `Toast`「已添加到单词本」，然后 `finish()` **回到原来的 App**，用户不被打断；
2. 翻译与写入在**后台静默完成**，成功不提示，失败也不提示；
3. 用户在 App 内打开该条目时会重新翻译（有缓存则直接用缓存）。

入口能力差异（系统决定，非应用可控）：

| 入口 | 触达 | 行为 |
| --- | --- | --- |
| `PROCESS_TEXT` | 使用系统文本选择菜单的 App（Chrome、UC、QQ 浏览器、今日头条等） | 静默入库 + Toast，返回原 App |
| `SEND` | 支持"分享"的绝大多数 App（含 Notion） | 静默入库 + Toast，返回原 App |
| `WEB_SEARCH` | 列出"网页搜索"类项的宿主（含 Notion） | 弹出释义对话框，可查看后再加入 |

菜单项名称固定取自 Activity 的 `android:label`，**不能按选中内容变化**。

## 系统级查词入口（WEB_SEARCH）

有些宿主（例如 Notion 这类自绘编辑器的选择菜单）**不列出 `PROCESS_TEXT` 项**，
但会列出「网页搜索」类的项。为了覆盖这类 App，额外声明了 `ACTION_WEB_SEARCH`：

```xml
<activity android:name=".capture.WordLookupActivity"
    android:exported="true"
    android:label="@string/capture_lookup"
    android:theme="@style/Theme.TranslucentNoDisplay"
    android:noHistory="true">
    <intent-filter>
        <action android:name="android.intent.action.WEB_SEARCH" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```

叠放在调用方任务之上，靠三个声明做到：

| 属性 | 作用 |
| --- | --- |
| `android:taskAffinity=""` | 不新建自己的任务，叠加在当前 App 之上（不会把 Notion 切到后台） |
| `android:excludeFromRecents="true"` | 不出现在最近任务里 |
| `android:launchMode="singleTop"` | 重复触发不叠加多个实例 |

行为与静默入口的区别：**它会弹出一个底部面板**，交互与 App 内双击单词完全一致。

- **单词**：直接复用 `WordInsightSheet`（`ModalBottomSheet` + `WordInsightBody`），
  读音、音标、读音拆分、中文释义、词形变化、例句与双击面板**完全一致**；
- **句子**：同样用 `ModalBottomSheet`，显示原文 + 整句翻译 + 「加入单词本」；
- 文本取自 `SearchManager.QUERY`；类型判定与翻译复用 `ManualFavorite`；
- **不提供"关闭"按钮**：点上半部分遮罩、返回手势、下拉即可关闭，与原有交互一致；
- 关闭不做任何写入。

面板是 `singleTop`，重复触发时通过 `onNewIntent` **就地刷新**内容，不重叠多个实例。

> **不要另写一套居中卡片**：早期版本自己实现了居中对话框，导致观感与 App 内面板不一致，
> 也没有"从下往上弹出 + 可拖动"的交互。统一走 `ModalBottomSheet`。

> **不要给 `WordInsightBody` 再套一层 `verticalScroll`**：它自身已经可滚动，
> 叠加会产生无限高度约束并抛
> `IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints`。

> **注意**：`WEB_SEARCH` 只保证拿到宿主填进 `query` 的文本，**拿不到所在句子的上下文**，
> 因此词形关系、上下句翻译这类依赖上下文的信息会缺失，`sentence` 传空串，
> 与手动添加的行为一致。

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

### 5. WEB_SEARCH 入口要叠在调用方之上

`WordLookupActivity` 若不设 `taskAffinity=""`，会启动在**自己应用的任务栈**里，
表现为"一点就把当前 App（如 Notion）切到后台"。

三个属性缺一不可：`taskAffinity=""`（叠加在调用方任务）、`excludeFromRecents`（不进最近任务）、
主题透明（背景可见）。

### 6. 自绘选择菜单的宿主只列 WEB_SEARCH

表现：Chrome / UC / QQ 浏览器 / 今日头条 的长按菜单里有「添加到单词本」，
但 Notion 里没有；Notion 的选择菜单只有「网页搜索」以及 QQ 浏览器等 App 的「翻译」项。

原因：宿主自己决定把哪些 action 排进选择菜单。Notion 只收录 `WEB_SEARCH` 类项，
不收录 `PROCESS_TEXT`。这类 App 的项点击后会**跳到另一个 App**，正是因为它们用的是
`WEB_SEARCH` 语义（去别处处理），而不是 `PROCESS_TEXT`（就地处理）。

应对：额外提供 `WEB_SEARCH` 入口，用对话框展示释义后再决定是否收藏。
无法保证每个宿主都显示这一项，最终仍由宿主决定。

### 7. 后台写入不能挂在 Activity 生命周期上

`CaptureTextActivity` 在 `onCreate` 里就 `finish()` 返回原 App。
如果翻译/写入用 activity 自己的 `CoroutineScope`（并在 `onDestroy` 里 `cancel()`），
协程会被取消，**收藏静默丢失**——表现为"Toast 弹了但列表里没有"。

正确做法是把任务丢给**不随 Activity 销毁的进程级作用域**
（当前的 `CaptureWork.scope`），Activity 只负责取文本、提示、结束。

### 8. 手动收藏的课名是占位符

`lessonTitle = "手动添加"`、`contentId = ""`，所以：

- 列表副标题显示"手动添加"而不是课程名；
- 句子详情里没有"打开原文"的跳转目标；
- 老收藏的"按原课回填翻译"逻辑不会命中（`contentId` 为空时跳过），
  翻译只在添加时写入一次。
