package com.cookandroide.pikaboka

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cookandroide.pikaboka.databinding.ActivitySpeechBinding
import com.microsoft.cognitiveservices.speech.*
import com.microsoft.cognitiveservices.speech.audio.AudioConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SpeechActivity : BaseActivity() {

    private val AZURE_SPEECH_KEY = "YOUR_AZURE_SPEECH_KEY"
    private val AZURE_SERVICE_REGION = "YOUR_AZURE_SERVICE_REGION"

    private val sentences = listOf(
        "안녕하세요",
        "저는 학생입니다",
        "오늘 날씨가 좋네요",
        "발음을 연습해봅시다"
    )

    private var currentSentence: String = "안녕하세요" // 현재 평가할 문장
    private var speechRecognizer: SpeechRecognizer? = null
    private var isEvaluating = false

    private lateinit var binding: ActivitySpeechBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpeechBinding.inflate(layoutInflater)
        setContentView(binding.root)

        addButtonClickEffect(binding.recordButton)
        addButtonClickEffect(binding.btnBack)

        // 뒤로가기 버튼
        binding.btnBack.setOnClickListener {
            finish()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                val options = ActivityOptionsCompat.makeCustomAnimation(
                    this,
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
                )
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }

        // 마이크 권한 요청
        val permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (!granted) binding.resultText.text = "마이크 권한이 필요합니다."
            }

        if (!checkPermission()) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        // 평가할 문장을 미리 랜덤 선택
        pickRandomSentence()

        // 발음 시작 버튼
        binding.recordButton.setOnClickListener {
            if (!checkPermission()) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                startSpeechRecognition()
            }
        }

        resetUI()
    }

    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    // 랜덤 문장 선택 및 화면에 표시
    private fun pickRandomSentence() {
        currentSentence = sentences.random()
        binding.targetSentence.text = currentSentence
    }

    private fun startSpeechRecognition() {
        if (isEvaluating) return

        isEvaluating = true
        updateUIRecordingState(currentSentence)

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val speechConfig = SpeechConfig.fromSubscription(AZURE_SPEECH_KEY, AZURE_SERVICE_REGION)
                    val audioConfig = AudioConfig.fromDefaultMicrophoneInput()
                    val assessmentConfig = PronunciationAssessmentConfig(
                        currentSentence,
                        PronunciationAssessmentGradingSystem.HundredMark,
                        PronunciationAssessmentGranularity.Phoneme,
                        true
                    )
                    speechRecognizer = SpeechRecognizer(speechConfig, "ko-KR", audioConfig)
                    assessmentConfig.applyTo(speechRecognizer)
                    speechRecognizer!!.recognizeOnceAsync().get()
                }

                if (result.reason == ResultReason.RecognizedSpeech) {
                    val assessmentResult = PronunciationAssessmentResult.fromResult(result)
                    val accuracyScore = assessmentResult.accuracyScore
                    val comment = when {
                        accuracyScore >= 80 -> "잘했어요! 🎉"
                        accuracyScore >= 60 -> "조금 더 연습해보세요 😅"
                        else -> "다시 시도해보세요 🔄"
                    }
                    binding.resultText.text =
                        "결과: ${result.text}\n정확도: %.2f점\n$comment".format(accuracyScore)

                    val scoreColor = when {
                        accuracyScore >= 80 -> ContextCompat.getColor(this@SpeechActivity, R.color.green)
                        accuracyScore >= 60 -> ContextCompat.getColor(this@SpeechActivity, R.color.orange)
                        else -> ContextCompat.getColor(this@SpeechActivity, R.color.red)
                    }
                    binding.resultText.setTextColor(scoreColor)
                } else {
                    binding.resultText.text = "인식 실패: ${result.reason}"
                    binding.resultText.setTextColor(Color.parseColor("#F44336"))
                }

            } catch (e: Exception) {
                binding.resultText.text = "오류 발생: ${e.message}"
                binding.resultText.setTextColor(Color.parseColor("#F44336"))
            } finally {
                resetUI()
                // 평가 후 다음 문장 랜덤 선택
                pickRandomSentence()
            }
        }
    }

    private fun updateUIRecordingState(currentSentence: String) {
        binding.recordButton.isEnabled = false
        binding.recordButton.text = "🎤 인식 중..."
        binding.recordButton.setBackgroundColor(ContextCompat.getColor(this, R.color.purple_500))
        binding.resultText.text = "발음 평가 진행 중..."
        binding.resultText.setTextColor(Color.BLACK)
    }

    private fun resetUI() {
        speechRecognizer?.close()
        speechRecognizer = null
        isEvaluating = false
        binding.recordButton.isEnabled = true
        binding.recordButton.text = "🎤 발음 시작"
        binding.recordButton.setBackgroundColor(ContextCompat.getColor(this, R.color.blue))
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.close()
        speechRecognizer = null
    }
}
