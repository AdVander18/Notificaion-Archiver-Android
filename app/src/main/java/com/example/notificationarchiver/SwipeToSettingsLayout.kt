package com.example.notificationarchiver

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout

class SwipeToSettingsLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var settingsView: View? = null
    private var isOpen = false
    private var width = 0
    private var downX = 0f
    private var downY = 0f
    private var tracking = false
    private var initialTranslationX = 0f
    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private var openAnimationEnabled = true
    private var pendingOpen = false

    // Доля ширины экрана, после которой свайп считается завершённым
    private val swipeThresholdFraction = 0.25f

    override fun onFinishInflate() {
        super.onFinishInflate()
        settingsView = getChildAt(1)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        width = w
        if (pendingOpen) {
            // Восстанавливаем открытое состояние без анимации
            settingsView?.translationX = 0f
            isOpen = true
            pendingOpen = false
        } else if (!isOpen) {
            settingsView?.translationX = -width.toFloat()
        }
    }

    fun requestOpen() {
        pendingOpen = true
        requestLayout()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (settingsView == null) return false

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                tracking = false
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (tracking) return true

                val dx = ev.x - downX
                val dy = ev.y - downY

                if (isOpen) {
                    if (dx < -touchSlop && Math.abs(dx) > Math.abs(dy) * 0.5f) {
                        tracking = true
                        initialTranslationX = settingsView!!.translationX
                        return true
                    }
                } else {
                    if (dx > touchSlop && dx > Math.abs(dy) * 0.5f) {
                        tracking = true
                        initialTranslationX = settingsView!!.translationX
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracking = false
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!tracking || settingsView == null) return false

        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                var newTranslation = initialTranslationX + dx
                newTranslation = newTranslation.coerceIn(-width.toFloat(), 0f)
                settingsView!!.translationX = newTranslation
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracking = false
                val current = settingsView!!.translationX
                val delta = current - initialTranslationX   // на сколько сдвинули от точки захвата
                val fraction = Math.abs(delta) / width.toFloat()

                if (isOpen) {
                    // Открыта → пытаемся закрыть свайпом влево (delta < 0)
                    if (delta < 0 && fraction > swipeThresholdFraction) {
                        animateToClosed()
                    } else {
                        animateToOpen()
                    }
                } else {
                    // Закрыта → пытаемся открыть свайпом вправо (delta > 0)
                    if (delta > 0 && fraction > swipeThresholdFraction) {
                        animateToOpen()
                    } else {
                        animateToClosed()
                    }
                }
                return true
            }
        }
        return false
    }

    fun setOpenAnimationEnabled(enabled: Boolean) {
        openAnimationEnabled = enabled
    }

    private fun animateToOpen() {
        if (openAnimationEnabled) {
            settingsView?.animate()
                ?.translationX(0f)
                ?.withEndAction { isOpen = true }
                ?.start()
        } else {
            settingsView?.translationX = 0f
            isOpen = true
        }
    }

    private fun animateToClosed() {
        settingsView?.animate()
            ?.translationX(-width.toFloat())
            ?.withEndAction { isOpen = false }
            ?.start()
    }

    fun openPanel(animate: Boolean = true) {
        if (animate) animateToOpen()
        else {
            settingsView?.translationX = 0f
            isOpen = true
        }
    }

    fun closePanel(animate: Boolean = true) {
        if (animate) animateToClosed()
        else {
            settingsView?.translationX = -width.toFloat()
            isOpen = false
        }
    }

    fun isPanelOpen(): Boolean = isOpen
}