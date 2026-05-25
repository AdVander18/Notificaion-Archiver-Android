package com.example.notificationarchiver

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator

object ThemeOverlayManager {
    private var overlayView: FakeThemeRevealView? = null
    private var windowManager: WindowManager? = null
    var isOverlayShown = false
        private set

    // Для отложенного запуска схлопывания
    private var hideRunnable: Runnable? = null

    fun show(
        context: Context,
        oldBitmap: Bitmap,
        cx: Float,
        cy: Float,
        newColor: Int,
        radius: Float
    ): FakeThemeRevealView {
        // Если оверлей уже висит – убираем (заодно отменяем запланированное схлопывание)
        if (isOverlayShown) removeView()

        val view = FakeThemeRevealView(context).apply {
            setRevealData(oldBitmap, cx, cy, newColor)
            this.radius = radius
        }

        val params = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager?.addView(view, params)
        overlayView = view
        isOverlayShown = true
        return view
    }

    fun hideWithAnimation(onComplete: () -> Unit = {}) {
        val view = overlayView ?: run {
            onComplete()
            return
        }

        val startRadius = view.radius
        val animator = ValueAnimator.ofFloat(startRadius, 0f).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                view.radius = anim.animatedValue as Float
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    removeView()
                    onComplete()
                }
            })
        }
        animator.start()
    }

    fun removeView() {
        // Отменяем отложенное схлопывание, если есть
        hideRunnable?.let { Handler(Looper.getMainLooper()).removeCallbacks(it) }
        hideRunnable = null

        overlayView?.let { view ->
            windowManager?.removeView(view)
        }
        overlayView = null
        windowManager = null
        isOverlayShown = false
    }

    fun scheduleHideWithDelay(delayMs: Long) {
        if (!isOverlayShown) return

        // Снимаем предыдущий запланированный запуск
        hideRunnable?.let { Handler(Looper.getMainLooper()).removeCallbacks(it) }

        val runnable = Runnable {
            hideRunnable = null
            hideWithAnimation()
        }
        hideRunnable = runnable
        Handler(Looper.getMainLooper()).postDelayed(runnable, delayMs)
    }

    data class OverlayData(
        val bitmap: Bitmap,
        val cx: Float,
        val cy: Float,
        val newColor: Int
    )

    /** Temporary storage for the overlay data during a theme change. */
    var pendingOverlayData: OverlayData? = null
}