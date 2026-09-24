package com.siyehua.egnlishstudy.data

import android.content.Context

data class CaptionStyle(

    val yPercent: Int = 12,

    val textSizeSp: Int = 16,

    val textColor: Int = 0xFFFFFFFF.toInt(),

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
