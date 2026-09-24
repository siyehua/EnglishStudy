package com.siyehua.egnlishstudy.playback

import com.siyehua.egnlishstudy.data.CaptionStyle

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

object DesktopCaptionOverlay {

    private var captionView: TextView? = null
    private var windowManager: WindowManager? = null

    fun show(context: Context, text: String, style: CaptionStyle) {
        val wm = windowManager ?: context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val view = captionView ?: TextView(context).apply {

            maxLines = Int.MAX_VALUE
            gravity = Gravity.CENTER
        }.also { captionView = it }

        view.text = text
        view.setTextColor(style.textColor)
        view.setBackgroundColor(style.bgColor)
        view.textSize = style.textSizeSp.toFloat()
        view.invalidate()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,

            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL

            val screenH = context.resources.displayMetrics.heightPixels
            y = ((screenH - 200) * style.yPercent / 100)
            x = 0
        }

        if (view.parent == null) {
            wm.addView(view, params)
        } else {
            wm.updateViewLayout(view, params)
        }
    }

    fun hide(context: Context) {
        captionView?.let { view ->
            runCatching { windowManager?.removeView(view) }
            captionView = null
        }
        windowManager = null
    }
}
