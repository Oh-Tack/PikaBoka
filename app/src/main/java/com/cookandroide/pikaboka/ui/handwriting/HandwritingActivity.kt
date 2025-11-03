package com.cookandroide.pikaboka.ui.handwriting

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.cookandroide.pikaboka.base.BaseActivity
import com.cookandroide.pikaboka.R
import com.cookandroide.pikaboka.databinding.ActivityHandwritingBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.json.JSONObject
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

class HandwritingActivity : BaseActivity() {

    private lateinit var binding: ActivityHandwritingBinding
    private lateinit var tflite: Interpreter
    private lateinit var labels: List<String>   // labels.json 로드 결과
    private var currentIndex: Int = -1

    // 모델 파일
    private val MODEL_FALLBACK = "k49_cnn_fp32.tflite"

    // 판정 규칙(앱 체감 인식률 ↑)
    private val PASS_THRESHOLD = 0.60f         // p(target) ≥ 0.60 → 정답
    private val SOFT_GAP = 0.10f               // p(target) ≥ p(top1)-0.10 → 부분정답
    private val TOPK = 3

    // TTA(작은 평행이동 평균) 옵션
    private val USE_TTA = true
    private val TTA_SHIFT_PX = 1 // 28x28에서 ±1px 이동 평균

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHandwritingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 기능별 테마(상태바 컬러)
        window.statusBarColor = getColor(R.color.feature_handwriting)

        // 버튼 터치 효과
        addButtonClickEffect(binding.btnBack)
        addButtonClickEffect(binding.evaluateButton)
        addButtonClickEffect(binding.nextButton)
        addButtonClickEffect(binding.btnClear)

        // 뒤로가기
        binding.btnBack.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // 1) labels.json 로드 (index 키를 숫자 정렬)
        labels = loadLabelsFromAssets("labels.json")

        // 2) TFLite 로드
        tflite = Interpreter(loadMappedAsset(MODEL_FALLBACK), Interpreter.Options())

        // 버튼 이벤트
        binding.btnClear.setOnClickListener {
            binding.drawView.clear()
            binding.resultText.text = ""
        }
        binding.nextButton.setOnClickListener { nextQuestion() }
        binding.evaluateButton.setOnClickListener { evaluateHandwriting() }

