package com.cookandroide.pikaboka.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class DrawingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint().apply {
        color = Color.BLACK
        isAntiAlias = true
        strokeWidth = 24f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val path = Path()
    private var drawBitmap: Bitmap? = null
    private var drawCanvas: Canvas? = null

    init {
        // 배경이 투명하지 않도록 하려면 여기 설정
        setBackgroundColor(Color.BLACK)
    }

    fun getProcessedInput(): FloatArray {
        val src = getBitmap() ?: return FloatArray(28 * 28)
        val resized = Bitmap.createScaledBitmap(src, 28, 28, true)
        val floatArray = FloatArray(28 * 28)

        for (y in 0 until 28) {
            for (x in 0 until 28) {
                val pixel = resized.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()

                // 색 반전 (학습 데이터: 검은 배경 + 흰 글씨)
                val inverted = 255 - gray

                floatArray[y * 28 + x] = inverted / 255.0f
            }
        }
        return floatArray
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            drawBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            drawCanvas = Canvas(drawBitmap!!)
            drawCanvas?.drawColor(Color.WHITE)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        canvas.drawPath(path, paint)
    }

    private var lastX = 0f
    private var lastY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                path.moveTo(x, y)
                lastX = x
                lastY = y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                path.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2)
                lastX = x
                lastY = y
            }
            MotionEvent.ACTION_UP -> {
                path.lineTo(x, y)
                // 그려진 경로를 비트맵 캔버스에 고정
                drawCanvas?.drawPath(path, paint)
                path.reset()
            }
            else -> return false
        }
        invalidate()
        return true
    }

    fun clear() {
        drawCanvas?.drawColor(Color.WHITE)
        path.reset()
        invalidate()
    }

    fun getBitmap(): Bitmap? {
        // 반환할 때 원본 크기 비트맵을 복사해서 제공
        return drawBitmap?.copy(Bitmap.Config.ARGB_8888, false)
    }
}
