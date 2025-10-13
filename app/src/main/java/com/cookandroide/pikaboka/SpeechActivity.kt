package com.cookandroide.pikaboka

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cookandroide.pikaboka.databinding.ActivitySpeechBinding
import com.microsoft.cognitiveservices.speech.*
import com.microsoft.cognitiveservices.speech.audio.AudioConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.random.Random

data class Sentence(
    val textJa: String,
    val pronunciation: String,
    val translation: String
)

class SpeechActivity : BaseActivity() {

    private val AZURE_SPEECH_KEY = "key"
    private val AZURE_SERVICE_REGION = "key"

    private lateinit var binding: ActivitySpeechBinding
    private var sentences: List<Sentence> = emptyList()
    private var currentSentence: Sentence? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isEvaluating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpeechBinding.inflate(layoutInflater)
        setContentView(binding.root)

        addButtonClickEffect(binding.recordButton)
        addButtonClickEffect(binding.btnBack)

        binding.btnBack.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // 마이크 권한 요청
        val permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (!granted) binding.resultText.text = "마이크 권한이 필요합니다."
            }

        if (!checkPermission()) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

        // JSON에서 문장 불러오기
        sentences = loadSentencesFromAssets()
        pickRandomSentence()

        // 발음 시작 버튼
        binding.recordButton.setOnClickListener {
            if (!checkPermission()) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            else startSpeechRecognition()
        }

        resetUI()
    }

    private fun checkPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    private fun loadSentencesFromAssets(): List<Sentence> {
        val list = mutableListOf<Sentence>()
        try {
            val inputStream = assets.open("sentences.json")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonStr = reader.readText()
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    Sentence(
                        textJa = obj.getString("textJa"),
                        pronunciation = obj.getString("pronunciation"),
                        translation = obj.getString("translation")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun pickRandomSentence() {
        if (sentences.isEmpty()) return
        currentSentence = sentences[Random.nextInt(sentences.size)]
        currentSentence?.let {
            binding.targetSentence.text = "${it.textJa}\n(${it.pronunciation})\n${it.translation}"
        }
    }

    private fun startSpeechRecognition() {
        if (isEvaluating || currentSentence == null) return
        isEvaluating = true
        updateUIRecordingState()

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val speechConfig =
                        SpeechConfig.fromSubscription(AZURE_SPEECH_KEY, AZURE_SERVICE_REGION)
                    val audioConfig = AudioConfig.fromDefaultMicrophoneInput()
                    val assessmentConfig = PronunciationAssessmentConfig(
                        currentSentence!!.textJa,
                        PronunciationAssessmentGradingSystem.HundredMark,
                        PronunciationAssessmentGranularity.Phoneme,
                        true
                    )
                    speechRecognizer = SpeechRecognizer(speechConfig, "ja-JP", audioConfig)
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
                        "문장: ${currentSentence!!.textJa}\n발음: ${currentSentence!!.pronunciation}\n인식: ${result.text}\n정확도: %.2f점\n$comment".format(
                            accuracyScore
                        )

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
                pickRandomSentence()
            }
        }
    }

    private fun updateUIRecordingState() {
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