        nextQuestion()
    }

    private fun nextQuestion() {
        // 0..labels.size-1 중 랜덤
        currentIndex = (0 until labels.size).random()
        binding.handwritingTitle.text = "이 글자를 써보세요: ${labels[currentIndex]}"
        binding.drawView.clear()
        binding.resultText.text = ""
    }

    private fun evaluateHandwriting() {
        binding.evaluateButton.isEnabled = false
        binding.evaluateButton.text = "평가 중..."
        binding.resultText.text = ""

        lifecycleScope.launch {
            try {
                // 전처리 + 적응형 크롭 + 미작성 감지
                val (floatArray, _) = withContext(Dispatchers.Default) {
                    getProcessedInput(binding.drawView)
                }

                if (floatArray == null) {
                    binding.resultText.text = "아직 캔버스에 글자를 쓰지 않았어요!"
                    return@launch
                }

                // --- 추론 (TTA 적용 시 ±1px 평행이동 평균) ---
                val probs: FloatArray = if (USE_TTA) {
                    val shifts = arrayOf(0 to 0, TTA_SHIFT_PX to 0, -TTA_SHIFT_PX to 0, 0 to TTA_SHIFT_PX, 0 to -TTA_SHIFT_PX)
                    val acc = FloatArray(labels.size)
                    for ((dx, dy) in shifts) {
                        val shifted = shift28x28(floatArray, dx, dy)
                        val input = toInputBuffer(shifted)
                        val out = Array(1) { FloatArray(labels.size) }
                        tflite.run(input, out)
                        val out0 = out[0]
                        for (i in acc.indices) {
                            acc[i] = acc[i] + out0[i]
                        }
                    }
                    val denom = 1.0f / shifts.size
                    for (i in acc.indices) {
                        acc[i] = acc[i] * denom
                    }
                    acc
                } else {
                    val input = toInputBuffer(floatArray)
                    val out = Array(1) { FloatArray(labels.size) }
                    tflite.run(input, out)
                    val out0 = out[0]
                    out0
                }


                val topIdx = probs.indices.maxByOrNull { probs[it] } ?: 0
                val conf = probs[topIdx]

                // Top-K 문구
                val topKText = probs.indices
                    .sortedByDescending { probs[it] }
                    .take(TOPK)
                    .joinToString("  ") { i -> "${labels[i]}(${String.format("%.1f", probs[i]*100)}%)" }

                // 판정 규칙
                val pTarget = probs[currentIndex]
                val isPass = pTarget >= PASS_THRESHOLD
                val isSoft = !isPass && (pTarget >= (conf - SOFT_GAP))

                val msg = when {
                    isPass && topIdx == currentIndex ->
                        "✅ 정답! (${labels[currentIndex]})\n신뢰도: ${"%.2f".format(pTarget*100)}%\nTop$TOPK: $topKText"
                    isPass && topIdx != currentIndex ->
                        "✅ 정답(임계 통과)!\n제시: ${labels[currentIndex]}\n예측1: ${labels[topIdx]}(${String.format("%.1f", conf*100)}%)\n" +
                                "타깃 확률: ${"%.2f".format(pTarget*100)}%\nTop$TOPK: $topKText"
                    isSoft ->
                        "☑ 부분 정답\n제시: ${labels[currentIndex]}\n예측1: ${labels[topIdx]}(${String.format("%.1f", conf*100)}%)\n" +
                                "타깃 확률: ${"%.2f".format(pTarget*100)}% (Top1과 격차 ≤ ${SOFT_GAP*100}%)\n" +
                                "한 번 더 또렷하게 써볼까요?\nTop$TOPK: $topKText"
                    else ->
                        "❌ 오답\n제시: ${labels[currentIndex]}\n인식: ${labels[topIdx]}(${String.format("%.1f", conf*100)}%)\n" +
                                "타깃 확률: ${"%.2f".format(pTarget*100)}%\n" +
                                "Tip) 중앙에 크게, 획을 좀 더 진하게 써보세요.\nTop$TOPK: $topKText"
                }

                binding.resultText.text = msg

            } catch (e: Exception) {
                binding.resultText.text = "오류: ${e.message ?: "알 수 없는 오류"}"
            } finally {
                binding.evaluateButton.isEnabled = true
                binding.evaluateButton.text = "✍ 평가하기"

                binding.drawView.clear()
            }
        }
    }

    // ----------------- 전처리/도우미 -----------------

    /** FloatArray(28*28) → TFLite 입력 버퍼 (float32, NHWC, C=1) */
    private fun toInputBuffer(arr: FloatArray): ByteBuffer =
        ByteBuffer.allocateDirect(4 * 28 * 28).apply {
            order(ByteOrder.nativeOrder())
            for (v in arr) putFloat(v)
            rewind()
        }

    /** 28x28 평행이동 (빈 영역 0으로 채움) */
    private fun shift28x28(src: FloatArray, dx: Int, dy: Int): FloatArray {
        if (dx == 0 && dy == 0) return src
        val dst = FloatArray(28 * 28)
        for (y in 0 until 28) for (x in 0 until 28) {
            val nx = x - dx
            val ny = y - dy
            if (nx in 0..27 && ny in 0..27) {
                dst[y * 28 + x] = src[ny * 28 + nx]
            }
        }
        return dst
    }

    /**
     * 캔버스 → 28x28 float[0..1]; 획=1, 배경=0.
     * - 적응형 임계치(밝기 히스토그램 10퍼센타일)로 바운딩 박스 추출
     * - 크롭 박스에 4% 패딩
     * - 아무 것도 안 썼으면 Pair(null, src) 반환
     */
    private fun getProcessedInput(drawView: DrawingView): Pair<FloatArray?, Bitmap> {
        val src = Bitmap.createBitmap(drawView.width, drawView.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(src)
        drawView.draw(c)

        val w = src.width; val h = src.height
        if (w <= 0 || h <= 0) return Pair(null, src)

        // 1) 그레이스케일 히스토그램 (샘플링)
        val hist = IntArray(256)
        var total = 0
        for (y in 0 until h step 2) {
            for (x in 0 until w step 2) {
                val p = src.getPixel(x, y)
                val g = ((0.299f * ((p shr 16) and 0xFF) +
                        0.587f * ((p shr  8) and 0xFF) +
                        0.114f * ( p         and 0xFF))).toInt().coerceIn(0, 255)
                hist[g]++
                total++
            }
        }
        // 2) 10퍼센타일 근처를 임계치로 (너무 낮으면 +여유)
        val targetCum = (total * 0.10f).toInt()
        var cum = 0
        var th = 200
        for (i in 0..255) {
            cum += hist[i]
            if (cum >= targetCum) { th = min(i + 10, 240); break }
        }

        // 3) 바운딩 박스 계산 + 획 픽셀 수
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        var inkCount = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val p = src.getPixel(x, y)
                val gray = (0.299f * ((p shr 16) and 0xFF) +
                        0.587f * ((p shr  8) and 0xFF) +
                        0.114f * ( p         and 0xFF))
                if (gray < th) {
                    inkCount++
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }

        // 4) 미작성 판단: 전체의 5% 미만이면 없음 처리
        if (inkCount < (w * h * 0.05f)) return Pair(null, src)
        if (minX > maxX || minY > maxY) return Pair(null, src)

        // 5) 크롭 박스에 4% 패딩
        val padX = (((maxX - minX + 1) * 0.04f).toInt()).coerceAtLeast(1)
        val padY = (((maxY - minY + 1) * 0.04f).toInt()).coerceAtLeast(1)
        minX = (minX - padX).coerceAtLeast(0)
        minY = (minY - padY).coerceAtLeast(0)
        maxX = (maxX + padX).coerceAtMost(w - 1)
        maxY = (maxY + padY).coerceAtMost(h - 1)

        val cropW = (maxX - minX + 1).coerceAtLeast(1)
        val cropH = (maxY - minY + 1).coerceAtLeast(1)
        val cropped = Bitmap.createBitmap(src, minX, minY, cropW, cropH)

        // 6) 정사각 패딩 + 중앙 정렬 (배경 흰색)
        val size = max(cropped.width, cropped.height)
        val padded = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).apply {
                drawColor(android.graphics.Color.WHITE)
                val left = (size - cropped.width) / 2f
                val top  = (size - cropped.height) / 2f
                drawBitmap(cropped, left, top, null)
            }
        }

        // 7) 28x28로 리사이즈 + 정규화(배경0/획1)
        val scaled = Bitmap.createScaledBitmap(padded, 28, 28, true)
        val arr = FloatArray(28 * 28)
        for (y in 0 until 28) for (x in 0 until 28) {
            val px = scaled.getPixel(x, y)
            val gray = (px and 0xFF) / 255f     // 0(검정)~1(흰색)
            arr[y * 28 + x] = 1f - gray         // 획=1, 배경=0
        }
        return Pair(arr, padded)
    }

    private fun loadLabelsFromAssets(assetName: String): List<String> {
        val jsonText = assets.open(assetName).bufferedReader().use { it.readText() }
        val obj = JSONObject(jsonText)
        // labels.json은 {"0":"あ", "1":"い", ...} 형태라고 가정
        val keys = obj.keys().asSequence().toList()
        val sorted = keys.sortedBy { it.toInt() }
        return sorted.map { k -> obj.getString(k) }
    }

    private fun loadMappedAsset(assetName: String): MappedByteBuffer {
        val fd = assets.openFd(assetName)
        val fis = FileInputStream(fd.fileDescriptor)
        val channel = fis.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.length)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::tflite.isInitialized) tflite.close()
    }
}
