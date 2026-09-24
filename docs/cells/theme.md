# 主题与视觉规范

## 职责

- 提供全应用统一的配色、字体与圆角。
- 支持系统深色模式。

## 涉及文件

| 文件 | 说明 |
| --- | --- |
| `ui/theme/Color.kt` | 色板常量 |
| `ui/theme/Theme.kt` | Material 3 明/暗配色与形状 |
| `ui/theme/Type.kt` | 字体排版 |

## 色板

| 常量 | 值 | 用途 |
| --- | --- | --- |
| `StudyBackground` | `#F7F8EF` | 浅色背景 |
| `StudySurface` | `#FFFFFF` | 卡片 |
| `StudyInk` | `#132019` | 主文字 |
| `StudyMuted` | `#68746C` | 次要文字 |
| `StudyLine` | `#E0E6DD` | 描边 |
| `StudyGreen` | `#1FA463` | 品牌主色（头部、主按钮） |
| `StudyGreenDark` | `#0E6B45` | 主色深 |
| `StudyMint` | `#DDF5E8` | 主色浅底 / 选中态 |
| `StudyBlue` | `#246BFD` | 次级强调（选中边框） |
| `StudyBlueSoft` | `#E5EEFF` | 次级浅底 |
| `StudyYellow` | `#FFC94A` | 徽章 |
| `StudyCoral` | `#FF775F` | 危险 / 停止 |
| `StudyOrange` | `#F59E0B` | 提示 |
| `StudyDarkBackground` | `#101511` | 深色背景 |
| `StudyDarkSurface` | `#18211B` | 深色卡片 |
| `StudyDarkLine` | `#2D3A31` | 深色描边 |

## 使用规则

- 页面结构用 `MaterialTheme.colorScheme`（`background` / `surface` / `onSurface` / `onSurfaceVariant` / `outline`），这样深色模式自动生效。
- 品牌绿只用于**绿色头部的背景**与主按钮，不要用于正文。
- 页面头部统一形态：绿色背景、底部 28dp 圆角、左侧半透明白底返回按钮、白色标题。
- 内容区统一用卡片：`MaterialTheme.shapes.large` + 1dp `outline` 描边 + 16dp 内边距。
- 形状尺度集中在 `Theme.kt` 的 `AppShapes`（8dp 系列），不要在页面里随意写圆角数值。

## 已知陷阱

### 1. 新增页面必须读主题色

新页面若把颜色写死为 `Study*` 浅色常量，深色模式下会出现“白底白字”或对比度过低。判断标准：把系统切到深色模式，页面所有文字与卡片仍清晰可读。

### 2. `[hidden]` 与自定义 `display` 的优先级

Compose 不涉及该问题；若将来在 Web 预览或自定义 View 中使用 `hidden` 属性，注意自定义 `display` 会覆盖浏览器的 `[hidden]{display:none}`，需要显式写 `[hidden]{display:none!important}`。

### 3. 深色模式下的“选中色”

选中态用 `StudyMint`（浅绿）在深色背景下偏亮，与 `StudyGreenDark` 图标搭配对比度足够；若改为其他组合，先确认深色模式下的可读性。
