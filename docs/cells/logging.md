# 日志

用于"出问题时把日志发出来定位"，不追求高性能埋点。

## 涉及文件

| 文件 | 说明 |
| --- | --- |
| `data/AppLog.kt` | 写文件、512 KB 轮转、单线程后台写入 |
| `MainActivity.kt` | 启动时 `AppLog.init`；菜单「导出日志」用 FileProvider 分享 |
| `res/xml/file_paths.xml` | FileProvider 路径（`files/logs/`） |
| `quick`：各 Repository | 在 HIT / MISS 处打点 |

## 行为

- 日志落在 `files/logs/app.log`，超过 512 KB 轮转为 `app.log.1`；
- `AppLog.log(tag, message)` **立即返回**，实际写盘在单线程后台完成，不阻塞调用方；
- 入口：首页 **☰ 菜单 → 导出日志**，通过系统分享把文件发出；
- 打点范围**刻意克制**：只在查词链路记录
  `HIT` / `MISS -> network` / `FAILED`，以及聚合接口的
  `aggregate OK` / `aggregate from local cache` / `aggregate FAILED`。

## 为什么不用 Mars xlog

xlog 用 mmap + 批量落盘，性能高出几个数量级，适合高频全量埋点；代价是引入 native
`.so`（包体积增加）与加密日志（导出后需解密）。

本项目的日志量极低（一次查词 3–6 条），调用方又不阻塞，因此**轻量实现足够**。
若将来要做全量埋点（播放进度、列表滚动、网络层），再替换 `AppLog` 的实现即可——
调用方只依赖 `AppLog.log` 这一个接口。

## 已知陷阱

### 1. 必须在每个入口初始化

`AppLog.init` 只写在 `MainActivity` 时，从**系统入口**（`PROCESS_TEXT` / `SEND` /
`WEB_SEARCH`）直接进入的进程不会建目录，日志为空。三个 Activity 都要初始化。

### 2. 不要给高频路径打点

播放进度每 40 ms 刷新一次。若在该路径写日志，轻量实现会堆积（每次 append 都要
open/write/close）。需要时先换成缓冲写入或 xlog。
