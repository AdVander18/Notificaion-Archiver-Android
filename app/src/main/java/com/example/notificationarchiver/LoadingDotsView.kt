package com.example.notificationarchiver

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.random.Random

class LoadingDotsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val DOT_COUNT = 15
        private const val DOT_RADIUS_DP = 6f
        private const val INITIAL_DELAY_MS = 0L
        private const val FRAME_INTERVAL_MS = 16L  // ~60 fps
        private const val SPEED_DP_PER_SEC = 1000f   // базовая скорость (dp/s)
        private const val DIRECTION_CHANGE_INTERVAL = 3000L // раз в 3 сек меняем направление
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = getDynamicColor(context)
        style = Paint.Style.FILL
    }

    private val dotRadiusPx: Float by lazy {
        DOT_RADIUS_DP * resources.displayMetrics.density
    }

    private val speedPxPerSec: Float by lazy {
        SPEED_DP_PER_SEC * resources.displayMetrics.density
    }

    // Текущие координаты и скорости (px и px/s)
    private var currentX = FloatArray(DOT_COUNT)
    private var currentY = FloatArray(DOT_COUNT)
    private var vx = FloatArray(DOT_COUNT)
    private var vy = FloatArray(DOT_COUNT)

    private var running = false
    private var shouldBeRunning = false

    private val handler = Handler(Looper.getMainLooper())
    private var moveRunnable: Runnable? = null
    private var directionRunnable: Runnable? = null

    /**
     * Извлекает цвет colorPrimary из текущей темы.
     * Если по какой-то причине не удаётся, возвращает чёрный цвет.
     */
    private fun getDynamicColor(context: Context): Int {
        val typedValue = TypedValue()
        return if (context.theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)) {
            typedValue.data
        } else {
            Color.BLACK
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            // Инициализация случайными позициями, если ещё не было
            if (currentX.all { it == 0f } && currentY.all { it == 0f }) {
                for (i in 0 until DOT_COUNT) {
                    currentX[i] = randomX(w)
                    currentY[i] = randomY(h)
                }
                invalidate()
            }
            // Назначаем случайные скорости при первом запуске или при изменении размера
            for (i in 0 until DOT_COUNT) {
                if (vx[i] == 0f && vy[i] == 0f) {
                    assignRandomVelocity(i)
                }
            }
            if (shouldBeRunning && !running) {
                startLoop()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // При повторном прикреплении (например, после восстановления) убедимся, что цвет актуален
        paint.color = getDynamicColor(context)
        if (shouldBeRunning && width > 0 && height > 0 && !running) {
            startLoop()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopLoop()
        shouldBeRunning = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (i in 0 until DOT_COUNT) {
            canvas.drawCircle(currentX[i], currentY[i], dotRadiusPx, paint)
        }
    }

    fun startAnimationLoop() {
        shouldBeRunning = true
        if (width > 0 && height > 0) {
            if (running) stopLoop()
            startLoop()
        }
    }

    fun stopAnimationLoop() {
        shouldBeRunning = false
        stopLoop()
    }

    private fun startLoop() {
        if (running) return
        running = true

        // Небольшая задержка перед стартом (как в исходном коде)
        handler.postDelayed({
            if (running) {
                startMoving()
                scheduleDirectionChanges()
            }
        }, INITIAL_DELAY_MS)
    }

    private fun stopLoop() {
        running = false
        moveRunnable?.let { handler.removeCallbacks(it) }
        directionRunnable?.let { handler.removeCallbacks(it) }
        moveRunnable = null
        directionRunnable = null
    }

    private fun startMoving() {
        val frameIntervalMs = FRAME_INTERVAL_MS
        val runnable = object : Runnable {
            override fun run() {
                if (!running || width <= 0 || height <= 0) return

                val delta = frameIntervalMs / 1000f // секунды
                for (i in 0 until DOT_COUNT) {
                    // Обновляем позиции
                    currentX[i] += vx[i] * delta
                    currentY[i] += vy[i] * delta

                    // Отражение от границ с учётом радиуса
                    if (currentX[i] - dotRadiusPx < 0) {
                        currentX[i] = dotRadiusPx
                        vx[i] = -vx[i]
                    } else if (currentX[i] + dotRadiusPx > width) {
                        currentX[i] = width - dotRadiusPx
                        vx[i] = -vx[i]
                    }
                    if (currentY[i] - dotRadiusPx < 0) {
                        currentY[i] = dotRadiusPx
                        vy[i] = -vy[i]
                    } else if (currentY[i] + dotRadiusPx > height) {
                        currentY[i] = height - dotRadiusPx
                        vy[i] = -vy[i]
                    }
                }
                invalidate()
                handler.postDelayed(this, frameIntervalMs)
            }
        }
        moveRunnable = runnable
        handler.post(runnable)
    }

    private fun scheduleDirectionChanges() {
        val runnable = object : Runnable {
            override fun run() {
                if (!running) return
                // Случайно меняем направление некоторых точек
                for (i in 0 until DOT_COUNT) {
                    if (Random.nextFloat() < 0.4f) { // 40% шанс смены вектора
                        assignRandomVelocity(i)
                    }
                }
                handler.postDelayed(this, DIRECTION_CHANGE_INTERVAL)
            }
        }
        directionRunnable = runnable
        handler.postDelayed(runnable, DIRECTION_CHANGE_INTERVAL)
    }

    private fun assignRandomVelocity(index: Int) {
        val angle = Random.nextFloat() * 2 * Math.PI
        vx[index] = speedPxPerSec * Math.cos(angle).toFloat()
        vy[index] = speedPxPerSec * Math.sin(angle).toFloat()
    }

    private fun randomX(viewWidth: Int): Float {
        val minVal = dotRadiusPx
        val maxVal = viewWidth - dotRadiusPx
        return if (maxVal <= minVal) viewWidth / 2f
        else minVal + Random.nextFloat() * (maxVal - minVal)
    }

    private fun randomY(viewHeight: Int): Float {
        val minVal = dotRadiusPx
        val maxVal = viewHeight - dotRadiusPx
        return if (maxVal <= minVal) viewHeight / 2f
        else minVal + Random.nextFloat() * (maxVal - minVal)
    }
}