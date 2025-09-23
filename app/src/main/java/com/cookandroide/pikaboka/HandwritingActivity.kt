package com.cookandroide.pikaboka

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.lifecycleScope
import com.cookandroide.pikaboka.databinding.ActivityHandwritingBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class HandwritingActivity : BaseActivity() {

    private lateinit var binding: ActivityHandwritingBinding
    private lateinit var tflite: Interpreter
    private var currentIndex: Int = -1

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHandwritingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 버튼 클릭 효과
        addButtonClickEffect(binding.evaluateButton)
        addButtonClickEffect(binding.clearButton)
        addButtonClickEffect(binding.nextButton)
        addButtonClickEffect(binding.btnBack)

        // 백버튼
        binding.btnBack.setOnClickListener {
            finish() // 기존 onBackPressedDispatcher 대신 finish 사용
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                val options = ActivityOptionsCompat.makeCustomAnimation(
                    this,
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
                )
                // finish()에는 startActivity처럼 옵션을 바로 넣을 수 없으므로 overridePendingTransition 사용
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }

        // 모델 로드
        val modelBytes = assets.open("k49_cnn.tflite").readBytes()
        val buffer = ByteBuffer.allocateDirect(modelBytes.size)
        buffer.order(ByteOrder.nativeOrder())
        buffer.put(modelBytes)
        tflite = Interpreter(buffer)

        // 버튼 이벤트
        binding.clearButton.setOnClickListener {
            binding.drawView.clear()
            binding.resultText.text = ""
        }

        binding.nextButton.setOnClickListener {
            nextQuestion()
        }

        binding.evaluateButton.setOnClickListener {
            evaluateHandwriting()
        }

        nextQuestion()
    }

    private fun nextQuestion() {
        currentIndex = (0 until labelMap.size).random()
        binding.handwritingTitle.text = "이 글자를 써보세요: ${labelMap[currentIndex]}"
        binding.drawView.clear()
        binding.resultText.text = ""
    }

    private fun evaluateHandwriting() {
        binding.evaluateButton.isEnabled = false
        binding.evaluateButton.text = "평가 중..."
        binding.resultText.text = ""

        lifecycleScope.launch {
            try {
                val input = withContext(Dispatchers.Default) {
                    getProcessedInput(binding.drawView)
                }

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

                binding.resultText.text = message

            } catch (e: Exception) {
                binding.resultText.text = "오류: ${e.message ?: "알 수 없는 오류"}"
            } finally {
                binding.evaluateButton.isEnabled = true
                binding.evaluateButton.text = "✍ 평가하기"
            }
        }
    }

    /** 캔버스 이미지를 28x28 흑백 float 배열로 변환 */
    private fun getProcessedInput(drawView: com.cookandroide.pikaboka.views.DrawingView): FloatArray {
        val bitmap = Bitmap.createBitmap(drawView.width, drawView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawView.draw(canvas)

        val scaled = Bitmap.createScaledBitmap(bitmap, 28, 28, true)
        val floatArray = FloatArray(28 * 28)

        for (y in 0 until 28) {
            for (x in 0 until 28) {
                val pixel = scaled.getPixel(x, y)
                val gray = (0.299 * ((pixel shr 16) and 0xFF) +
                        0.587 * ((pixel shr 8) and 0xFF) +
                        0.114 * (pixel and 0xFF)).toFloat() / 255f
                floatArray[y * 28 + x] = 1f - gray
            }
        }
        return floatArray
    }

    override fun onDestroy() {
        super.onDestroy()
        tflite.close()
    }
}
