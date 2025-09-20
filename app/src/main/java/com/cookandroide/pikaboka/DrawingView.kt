package com.cookandroide.pikaboka

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

class DrawingView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val path = Path()
    private val paint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 60f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private var bitmap: Bitmap? = null
    private var canvasBitmap: Canvas? = null

    init {
        setBackgroundColor(Color.BLACK)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = min(w, h) // 정사각형 고정
        bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        canvasBitmap = Canvas(bitmap!!)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        bitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        canvas.drawPath(path, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x.coerceIn(0f, (bitmap?.width ?: width).toFloat())
        val y = event.y.coerceIn(0f, (bitmap?.height ?: height).toFloat())

        when (event.action) {
            MotionEvent.ACTION_DOWN -> path.moveTo(x, y)
            MotionEvent.ACTION_MOVE -> path.lineTo(x, y)
            MotionEvent.ACTION_UP -> {
                canvasBitmap?.drawPath(path, paint)
                path.reset()
            }
        }
        invalidate()
        return true
    }

    fun clear() {
        bitmap?.eraseColor(Color.BLACK)
        invalidate()
    }

    fun getBitmap(): Bitmap {
        // 원본 캔버스 비트맵 그대로 반환
        return bitmap ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }
}
