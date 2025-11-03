package com.cookandroide.pikaboka.ui.handwriting

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class DrawingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val drawPaint = Paint().apply {
        color = Color.BLACK
        isAntiAlias = true
        isDither = true
        strokeWidth = dp(24f)
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val drawPath = Path()
    private var canvasBitmap: Bitmap? = null
    private var drawCanvas: Canvas? = null
    private val bitmapPaint = Paint(Paint.DITHER_FLAG)

    // 가이드 라인(점선)
    private var showGuides: Boolean = true
    private var guideInsetRatio: Float = 0.10f   // 바깥 여백 비율(10%)
    private var guideSquare: Boolean = true      // 정사각형 고정 (기본 on)

    // 대시/색/굵기는 View 크기에 맞춰 매 프레임 재계산
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#B0BEC5")
    }
    private val guideThinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#B0BEC5")
    }

    // Touch track
    private var lastX = 0f
    private var lastY = 0f
    private val touchTolerance = 2f

    init {
        // View 배경은 기본값 유지 (검정으로 깔면 전처리와 불일치)
        // 내부 비트맵을 흰색으로 채워 사용
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        drawCanvas = Canvas(canvasBitmap!!).apply {
            drawColor(Color.WHITE) // 항상 흰 배경
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 1) 내부 비트맵
        canvasBitmap?.let { canvas.drawBitmap(it, 0f, 0f, bitmapPaint) }

        // 2) 가이드(점선 사각 + 중앙 십자)
        if (showGuides) drawGuides(canvas)

        // 3) 진행 중인 Path
        canvas.drawPath(drawPath, drawPaint)
    }

    private fun drawGuides(canvas: Canvas) {
        val vw = width.toFloat()
        val vh = height.toFloat()
        if (vw <= 0f || vh <= 0f) return

        // ----- 가이드 영역 계산 -----
        val minSide = min(vw, vh)
        val inset = minSide * guideInsetRatio
        val rect: RectF = if (guideSquare) {
            // 정사각형 경계(중앙 고정)
            val side = minSide - (inset * 2f)
            val left = (vw - side) / 2f
            val top = (vh - side) / 2f
            RectF(left, top, left + side, top + side)
        } else {
            // 기존 사각형(가변 종횡비)
            RectF(inset, inset, vw - inset, vh - inset)
        }

        // ----- 대시/선 굵기 동적 계산 -----
        // 패턴이 화면 크기에 비례하도록: 가로/세로 중 작은 변 기준
        val unit = (minSide / 200f).coerceAtLeast(dp(0.5f))   // 최소 굵기 보장
        val dashOn = 8f * unit
        val dashOff = 6f * unit

        guidePaint.strokeWidth = 1.5f * unit
        guideThinPaint.strokeWidth = 1.2f * unit

        guidePaint.pathEffect = DashPathEffect(floatArrayOf(dashOn, dashOff), 0f)
        guideThinPaint.pathEffect = DashPathEffect(floatArrayOf(dashOn * 0.7f, dashOff * 0.7f), 0f)

        // ----- 사각 경계 -----
        canvas.drawRect(rect, guidePaint)

        // ----- 중앙 십자선 (경계 안으로만) -----
        val cx = rect.centerX()
        val cy = rect.centerY()
        canvas.drawLine(rect.left, cy, rect.right, cy, guideThinPaint)   // 가로
        canvas.drawLine(cx, rect.top, cx, rect.bottom, guideThinPaint)   // 세로
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                drawPath.reset()
                drawPath.moveTo(x, y)
                lastX = x
                lastY = y
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(x - lastX)
                val dy = abs(y - lastY)
                if (dx >= touchTolerance || dy >= touchTolerance) {
                    drawPath.quadTo(lastX, lastY, (x + lastX) / 2f, (y + lastY) / 2f)
                    lastX = x
                    lastY = y
                }
            }
            MotionEvent.ACTION_UP -> {
                drawPath.lineTo(x, y)
                drawCanvas?.drawPath(drawPath, drawPaint)
                drawPath.reset()
            }
            else -> return false
        }
        invalidate()
        return true
    }

    // 캔버스 초기화
    fun clear() {
        canvasBitmap?.eraseColor(Color.WHITE)
        drawPath.reset()
        invalidate()
    }

    // dp → px
    private fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
}
