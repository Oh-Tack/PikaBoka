package com.cookandroide.pikaboka

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.microsoft.cognitiveservices.speech.*
import com.microsoft.cognitiveservices.speech.audio.AudioConfig
import kotlinx.coroutines.*
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.Future
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    // --- Azure 발음 평가 관련 ---
    private val AZURE_SPEECH_KEY = "AZURE_SPEECH_KEY"
    private val AZURE_SERVICE_REGION = "AZURE_SERVICE_REGION"
    private var speechRecognizer: SpeechRecognizer? = null
    private var isRecording = false
    private var isEvaluatingPronunciation = false

    // --- 손글씨 평가 관련 ---
    private var isEvaluatingHandwriting = false
    private var tflite: Interpreter? = null
    private val TARGET_W = 28
    private val TARGET_H = 28
    private val BIN_THRESHOLD = 15
    private val MARGIN_RATIO = 0.3f
    private val MIN_STROKE_AREA = 100
    private val MAX_ASPECT_RATIO = 3.0f
    private var debugMode = true

    // --- UI ---
    private lateinit var tvResult: TextView
    private lateinit var tvTargetKana: TextView
    private lateinit var btnEvaluateHandwriting: Button
    private lateinit var btnClearCanvas: Button
    private lateinit var drawingView: DrawingView
    private lateinit var progressEvaluating: ProgressBar
    private lateinit var top3Text: TextView

    private lateinit var btnShowPronunciation: Button
    private lateinit var btnShowHandwriting: Button
    private lateinit var pronunciationLayout: LinearLayout
    private lateinit var handwritingLayout: LinearLayout
    private lateinit var pronunciationTargetText: TextView
    private lateinit var recordButton: Button

    private lateinit var btnSaveCanvas: Button

    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // --- 히라가나 테이블 46글자 ---
    private val kanaTable = arrayOf(
        "あ","い","う","え","お","か","き","く","け","こ",
        "さ","し","す","せ","そ","た","ち","つ","て","と",
        "な","に","ぬ","ね","の","は","ひ","ふ","へ","ほ",
        "ま","み","む","め","も","や","ゆ","よ",
        "ら","り","る","れ","ろ","わ","を","ん","ゝ" // 마지막에 iteration mark 추가
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // --- UI 초기화 ---
        tvResult = findViewById(R.id.resultText)
        tvTargetKana = findViewById(R.id.targetKanaText)
        btnEvaluateHandwriting = findViewById(R.id.evaluateHandwritingButton)
        btnClearCanvas = findViewById(R.id.clearCanvasButton)
        progressEvaluating = findViewById(R.id.progressEvaluating)
        top3Text = findViewById(R.id.top3Text)

        btnSaveCanvas = findViewById(R.id.saveCanvasButton) //  저장 버튼
        btnShowPronunciation = findViewById(R.id.btnShowPronunciation)
        btnShowHandwriting = findViewById(R.id.btnShowHandwriting)
        pronunciationLayout = findViewById(R.id.pronunciationLayout)
        handwritingLayout = findViewById(R.id.handwritingLayout)
        pronunciationTargetText = findViewById(R.id.pronunciationTargetText)
        recordButton = findViewById(R.id.recordButton)

        val canvasContainer = findViewById<FrameLayout>(R.id.canvasContainer)
        drawingView = DrawingView(this, null)
        canvasContainer.addView(drawingView)

        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) Toast.makeText(this, "마이크 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
        requestAudioPermission()

        // --- TFLite 모델 로드 ---
        try {
            val modelBuffer = loadModelFileSafely("k49_cnn.tflite")
            tflite = Interpreter(modelBuffer, Interpreter.Options().apply { setUseXNNPACK(true) })
            Log.d("TFLITE", "Input shape = ${tflite?.getInputTensor(0)?.shape()?.contentToString()}")
            Log.d("TFLITE", "Output shape = ${tflite?.getOutputTensor(0)?.shape()?.contentToString()}")
        } catch (e: Exception) {
            tvResult.text = "모델 로드 오류: ${e.message}"
            tvResult.setTextColor(Color.RED)
        }

        // --- 버튼 이벤트 ---
        btnEvaluateHandwriting.setOnClickListener { if (!isEvaluatingHandwriting) runHandwritingEvaluation() }
        btnClearCanvas.setOnClickListener { drawingView.clear(); tvResult.text = ""; top3Text.text = "" }

        btnShowPronunciation.setOnClickListener { showPronunciationLayout() }
        btnShowHandwriting.setOnClickListener { showHandwritingLayout() }

        recordButton.setOnClickListener { handlePronunciationButton() }

        btnSaveCanvas.setOnClickListener {
            val bmp = drawingView.getBitmap()
            if (!hasValidContent(bmp)) {
                Toast.makeText(this, "❌ 그려진 내용이 없습니다.", Toast.LENGTH_SHORT).show()
            } else {
                val filename = "hiragana_${System.currentTimeMillis()}"
                saveBitmapAsPNG(bmp, filename)

                // 저장 후 바로 평가
                if (!isEvaluatingHandwriting) {
                    runHandwritingEvaluation()
                }
            }
        }

        tvTargetKana.text = "い" // 예시 목표 히라가나
        pronunciationTargetText.text = "말할 문장: 안녕하세요"
        showHandwritingLayout()
    }

    // ================= PNG 저장 함수 =================

    private fun saveBitmapAsPNG(bitmap: Bitmap, filename: String) {
        val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        if (dir != null && !dir.exists()) dir.mkdirs()
        val file = File(dir, "$filename.png")

        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            // Toast는 반드시 Main Thread에서
            mainScope.launch(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "✅ 저장 완료: ${file.absolutePath}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            mainScope.launch(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "❌ 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }


    // ================= 손글씨 평가 =================
    private fun runHandwritingEvaluation() {
        val interpreter = tflite ?: run {
            tvResult.text = "모델이 로드되어 있지 않습니다."
            tvResult.setTextColor(Color.RED)
            return
        }

        val bitmap = drawingView.getBitmap()
        if (!hasValidContent(bitmap)) {
            tvResult.text = "❌ 그려진 내용이 없습니다. 히라가나를 써주세요."
            tvResult.setTextColor(Color.RED)
            return
        }

        isEvaluatingHandwriting = true
        tvResult.text = "평가 중..."
        progressEvaluating.visibility = View.VISIBLE
        btnEvaluateHandwriting.isEnabled = false
        btnClearCanvas.isEnabled = false

        mainScope.launch(Dispatchers.Default) {
            try {
                val input = preprocessBitmapToFloatArray(bitmap)
                val output = Array(1) { FloatArray(49) } // K49 49 클래스
                interpreter.run(input, output)

                val targetKana = tvTargetKana.text.toString()
                val targetIndex = kanaTable.indexOf(targetKana).coerceIn(0, kanaTable.size - 1)
                val confidence = output[0][targetIndex]
                val scorePercent = (confidence * 100).toInt()

                val resultText = when {
                    scorePercent >= 80 -> "✅ 정확히 따라 썼습니다: $targetKana ($scorePercent%)"
                    scorePercent >= 60 -> "⚠️ 거의 맞게 썼습니다: $targetKana ($scorePercent%)"
                    scorePercent >= 40 -> "⚠️ 조금 다릅니다: $targetKana ($scorePercent%)"
                    else -> "❌ 잘못 썼습니다: $targetKana ($scorePercent%)"
                }

                val top3 = getTopK(output[0], 3)
                val top3Str = top3.joinToString("\n") { (k, v) -> "$k: ${(v*100).toInt()}%" }

                withContext(Dispatchers.Main) {
                    tvResult.text = resultText
                    tvResult.setTextColor(
                        when {
                            scorePercent >= 80 -> Color.GREEN
                            scorePercent >= 60 -> Color.parseColor("#4CAF50")
                            scorePercent >= 40 -> Color.BLUE
                            else -> Color.RED
                        }
                    )
                    top3Text.text = "Top-3 결과:\n$top3Str"
                    drawingView.clear()
                    resetEvaluateUI()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvResult.text = "평가 오류: ${e.message}"
                    tvResult.setTextColor(Color.RED)
                    resetEvaluateUI()
                }
            }
        }
    }

    private fun resetEvaluateUI() {
        isEvaluatingHandwriting = false
        progressEvaluating.visibility = View.GONE
        btnEvaluateHandwriting.isEnabled = true
        btnClearCanvas.isEnabled = true
    }

    private fun getTopK(probs: FloatArray, k: Int): List<Pair<String, Float>> {
        return probs.mapIndexed { idx, v -> kanaTable.getOrNull(idx)?.let { it to v } }
            .filterNotNull()
            .sortedByDescending { it.second }
            .take(k)
    }

    private fun hasValidContent(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        val step = maxOf(1, minOf(width, height)/20)
        var brightPixelCount = 0
        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val c = bitmap.getPixel(x, y)
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val lum = (r+g+b)/3
                if (lum > BIN_THRESHOLD) {
                    brightPixelCount++
                    // 얇은 선도 감지 가능하도록 MIN_STROKE_AREA 대비 1/4
                    if (brightPixelCount >= MIN_STROKE_AREA/4) return true
                }
            }
        }
        return false
    }

    private fun preprocessBitmapToFloatArray(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // 글씨 영역 찾기 (bounding box)
        var left = width
        var right = 0
        var top = height
        var bottom = 0
        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val gray = ((pixels[rowOffset + x] shr 16 and 0xFF) +
                        (pixels[rowOffset + x] shr 8 and 0xFF) +
                        (pixels[rowOffset + x] and 0xFF)) / 3
                if (gray > BIN_THRESHOLD) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }

        if (left > right || top > bottom) {
            left = 0; right = width - 1; top = 0; bottom = height - 1
        }

        // 마진 적용
        val cropW = right - left + 1
        val cropH = bottom - top + 1
        val marginX = (cropW * MARGIN_RATIO).toInt().coerceAtLeast(2)
        val marginY = (cropH * MARGIN_RATIO).toInt().coerceAtLeast(2)
        val cropLeft = (left - marginX).coerceAtLeast(0)
        val cropTop = (top - marginY).coerceAtLeast(0)
        val cropRight = (right + marginX).coerceAtMost(width - 1)
        val cropBottom = (bottom + marginY).coerceAtMost(height - 1)

        val cropped = Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropRight - cropLeft + 1, cropBottom - cropTop + 1)

        // 28x28 중앙 정렬
        val targetBmp = Bitmap.createBitmap(TARGET_W, TARGET_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(targetBmp)
        canvas.drawColor(Color.BLACK)
        val scale = min(TARGET_W.toFloat() / cropped.width, TARGET_H.toFloat() / cropped.height)
        val dx = (TARGET_W - cropped.width * scale) / 2f
        val dy = (TARGET_H - cropped.height * scale) / 2f
        val matrix = Matrix()
        matrix.postScale(scale, scale)
        matrix.postTranslate(dx, dy)
        canvas.drawBitmap(cropped, matrix, Paint(Paint.ANTI_ALIAS_FLAG))

        // Gaussian Blur 적용 (간단한 커널)
        applyGaussianBlur(targetBmp)

        // FloatArray 변환 + 반전
        val input = Array(1) { Array(TARGET_H) { Array(TARGET_W) { FloatArray(1) } } }
        for (y in 0 until TARGET_H) {
            for (x in 0 until TARGET_W) {
                val p = targetBmp.getPixel(x, y)
                val gray = ((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) / 3f
                input[0][y][x][0] = 1f - (gray / 255f)
            }
        }

        // 디버그 PNG 저장
        saveBitmapAsPNG(targetBmp, "debug_preprocessed")

        return input
    }

    // 간단 Gaussian Blur (3x3)
    private fun applyGaussianBlur(bitmap: Bitmap) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val newPixels = pixels.copyOf()

        val kernel = arrayOf(
            floatArrayOf(1f, 2f, 1f),
            floatArrayOf(2f, 4f, 2f),
            floatArrayOf(1f, 2f, 1f)
        )
        val kSum = 16f

        for (y in 1 until h-1) {
            for (x in 1 until w-1) {
                var sum = 0f
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val p = pixels[(y+ky)*w + (x+kx)]
                        val gray = ((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) / 3f
                        sum += gray * kernel[ky+1][kx+1]
                    }
                }
                val avg = (sum / kSum).toInt().coerceIn(0,255)
                newPixels[y*w + x] = Color.rgb(avg, avg, avg)
            }
        }
        bitmap.setPixels(newPixels, 0, w, 0, 0, w, h)
    }



    private fun loadModelFileSafely(modelName: String): ByteBuffer {
        return try {
            assets.openFd(modelName).use { fd ->
                FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        } catch (e: Exception) {
            try {
                assets.open(modelName).use { ins ->
                    val bytes = ins.readBytes()
                    val bb = ByteBuffer.allocateDirect(bytes.size)
                    bb.put(bytes)
                    bb.rewind()
                    bb
                }
            } catch (ex: Exception) {
                throw IOException("모델 파일을 읽을 수 없습니다: ${ex.message}")
            }
        }
    }

    private fun showPronunciationLayout() {
        pronunciationLayout.visibility = View.VISIBLE
        handwritingLayout.visibility = View.GONE
        btnShowPronunciation.isEnabled = false
        btnShowHandwriting.isEnabled = true
        tvResult.text = ""
    }

    private fun showHandwritingLayout() {
        pronunciationLayout.visibility = View.GONE
        handwritingLayout.visibility = View.VISIBLE
        btnShowPronunciation.isEnabled = true
        btnShowHandwriting.isEnabled = false
        tvResult.text = ""
    }

    private fun requestAudioPermission() {
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // ================= 발음 평가 =================
    private fun handlePronunciationButton() {
        if(isEvaluatingPronunciation){
            tvResult.text = "평가 진행 중입니다. 잠시만 기다려주세요..."
            return
        }

        if(!isRecording){
            startRecording()
        } else {
            stopRecordingAndEvaluate()
        }
    }

    private fun startRecording() {
        val speechConfig = SpeechConfig.fromSubscription(AZURE_SPEECH_KEY, AZURE_SERVICE_REGION)
        speechConfig.speechRecognitionLanguage = "ko-KR"
        val audioConfig = AudioConfig.fromDefaultMicrophoneInput()
        speechRecognizer = SpeechRecognizer(speechConfig, audioConfig)

        val assessmentConfig = PronunciationAssessmentConfig(
            "안녕하세요",
            PronunciationAssessmentGradingSystem.HundredMark,
            PronunciationAssessmentGranularity.Phoneme,
            true
        )
        assessmentConfig.applyTo(speechRecognizer)

        isRecording = true
        tvResult.text = "말할 문장: 안녕하세요"
        recordButton.text = "발음 시작"
        tvResult.setTextColor(Color.BLACK)
    }

    private fun stopRecordingAndEvaluate() {
        speechRecognizer?.let { recognizer ->
            isRecording=false
            isEvaluatingPronunciation=true
            recordButton.isEnabled=false
            tvResult.text="발음 평가 진행 중..."
            Thread{
                try{
                    val futureResult: Future<SpeechRecognitionResult> = recognizer.recognizeOnceAsync()
                    val speechResult = futureResult.get()
                    runOnUiThread {
                        if(speechResult.reason==ResultReason.RecognizedSpeech){
                            val assessmentResult = PronunciationAssessmentResult.fromResult(speechResult)
                            val prosodyScoreText = assessmentResult.prosodyScore?.let{"억양 점수: $it"}?:""
                            tvResult.text = """
                                인식 결과: ${speechResult.text}
                                발음 정확도: ${assessmentResult.accuracyScore}
                                $prosodyScoreText
                            """.trimIndent()
                        } else {
                            tvResult.text="인식 실패: ${speechResult.reason}"
                        }
                        resetPronunciationUI()
                    }
                }catch(e:Exception){
                    runOnUiThread {
                        tvResult.text="오류 발생: ${e.message}"
                        resetPronunciationUI()
                    }
                }
            }.start()
        }
    }

    private fun resetPronunciationUI() {
        speechRecognizer?.close()
        speechRecognizer=null
        isEvaluatingPronunciation=false
        recordButton.isEnabled=true
        recordButton.text="다시 말하기"
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
        tflite?.close()
        tflite=null
    }
}