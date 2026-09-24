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

## 加载状态

`WordInsightViewModel` 为每个子模块维护独立状态（Idle / Loading / Success / Error），面板按块渲染，某一块失败不影响其他块显示。

## 已知陷阱

### 1. 双击与单击的冲突

阅读区同时要响应“单击播放该句”和“双击查词”。命中判定放在 `ClickableReadingText` 内，不要在外层再叠加点击手势，否则会出现“双击被识别成两次单击”。

### 2. 释义面板与播放器条

面板是覆盖层，不要因为它而暂停播放；查词时音频应继续。
