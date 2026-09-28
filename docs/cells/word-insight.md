# 查词与释义

## 职责

- 双击阅读区中的任意单词 → 打开释义面板。
- 面板展示：发音、音标、中文释义、词形关系、原句翻译。
- 支持逐词发音（优先使用词典音频，回退到 TTS）。

## 涉及文件

| 文件 | 说明 |
| --- | --- |
| `ui/wordinsight/ClickableReadingText.kt` | 渲染可点击单词，识别双击与命中词 |
| `ui/wordinsight/ClickedWord.kt` | 命中结果（单词 + 所在句） |
| `ui/wordinsight/WordInsightSheet.kt` | 释义面板（底部弹层形态） |
| `ui/wordinsight/WordInsightScreen.kt` | 全屏形态（从收藏进入） |
| `ui/wordinsight/WordInsightViewModel.kt` | 并发加载各子模块状态 |
| `data/wordform/*` | 后端接口与仓库 |

## 后端接口

Base URL：`https://handwriter.asia/english`（见 `wordform/WordFormApiClient.kt`）。

| 接口 | 返回 |
| --- | --- |
| `POST /word-form` | 词形归并：headword、relation（如 *past tense of write*）、缩写展开 |
| `POST /word-pronunciation` | IPA 音标 |
| `POST /word-phonics` | 音节 / 拼读拆分 |
| `POST /word-meaning` | 中文释义（按词性分组）+ 整句翻译 |

词形解析是**本地优先**的：后端先用内置的缩写表与不规则变化表，再加保守的后缀规则，LLM 只作为兜底。

## 四张缓存表

| 表 | 键 | 兜底 |
| --- | --- | --- |
| `word_meaning_cache` | `(词, 句子)` | 词级（最近一条） |
| `word_form_cache` | `(词, 句子)` | 词级 |
| `word_phonics_cache` | `(词, 句子, ipa)` | 词级 |
| `word_pronunciation_cache` | `(词)` | —（单键，天然命中） |

**发音的阴性缓存**：接口失败时不再抛异常，而是写入一条 `phonetic = null` 的记录，
10 分钟内视为"查过了、暂无"，直接返回空结果而**不再联网**，避免后端抖动时反复重试。

## 释义缓存

`word_meaning_cache` 表按 **`(normalized, stableHash(sentence))`** 作主键 —— 同一个单词在不同句子里
各有一份缓存，因为多义项的释义依赖上下文。

查询顺序（`WordMeaningRepository.resolve`）：

1. `(词, 句子)` 精确命中 → 直接用；
2. **词级兜底**：`(词)` 最近一次更新的任意句子缓存 → 直接用；
   若当前带句子，顺手把这条复制成句级缓存，避免下次再走兜底；
3. 都没有 → 调 `POST /word-meaning`，`found == true` 时写入缓存。

第 2 步是为了让**不同入口之间共用缓存**。各入口传的 `sentence` 不同：

| 入口 | sentence |
| --- | --- |
| 课程内双击单词 | 该词所在的那一句 |
| 手动添加（「＋」） | `""` |
| 系统入口（`PROCESS_TEXT` / `SEND`） | `""` |
| 系统查词（`WEB_SEARCH`） | `""` |

没有兜底时，双击缓存过的词在手动词典里会**再次联网**（键不同）；有了兜底就复用了。
代价是：词级兜底可能给出"别的句子"的义项，本项目接受（翻译兼顾多个意思）。

## 加载状态

`WordInsightViewModel` 为每个子模块维护独立状态（Idle / Loading / Success / Error），面板按块渲染，某一块失败不影响其他块显示。

## 已知陷阱

### 1. 双击与单击的冲突

阅读区同时要响应“单击播放该句”和“双击查词”。命中判定放在 `ClickableReadingText` 内，不要在外层再叠加点击手势，否则会出现“双击被识别成两次单击”。

### 2. 缓存的键必须包含句子，且要有词级兜底

释义缓存主键是 `(词, 句子)`。若只按词存，不同上下文的义项会互相覆盖；
若只按 `(词, 句子)` 存而**没有**词级兜底，则各入口（句子不同）之间无法复用，
表现为"刚在课程里查过，手动词典里再查又联网"。

两者都要：句级精确命中优先，词级兜底次之，最后才联网。

### 3. 发音接口失败会导致无限重试

后端 `/word-pronunciation` 曾因调外部 IPA 服务超时而返回 **500**。客户端拿到异常后
`phonetic` 为空 → 不写缓存 → 下次仍然 MISS → 再请求 → 再 500，表现为"每次查同一个词
都要等一次超时"。

两处都要防：后端把网络异常降级为可用结果（本地兜底），客户端失败时写**阴性缓存**
并在 TTL 内不重试。只做一个都还会复发。

### 4. 释义面板与播放器条

面板是覆盖层，不要因为它而暂停播放；查词时音频应继续。
