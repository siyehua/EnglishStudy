package com.siyehua.egnlishstudy.data

import android.content.Context

/**
 * 桌面字幕样式配置的本地持久化（SharedPreferences）。
 */
data class CaptionStyle(
    /** 垂直位置百分比 5(靠近顶部)~85(靠近底部)，默认 12；用于避开状态栏与导航栏 */
    val yPercent: Int = 12,
    /** 字号 sp，默认 16 */
    val textSizeSp: Int = 16,
    /** 文字颜色 ARGB，默认白色 */
    val textColor: Int = 0xFFFFFFFF.toInt(),
    /** 背景颜色 ARGB（含透明度），默认半透明黑 */
    val bgColor: Int = 0xB3000000.toInt()
)

class CaptionStyleStore(context: Context) {

    private val prefs = context.getSharedPreferences("caption_style", Context.MODE_PRIVATE)

    fun load(): CaptionStyle = CaptionStyle(
        yPercent = prefs.getInt(KEY_Y, 12).coerceIn(5, 85),
        textSizeSp = prefs.getInt(KEY_SIZE, 16).coerceIn(10, 40),
        textColor = prefs.getInt(KEY_TEXT_COLOR, 0xFFFFFFFF.toInt()),
        bgColor = prefs.getInt(KEY_BG_COLOR, 0xB3000000.toInt())
    )

    /** 字幕开关偏好（用户是否开启过桌面字幕） */
    fun isCaptionEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setCaptionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun save(style: CaptionStyle) {
        prefs.edit()
            .putInt(KEY_Y, style.yPercent.coerceIn(5, 85))
            .putInt(KEY_SIZE, style.textSizeSp.coerceIn(10, 40))
            .putInt(KEY_TEXT_COLOR, style.textColor)
            .putInt(KEY_BG_COLOR, style.bgColor)
            .apply()
    }

    companion object {
        private const val KEY_Y = "y_percent"
        private const val KEY_SIZE = "text_size"
        private const val KEY_TEXT_COLOR = "text_color"
        private const val KEY_BG_COLOR = "bg_color"
        private const val KEY_ENABLED = "caption_enabled"
    }
}
