# 内容加载与本地缓存

## 职责

- 从后端拉取课程列表与详情（`POST /contents`）。
- 把结果落到本地 SQLite，保证再次打开应用时秒开、且已拉取的课程可离线阅读。
- 为首页的筛选（type / level / source）与分页提供查询能力。

## 涉及文件

| 文件 | 说明 |
| --- | --- |
| `data/FetchDataManager.kt` | 远端拉取 + 本地缓存的门面，UI 只与它交互 |
| `data/ContentCacheDatabase.kt` | SQLite 表结构、读写、筛选查询、收藏与音频缓存表 |
| `data/wordform/WordFormApiClient.kt` | 后端 base URL（`DEFAULT_BASE_URL`）与 HTTP 调用 |
| `ui/ContentViewModel.kt` | 首页状态机：Loading / Success / Error，筛选与分页 |
| `data/LessonQueueHolder.kt` | 首页当前展示顺序，交给播放模块使用 |

## 数据流

```
ContentViewModel
   │  1. loadCachedContent()         ← 先读本地，立即出内容
   │  2. refreshRemoteContent()      ← 再打网络，成功后 upsert 进缓存
   │  3. queryCachedContent(filters, limit, offset)   ← 筛选 / 分页
   ▼
FetchDataManager ── ContentCacheDatabase(SQLite)
                 └─ WordFormApiClient(HTTP)
```

- `ContentViewModel` 是**页面级**作用域（绑定 `list` 路由），离开首页即销毁；播放状态不在这里。
- 分页：`PAGE_SIZE = 20`，`nextOffset` 递增，`hasMore` 由返回条数是否等于页大小推断。

## 内容模型

`model/ContentModels.kt` 定义统一的内容抽象：

- `Content`：id、title、type、level、source、audioUrl、audioStart/End
- `Dialogue` → 由 `DialogueLine` 组成（speaker、text、**trans（中文翻译）**、start、end、audioUrl）
- `Article` / `Blog` / `News`：正文为长文本，阅读时用 `SentenceSplitter` 切成句子

EnglishPod 的每一课都是一个 `DIALOGUE`，每行保留原始文本、原始中文翻译与时间戳——**不做二次识别或机器翻译**。

## 缓存格式

`DialogueLine` 以分隔符拼接后存入 `contents.body`，读取时拆回：

```
speaker::text::start::end::audioUrl::trans
```

分隔符是 `::`（`SPEAKER_SEPARATOR`），按 `limit = 6` 拆分，所以 `trans` 必须始终是最后一个字段。**新增字段只能追加在末尾**，否则老缓存会被解析错位；解析用 `getOrElse` 兜底空值，保证旧数据不崩。

## 已知陷阱

### 1. SQLite LIMIT 只能用逗号格式

Android 的 `SQLiteQueryBuilder` 会校验 limit 字符串，**只接受 `offset, count` 逗号形式**，写成 `count OFFSET offset` 会直接抛：

```
java.lang.IllegalArgumentException: invalid LIMIT clauses:20 OFFSET 0
```

正确写法：

```kotlin
"${offset.coerceAtLeast(0)}, ${limit.coerceAtLeast(1)}"
```

注意逗号形式里**第一个数是 offset、第二个才是数量**，顺序和 `LIMIT x OFFSET y` 相反。

### 2. 中文翻译字段的兼容

`trans` 是后加的字段。老版本缓存里这一列不存在，解析出来是空串，UI 需要容错：收藏列表首次打开句子详情时，会用本地已缓存的课程回填翻译。

### 3. 音频分句由服务端切好

`DialogueLine.audioUrl` 指向 `GET /ting/segment?lesson=N&start=..&end=..`，由服务端用 ffmpeg 切片并前后各补 200 ms。客户端**不要**改为在长音频里 seek，源文件是未标记的 VBR MP3，seek 不精确。
