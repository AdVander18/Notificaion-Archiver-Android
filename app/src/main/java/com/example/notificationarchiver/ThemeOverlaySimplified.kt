package com.example.notificationarchiver

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager

object ThemeOverlaySimplified {
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null

    val isShown: Boolean get() = overlayView != null

    @Volatile
    var isTransitioning: Boolean = false
        private set

    fun show(context: Context, color: Int) {
        hide()   // на всякий случай убираем предыдущий
        isTransitioning = true

        overlayView = View(context).apply {
            setBackgroundColor(color)
        }

        val params = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            format = PixelFormat.OPAQUE
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager?.addView(overlayView, params)
    }

    fun hide() {
        overlayView?.let { view ->
            windowManager?.removeView(view)
        }
        overlayView = null
        windowManager = null
        isTransitioning = false
    }
}