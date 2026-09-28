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

## 缓存策略

### 两把钥匙

| 钥匙 | 组成 | 用于 |
| --- | --- | --- |
| **word key** | `v2\|单词` 或 `v2\|form\|单词` / `v2\|phonics\|单词\|IPA` | 不依赖上下文的：发音、词形、读音拆分 |
| **context key** | `v2\|meaning\|单词\|hash(句子)` | 依赖上下文的：中文释义、整句翻译 |

**key 等于真实依赖**，不掺入无关维度。这样：

- 发音、词形、读音拆分**天然跨入口命中**（句子不参与 key）；
- 只有释义需要上下文，miss 时回落到 **word key 的最近一条**。

> 早期把句子（甚至 IPA）塞进词形/读音拆分的 key，导致同一词在不同入口各存一份、
> 永远命中不了，只能靠"词级兜底"补丁维持。**兜底是治症状，收敛 key 才是治本。**

### 版本前缀

所有 key 带 `CACHE_KEY_VERSION`（当前 `v2`）+ `DATABASE_VERSION`。

- 改 key 规则或输出结构 → **提升版本号**，旧缓存视为 miss；
- `onUpgrade` 里直接 `DROP` 这几张查词表（缓存可丢，代价只是首次重新联网）。

### 发音的阴性缓存

接口失败时写入 `phonetic = null`，10 分钟内视为"查过了、暂无"，直接返回空结果而不再联网。

### 聚合接口

客户端优先调 `POST /word-insight`，服务端**一次返回四项**（词形 / 发音 / 读音拆分 / 释义），
替代原来的 4 次请求。聚合失败时自动退回"4 次独立请求"的老路径。

客户端还会先尝试**纯本地**拼装：四项在本地都命中时直接返回，连聚合请求都不发。

## 加载状态

`WordInsightViewModel` 为每个子模块维护独立状态（Idle / Loading / Success / Error），面板按块渲染，某一块失败不影响其他块显示。

## 已知陷阱

### 1. 双击与单击的冲突

阅读区同时要响应“单击播放该句”和“双击查词”。命中判定放在 `ClickableReadingText` 内，不要在外层再叠加点击手势，否则会出现“双击被识别成两次单击”。

### 2. key 要等于真实依赖

历史问题：词形与读音拆分的 key 里塞了句子（甚至 IPA），而它们其实只依赖单词。
结果是同一词在不同入口各存一份、永远命中不了，表现为"刚在课程里查过，外部入口再查又联网"。

规则：**key 只包含该数据真正依赖的维度**。依赖上下文的（释义）用 context key，
不依赖的（发音 / 词形 / 读音拆分）只用 word key。

### 3. 发音接口失败会导致无限重试

后端 `/word-pronunciation` 曾因调外部 IPA 服务超时而返回 **500**。客户端拿到异常后
`phonetic` 为空 → 不写缓存 → 下次仍然 MISS → 再请求 → 再 500，表现为"每次查同一个词
都要等一次超时"。

两处都要防：后端把网络异常降级为可用结果（本地兜底），客户端失败时写**阴性缓存**
并在 TTL 内不重试。只做一个都还会复发。

### 4. 释义面板与播放器条

面板是覆盖层，不要因为它而暂停播放；查词时音频应继续。
