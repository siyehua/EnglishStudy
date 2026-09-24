# 桌面悬浮字幕

悬浮在所有应用之上、实时显示当前播放句子的字幕条，类似音乐 App 的桌面歌词。

## 职责

- 在屏幕上层绘制当前英文句子。
- 随播放进度实时换句（跨课程也跟随）。
- 不干扰其他应用的操作。
- 记住开关状态与样式偏好。

## 涉及文件

| 文件 | 说明 |
| --- | --- |
| `playback/DesktopCaptionOverlay.kt` | 悬浮窗的创建、更新、销毁 |
| `data/CaptionStyleStore.kt` | 样式与开关的持久化 |
| `ui/screens/CaptionSettingsScreen.kt` | 设置界面 |
| `playback/PlaybackNotificationService.kt` | 字幕的宿主与开关处理 |

## 行为

| 项 | 行为 |
| --- | --- |
| 开关入口 | 播放器条的 📺 按钮；通知里的字幕 action |
| 内容 | 当前播放句子（`currentSentenceFlow`） |
| 换句 | 跟随播放自动更新 |
| 长句 | **完整换行，不截断**（`maxLines = Int.MAX_VALUE`） |
| 触摸 | 完全穿透（不消费、不抢焦点），可以正常操作其他应用 |
| 偏好 | 开关状态与样式本地持久化，重启后自动恢复 |
| 停止播放 | 自动隐藏 |

## 实现要点

- 通过 `WindowManager` + `TYPE_APPLICATION_OVERLAY` 添加一个 `TextView`。
- 窗口标志必须同时包含 `FLAG_NOT_FOCUSABLE` 与 `FLAG_NOT_TOUCHABLE`：前者不抢输入焦点，后者让触摸事件穿透到下层应用。缺任意一个都会破坏使用体验。
- 位置：`gravity = TOP or CENTER_HORIZONTAL`，`y = (heightPixels - 200) * yPercent / 100`。
- 视图被复用：已存在时走 `updateViewLayout`，否则 `addView`。
- **每次 `show()` 都重新应用样式**（文本、颜色、背景、字号），这样服务重启或用户改设置后都能立即生效。

## 样式模型

`CaptionStyleStore` 使用 `caption_style` 这份 SharedPreferences：

| Key | 含义 | 默认值 | 范围 |
| --- | --- | --- | --- |
| `y_percent` | 垂直位置（距顶部百分比） | `12` | 5 – 85 |
| `text_size` | 字号（sp） | `16` | 12 – 32 |
| `text_color` | 文字颜色（ARGB） | `0xFFFFFFFF` 白 | — |
| `bg_color` | 背景颜色（ARGB） | `0xB3000000` 半透明黑 | — |
| `caption_enabled` | 字幕开关 | `false` | — |

读写都做范围钳制，避免非法值把字幕推到屏幕外。

## 设置界面

入口：首页 **☰ 菜单 → 字幕设置**。

- 顶部**实时预览**卡片。
- 位置滑块 5%–85%，字号滑块 12–32 sp。
- 文字颜色：白 / 浅黄 / 浅青 / 浅橙 / 浅绿 / 浅粉。
- 背景颜色：半透明黑 / 半透明白 / 深绿 / 深蓝 / 深橙 / 无背景。
- 改动**立即保存并实时推送**给服务（`ACTION_UPDATE_CAPTION_STYLE`），正在显示的字幕会当场变化。
- 视觉风格与应用一致（绿色圆角头部 + 卡片内容），颜色取自 `MaterialTheme.colorScheme`，随系统深色模式切换。

## 权限

悬浮窗需要 `SYSTEM_ALERT_WINDOW`。当用户开启字幕但没有权限时：

1. 服务把开关回滚为关闭并持久化 `false`；
2. 广播 `ACTION_NEED_OVERLAY_PERMISSION`；
3. `MainActivity` 收到后跳转 `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`。

App 内开启字幕时同样会做权限检查。

## 已知陷阱

### 1. 背景全透明会被误认为“功能坏了”

白字 + 透明背景，在浅色桌面上完全看不见，现象是“字幕没出来”。默认必须是半透明黑底胶囊。选择“无背景”时要注意文字颜色与桌面底色的对比。

### 2. 服务重启会丢样式

字幕由服务绘制，服务重建后如果只使用默认值，用户的设置就“失效”。因此服务在 `onCreate()` 读取一次，并且**每次显示字幕前都重新读取**。

### 3. 开关只改通知是不够的

字幕是服务持有的窗口，App 内点击开关必须通知服务（`startService` + `ACTION_TOGGLE_CAPTION`），由服务执行显示/隐藏，否则会出现“按钮状态变了但字幕没反应”。

### 4. 切换 duration 值不会自动重排

字号变化后窗口尺寸随之变化，`updateViewLayout` 已覆盖该场景；若后续改成固定宽高，需要手动重新计算 `y`。
