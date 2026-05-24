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

    override fun onFinishInflate() {
        super.onFinishInflate()
        // Первый ребёнок – основной контент, второй – контейнер настроек
        settingsView = getChildAt(1)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        width = w
        // Перемещаем панель настроек за левый край экрана, если она закрыта
        if (!isOpen) {
            settingsView?.translationX = -width.toFloat()
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (settingsView == null) return false

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                tracking = false
                // Сначала даём дочерним элементам (RecyclerView и др.) шанс обработать касание
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (tracking) return true  // уже перехватили

                val dx = ev.x - downX
                val dy = ev.y - downY

                if (isOpen) {
                    // Панель открыта – перехватываем только при явном свайпе влево (закрытие)
                    if (dx < -touchSlop && Math.abs(dx) > Math.abs(dy) * 0.5f) {
                        tracking = true
                        initialTranslationX = settingsView!!.translationX
                        return true
                    }
                } else {
                    // Панель закрыта – перехватываем только при свайпе вправо (открытие)
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
                // Ограничиваем движение от -width (полностью скрыто) до 0 (полностью открыто)
                newTranslation = newTranslation.coerceIn(-width.toFloat(), 0f)
                settingsView!!.translationX = newTranslation
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracking = false
                val current = settingsView!!.translationX
                val threshold = -width / 2f  // Половина ширины – порог для завершения жеста

                if (isOpen) {
                    // Была открыта → закрываем, если сдвинули левее порога
                    if (current < threshold) animateToClosed() else animateToOpen()
                } else {
                    // Была закрыта → открываем, если сдвинули правее порога
                    if (current > threshold) animateToOpen() else animateToClosed()
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
            // Прямая установка без вызова openPanel()
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