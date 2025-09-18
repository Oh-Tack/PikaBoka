package com.cookandroide.pikaboka

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.microsoft.cognitiveservices.speech.*
import com.microsoft.cognitiveservices.speech.audio.AudioConfig
import java.util.concurrent.Future

class MainActivity : AppCompatActivity() {

    private val AZURE_SPEECH_KEY = "AZURE_SPEECH_KEY"
    private val AZURE_SERVICE_REGION = "AZURE_SERVICE_REGION"

    private var speechRecognizer: SpeechRecognizer? = null
    private var isRecording = false
    private var isEvaluating = false

    private lateinit var tvResult: TextView
    private lateinit var btnRecord: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvResult = findViewById(R.id.resultText)
        btnRecord = findViewById(R.id.recordButton)

        // 마이크 권한 요청
        val permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (!granted) tvResult.text = "마이크 권한이 필요합니다."
            }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        btnRecord.setOnClickListener {
            if (isEvaluating) {
                tvResult.text = "평가 진행 중입니다. 잠시만 기다려주세요..."
                return@setOnClickListener
            }

            if (!isRecording) {
                startRecording()
            } else {
                stopRecordingAndEvaluate()
            }
        }
    }

    private fun startRecording() {
        val speechConfig = SpeechConfig.fromSubscription(AZURE_SPEECH_KEY, AZURE_SERVICE_REGION)
        speechConfig.speechRecognitionLanguage = "ko-KR"

        val audioConfig = AudioConfig.fromDefaultMicrophoneInput()
        speechRecognizer = SpeechRecognizer(speechConfig, audioConfig)

        // 발음 평가 설정
        val assessmentConfig = PronunciationAssessmentConfig(
            "안녕하세요",
            PronunciationAssessmentGradingSystem.HundredMark,
            PronunciationAssessmentGranularity.Phoneme,
            true
        )
        assessmentConfig.applyTo(speechRecognizer)

        isRecording = true
        tvResult.text = "말할 문장: 안녕하세요"
        btnRecord.text = "발음 시작"
        tvResult.setTextColor(ContextCompat.getColor(this, R.color.black))
        btnRecord.setBackgroundColor(ContextCompat.getColor(this, R.color.purple))
    }

    private fun stopRecordingAndEvaluate() {
        speechRecognizer?.let { recognizer ->
            isRecording = false
            isEvaluating = true
            btnRecord.isEnabled = false
            tvResult.text = "발음 평가 진행 중..."
            btnRecord.text = "발음 중..."
            btnRecord.setBackgroundColor(ContextCompat.getColor(this, R.color.gray)) // 평가 중 색상

            Thread {
                try {
                    val futureResult: Future<SpeechRecognitionResult> = recognizer.recognizeOnceAsync()
                    val speechResult = futureResult.get()

                    runOnUiThread {
                        if (speechResult.reason == ResultReason.RecognizedSpeech) {
                            val assessmentResult = PronunciationAssessmentResult.fromResult(speechResult)

                            val prosodyScoreText = assessmentResult.prosodyScore?.let { "억양 점수: $it" } ?: ""

                            val resultText = """
                                인식 결과: ${speechResult.text}
                                발음 정확도: ${assessmentResult.accuracyScore}
                                $prosodyScoreText
                            """.trimIndent()

                            // 발음 완성도: ${assessmentResult.completenessScore}
                            // 발음 유창성: ${assessmentResult.fluencyScore}
                            // 종합 발음 점수: ${assessmentResult.pronunciationScore}

                            tvResult.text = resultText

                            // 발음 점수 색상 강조
                            val scoreColor = when {
                                assessmentResult.accuracyScore >= 80 -> ContextCompat.getColor(this, R.color.green) // 초록
                                assessmentResult.accuracyScore >= 60 -> ContextCompat.getColor(this, R.color.orange) // 주황
                                else -> ContextCompat.getColor(this, R.color.red) // 빨강
                            }
                            tvResult.setTextColor(scoreColor)

                        } else {
                            tvResult.text = "인식 실패: ${speechResult.reason}"
                            tvResult.setTextColor(Color.parseColor("#F44336"))
                        }
                        resetUI()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        tvResult.text = "오류 발생: ${e.message}"
                        tvResult.setTextColor(Color.parseColor("#F44336"))
                        resetUI()
                    }
                }
            }.start()
        }
    }

    private fun resetUI() {
        speechRecognizer?.close()
        speechRecognizer = null
        isEvaluating = false
        btnRecord.isEnabled = true
        btnRecord.text = "다시 말하기"
        btnRecord.setBackgroundColor(ContextCompat.getColor(this, R.color.blue)) // 기본 색상
    }
}
