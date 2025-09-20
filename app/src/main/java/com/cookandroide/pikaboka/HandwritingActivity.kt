package com.cookandroide.pikaboka

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.widget.TextView
import com.cookandroide.pikaboka.views.DrawingView
import com.google.android.material.button.MaterialButton
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class HandwritingActivity : BaseActivity() {

    private lateinit var drawView: DrawingView
    private lateinit var evaluateButton: MaterialButton
    private lateinit var btnBack: MaterialButton
    private lateinit var resultText: TextView
    private lateinit var clearButton: MaterialButton
    private lateinit var nextButton: MaterialButton
    private lateinit var tflite: Interpreter

    private val labelMap = arrayOf(
        "あ","い","う","え","お",
        "か","き","く","け","こ",
        "さ","し","す","せ","そ",
        "た","ち","つ","て","と",
        "な","に","ぬ","ね","の",
        "は","ひ","ふ","へ","ほ",
        "ま","み","む","め","も",
        "や","ゆ","よ",
        "ら","り","る","れ","ろ",
        "わ","を","ん","ゝ","ゞ","ゑ"
    )

    private var currentIndex: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_handwriting)

        // 뷰 초기화
        drawView = findViewById(R.id.drawView)
        evaluateButton = findViewById(R.id.evaluateButton)
        btnBack = findViewById(R.id.btnBack)
        resultText = findViewById(R.id.resultText)
        clearButton = findViewById(R.id.clearButton)
        nextButton = findViewById(R.id.nextButton)

        clearButton.setOnClickListener {
            drawView.clear()
            resultText.text = ""
        }

        nextButton.setOnClickListener {
            nextQuestion()
        }

        btnBack.setOnClickListener { finish() }

        // 모델 로드
        val modelBytes = assets.open("k49_cnn.tflite").readBytes()
        val buffer = ByteBuffer.allocateDirect(modelBytes.size)
        buffer.order(ByteOrder.nativeOrder())
        buffer.put(modelBytes)
        tflite = Interpreter(buffer)

        // 첫 문제 출제
        nextQuestion()

        // 평가 버튼
        evaluateButton.setOnClickListener {
            evaluateButton.isEnabled = false
            evaluateButton.text = "평가 중..."
            resultText.text = ""

            Thread {
                try {
                    val input = getProcessedInput(drawView)
                    val inputBuffer = ByteBuffer.allocateDirect(4 * 1 * 28 * 28)
                    inputBuffer.order(ByteOrder.nativeOrder())
                    for (v in input) inputBuffer.putFloat(v)
                    inputBuffer.rewind()

                    val output = Array(1) { FloatArray(labelMap.size) }
                    tflite.run(inputBuffer, output)

                    val probs = output[0]
                    val predicted = probs.indices.maxByOrNull { probs[it] } ?: -1
                    val confidence = probs[predicted] * 100

                    val message = if (predicted == currentIndex) {
                        "✅ 정답! (${labelMap[currentIndex]})\n신뢰도: ${"%.2f".format(confidence)}%"
                    } else {
                        "❌ 오답!\n제시된 글자: ${labelMap[currentIndex]}\n인식: ${labelMap[predicted]}\n신뢰도: ${"%.2f".format(confidence)}%"
                    }

                    runOnUiThread {
                        resultText.text = message
                        evaluateButton.isEnabled = true
                        evaluateButton.text = "✍ 평가하기"
                    }

                } catch (e: Exception) {
                    runOnUiThread {
                        evaluateButton.isEnabled = true
                        evaluateButton.text = "✍ 평가하기"
                        AlertDialog.Builder(this)
                            .setTitle("오류")
                            .setMessage(e.message ?: "알 수 없는 오류")
                            .setPositiveButton("확인", null)
                            .show()
                    }
                }
            }.start()
        }
    }

    private fun nextQuestion() {
        currentIndex = (0 until labelMap.size).random()
        val titleView = findViewById<TextView>(R.id.handwritingTitle)
        titleView.text = "이 글자를 써보세요: ${labelMap[currentIndex]}"
        drawView.clear()
        resultText.text = ""
    }

    /** 캔버스 이미지를 28x28 흑백 float 배열로 변환 */
    private fun getProcessedInput(drawView: DrawingView): FloatArray {
        // 캔버스 크기만큼 Bitmap 생성
        val bitmap = Bitmap.createBitmap(drawView.width, drawView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawView.draw(canvas)

        // 28x28 스케일
        val scaled = Bitmap.createScaledBitmap(bitmap, 28, 28, true)
        val floatArray = FloatArray(28 * 28)

        for (y in 0 until 28) {
            for (x in 0 until 28) {
                val pixel = scaled.getPixel(x, y)
                // 그레이스케일 변환
                val gray = (0.299 * ((pixel shr 16) and 0xFF) +
                        0.587 * ((pixel shr 8) and 0xFF) +
                        0.114 * (pixel and 0xFF)).toFloat() / 255f
                floatArray[y * 28 + x] = 1f - gray // 배경 흰색, 글자 검정 → 반전
            }
        }
        return floatArray
    }
}
