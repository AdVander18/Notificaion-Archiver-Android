package com.example.notificationarchiver

import android.content.Context
import android.graphics.*
import android.view.View

class FakeThemeRevealView(context: Context) : View(context) {
    private var oldBitmap: Bitmap? = null
    private var centerX = 0f
    private var centerY = 0f
    private var newColor = Color.WHITE

    var radius = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setRevealData(old: Bitmap, cx: Float, cy: Float, color: Int) {
        oldBitmap = old
        centerX = cx
        centerY = cy
        newColor = color
        radius = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Старая тема — весь фон
        oldBitmap?.let { canvas.drawBitmap(it, 0f, 0f, paint) }
        // Фейковая новая тема — только внутри круга
        if (radius > 0) {
            paint.color = newColor
            paint.style = Paint.Style.FILL
            canvas.drawCircle(centerX, centerY, radius, paint)
        }
    }
}