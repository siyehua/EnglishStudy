# 浏览器扩展

## 职责

- 在 PC 浏览器里划词加入单词本，和手机端共用同一份收藏。
- 独立的工具栏入口：手动输入添加 + 查看最近添加。
- 不需要上架 Chrome 应用商店，以「加载已解压的扩展程序」方式使用。

## 涉及文件

| 文件 | 说明 |
| --- | --- |
| `Extension/manifest.json` | MV3 清单 |
| `Extension/background.js` | 右键菜单、翻译、写入服务端、页面提示、角标 |
| `Extension/popup.html` / `popup.js` | 工具栏弹窗 |
| `Extension/options.html` / `options.js` | 后端地址设置 |
| `Extension/icons/` | 复用手机端 App 图标（`mipmap-xxxhdpi/ic_launcher.webp` 转 PNG） |
| `Backend/app/favorites/store.py` | 收藏的落库与归并（与手机端共用） |

## 为什么能和手机端共存

扩展**不直连数据库**，只调后端两个已有接口：

| 用途 | 接口 |
| --- | --- |
| 取翻译 | `POST /word-meaning` |
| 写收藏 | `POST /favorites/sync`（`bucket = "default"`） |

`/favorites/sync` 是「推本地全量 + 返回服务端全量」。扩展只推**一条**新记录即可：
服务端按 `syncId` 归并、按 `updatedAt` 取新，多余的本地列表对扩展无害（忽略响应即可）。

手机端下次启动或打开收藏页时会拉全量，于是电脑上添加的词会出现在手机里。

## 与手机端一致的规则

**类型判定**（和 `ManualFavorite.looksLikeSingleWord` 一致）：
无空白字符，且每个字符都是字母、`'`、`’`、`-` → 单词；否则 → 句子。

**翻译取法**（和 `ManualFavorite.translate` 一致）：

| 类型 | 请求 | 取哪个字段 |
| --- | --- | --- |
| 单词 | `word = 全文`，`sentence = ""` | `meanings` 拼成 `词性 释义；…`，为空时退回 `sentenceChinese` |
| 句子 | `word = 第一个词`，`sentence = 全文` | `sentenceChinese` |

**写入字段**：`lessonTitle = "浏览器"`，`contentId = ""`，`audioUrl = null`，
`startTime` / `endTime` = 0，`syncId` 为新 UUID，`updatedAt = Date.now()`。

> `lessonTitle` 特意不叫「手动添加」：手机端列表用它当副标题，
> 写成「浏览器」就能一眼看出这条是电脑上加的。

## 已知陷阱

### 1. 自定义后端地址要同步改 `host_permissions`

MV3 里扩展后台的 `fetch` 只在 `host_permissions` 命中时才绕过 CORS。
设置页改了地址但没把域名加进清单，请求会被浏览器拦住（后端没有 CORS 头）。

### 2. 右键菜单只在 `onInstalled` 里创建

菜单由浏览器持久化，service worker 重启不会丢。所以只用
`chrome.runtime.onInstalled` + `contextMenus.removeAll` 重建，不要在顶层 `create`，
否则每次唤醒 SW 都会重复注册或报 id 冲突。

### 3. 页面提示不能直接塞 DOM 样式

`executeScript` 注入的提示用 **shadow DOM** 承载，避免被页面 CSS 影响；
注入依赖 `activeTab`（右键菜单点击算用户手势），所以不需要 `<all_urls>`。

### 4. 不要把翻译逻辑写在 popup 里

popup 每次打开都会重建，逻辑放 `background.js`，popup 只发
`{ type: "add" }` / `{ type: "recent" }` 消息。否则两份代码会漂移。

### 5. Chrome 稳定版禁用了 `--load-extension`

命令行加载扩展从 Chrome 137 起被禁用（日志里是
`--load-extension is not allowed in Google Chrome, ignoring`），只能在
`chrome://extensions/` 里手动「加载已解压的扩展程序」。
自动化验证可以用 `--pack-extension`，它会校验清单合法性。
