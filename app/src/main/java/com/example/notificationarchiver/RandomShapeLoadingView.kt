package com.example.notificationarchiver

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class RandomShapeLoadingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var isTriangle = true
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FF6200EE")
    }

    private val handler = Handler(Looper.getMainLooper())
    private val toggleRunnable = object : Runnable {
        override fun run() {
            isTriangle = !isTriangle
            invalidate()
            handler.postDelayed(this, 400L)
        }
    }

    /** Запускает смену фигур. Если уже запущена, повторный вызов безопасен. */
    fun start() {
        handler.removeCallbacks(toggleRunnable)  // на случай двойного вызова
        handler.post(toggleRunnable)
    }

    /** Останавливает анимацию. Можно вызывать, даже если не запущена. */
    fun stop() {
        handler.removeCallbacks(toggleRunnable)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Анимация стартует только после явного вызова start(),
        // здесь ничего автоматически не включаем.
    }

    override fun onDetachedFromWindow() {
        stop()            // гарантированно прекращаем утечку сообщений
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val cx = width / 2f
        val cy = height / 2f
        val size = min(width, height) / 2f * 0.7f

        if (isTriangle) {
            val path = Path().apply {
                moveTo(cx, cy - size)
                lineTo(cx - size * 0.866f, cy + size * 0.5f)
                lineTo(cx + size * 0.866f, cy + size * 0.5f)
                close()
            }
            canvas.drawPath(path, paint)
        } else {
            val half = size * 0.7f
            canvas.drawRect(cx - half, cy - half, cx + half, cy + half, paint)
        }
    }
}