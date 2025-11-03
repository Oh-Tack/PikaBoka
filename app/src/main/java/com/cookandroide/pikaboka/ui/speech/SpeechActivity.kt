package com.cookandroide.pikaboka.ui.speech

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cookandroide.pikaboka.BuildConfig
import com.cookandroide.pikaboka.R
import com.cookandroide.pikaboka.audio.AudioPlayer
import com.cookandroide.pikaboka.base.BaseActivity
import com.cookandroide.pikaboka.data.speech.SpeechRepository
import com.cookandroide.pikaboka.databinding.ActivitySpeechBinding
import com.cookandroide.pikaboka.net.TtsApi
import com.cookandroide.pikaboka.net.TtsRequest
import com.google.android.material.button.MaterialButton
import com.microsoft.cognitiveservices.speech.*
import com.microsoft.cognitiveservices.speech.audio.AudioConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class SpeechActivity : BaseActivity() {

    companion object {
        const val EXTRA_JA = "extra_ja"
        const val EXTRA_PRON = "extra_pron"
        const val EXTRA_TR = "extra_tr"

        fun intentForSentence(ctx: Context, ja: String, pron: String, tr: String): Intent =
            Intent(ctx, SpeechActivity::class.java).apply {
                putExtra(EXTRA_JA, ja)
                putExtra(EXTRA_PRON, pron)
                putExtra(EXTRA_TR, tr)
            }
    }

    // API 키
    private val AZURE_SPEECH_KEY = BuildConfig.AZURE_SPEECH_KEY
    private val AZURE_SERVICE_REGION = BuildConfig.AZURE_SERVICE_REGION
    private val ngrok = "https://" + BuildConfig.ngrok + "/"

    private lateinit var binding: ActivitySpeechBinding
    private var blinkAnimator: ObjectAnimator? = null
    private var isEvaluating = false
    private var speechRecognizer: SpeechRecognizer? = null

    private data class Sentence(val textJa: String, val pronunciation: String, val translation: String)
    private var allSentences: List<Sentence> = emptyList()
    private var currentSentence: Sentence? = null

    private val ttsApi by lazy { TtsApi.create(ngrok) }

    private val pickSentenceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == RESULT_OK && res.data != null) {
            val ja = res.data!!.getStringExtra(EXTRA_JA) ?: return@registerForActivityResult
            val pr = res.data!!.getStringExtra(EXTRA_PRON).orEmpty()
            val tr = res.data!!.getStringExtra(EXTRA_TR).orEmpty()
            currentSentence = Sentence(ja, pr, tr)
            binding.targetSentence.text = "$ja\n($pr)\n$tr"
            binding.resultText.text = getString(R.string.press_to_start)
            binding.resultText.setTextColor(ContextCompat.getColor(this, R.color.black))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpeechBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = getColor(R.color.feature_speech)

        // 버튼 이펙트
        addButtonClickEffect(binding.btnBack)
        addButtonClickEffect(binding.btnChooseSentence)
        addButtonClickEffect(binding.btnSpeak)

        binding.btnBack.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        binding.btnChooseSentence.setOnClickListener {
            pickSentenceLauncher.launch(Intent(this, SpeechCategoryActivity::class.java))
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        binding.btnSpeak.setOnClickListener {
            val s = currentSentence ?: run {
                Toast.makeText(this, "먼저 연습 문장을 선택하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (isEvaluating) {
                Toast.makeText(this, "평가 중에는 재생할 수 없어요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            setBackDisabled(true)
            lifecycleScope.launch {
                try {
                    val res = ttsApi.ttsBasic(TtsRequest(text = s.textJa, speaker = 47))
                    AudioPlayer.playBase64Wav(this@SpeechActivity, res.audioBase64, cacheKey = "${s.textJa}_47")
                } catch (e: Exception) {
                    Toast.makeText(this@SpeechActivity, "TTS 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    setBackDisabled(false)
                }
            }
        }

        val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) binding.resultText.text = "마이크 권한이 필요합니다."
        }
        if (!hasMicPermission()) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

        val ja = intent.getStringExtra(EXTRA_JA)
        if (ja != null) {
            val pr = intent.getStringExtra(EXTRA_PRON).orEmpty()
            val tr = intent.getStringExtra(EXTRA_TR).orEmpty()
            currentSentence = Sentence(ja, pr, tr)
        } else {
            val all = SpeechRepository.loadAll(this)
            allSentences = all.map { Sentence(it.textJa, it.pronunciation, it.translation) }
            pickRandomSentence()
        }

        currentSentence?.let {
            binding.targetSentence.text = "${it.textJa}\n(${it.pronunciation})\n${it.translation}"
        }

        addButtonClickEffect(binding.recordButton)
        binding.recordButton.setOnClickListener {
            if (!hasMicPermission()) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            else {
                val s = currentSentence ?: run {
                    Toast.makeText(this, "먼저 연습 문장을 선택하세요.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                startSpeechRecognition(s)
            }
        }

        resetUI()
        setupRoundRecordButton()
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    private fun pickRandomSentence() {
        if (allSentences.isEmpty()) {
            val all = SpeechRepository.loadAll(this)
            allSentences = all.map { Sentence(it.textJa, it.pronunciation, it.translation) }
        }
        if (allSentences.isEmpty()) return
        currentSentence = allSentences[Random.nextInt(allSentences.size)]
        currentSentence?.let {
            binding.targetSentence.text = "${it.textJa}\n(${it.pronunciation})\n${it.translation}"
        }
    }

    // 음성 인식 / 평가
    private fun startSpeechRecognition(s: Sentence) {
        if (isEvaluating) return
        isEvaluating = true

        setBackDisabled(true)
        binding.btnBack.isEnabled = false
        binding.btnBack.alpha = 0.4f
        binding.btnChooseSentence.isEnabled = false
        binding.btnChooseSentence.alpha = 0.4f
        binding.btnSpeak.isEnabled = false
        binding.btnSpeak.alpha = 0.4f
        binding.recordButton.isEnabled = false

        updateUIRecordingState()
        startRecordingVisuals()

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val speechConfig = SpeechConfig.fromSubscription(AZURE_SPEECH_KEY, AZURE_SERVICE_REGION).apply {
                        speechRecognitionLanguage = "ja-JP"
                        setProperty(PropertyId.SpeechServiceConnection_InitialSilenceTimeoutMs, "4000")
                        setProperty(PropertyId.SpeechServiceConnection_EndSilenceTimeoutMs, "1800")
                    }
                    val audioConfig = AudioConfig.fromDefaultMicrophoneInput()

                    val ref = s.textJa.trim().replace('　', ' ').replace(Regex("\\s+"), " ")
                    val assessmentConfig = PronunciationAssessmentConfig(
                        ref,
                        PronunciationAssessmentGradingSystem.HundredMark,
                        PronunciationAssessmentGranularity.Phoneme,
                        true
                    )
                    val recognizer = SpeechRecognizer(speechConfig, audioConfig).also {
                        assessmentConfig.applyTo(it)
                    }
                    try {
                        recognizer.recognizeOnceAsync().get()
                    } finally {
                        recognizer.close()
                    }
                }

                when (result.reason) {
                    ResultReason.RecognizedSpeech -> {
                        val pa = PronunciationAssessmentResult.fromResult(result)
                        val accuracy = pa.accuracyScore
                        val comment = when {
                            accuracy >= 80 -> "잘했어요! 🎉"
                            accuracy >= 60 -> "조금 더 연습해보세요 😅"
                            else -> "다시 시도해보세요 🔄"
                        }
                        binding.resultText.text = "문장: ${s.textJa}\n" +
                                "발음: ${s.pronunciation}\n" +
                                "인식: ${result.text}\n" +
                                "정확도: %.2f점\n$comment".format(accuracy)
                        val scoreColor = when {
                            accuracy >= 80 -> ContextCompat.getColor(this@SpeechActivity, R.color.green)
                            accuracy >= 60 -> ContextCompat.getColor(this@SpeechActivity, R.color.orange)
                            else -> ContextCompat.getColor(this@SpeechActivity, R.color.red)
                        }
                        binding.resultText.setTextColor(scoreColor)
                    }

                    ResultReason.NoMatch -> {
                        val details = NoMatchDetails.fromResult(result)
                        binding.resultText.text = "인식 실패(NoMatch): ${details.reason}"
                        binding.resultText.setTextColor(Color.parseColor("#F44336"))
                    }

                    ResultReason.Canceled -> {
                        val cancel = CancellationDetails.fromResult(result)
                        binding.resultText.text = buildString {
                            append("취소됨: ${cancel.reason}")
                            cancel.errorDetails?.let { append("\n$it") }
                        }
                        binding.resultText.setTextColor(Color.parseColor("#F44336"))
                    }

                    else -> {
                        binding.resultText.text = "인식 실패: ${result.reason}"
                        binding.resultText.setTextColor(Color.parseColor("#F44336"))
                    }
                }
            } catch (e: Exception) {
                binding.resultText.text = "오류 발생: ${e.message}"
                binding.resultText.setTextColor(Color.parseColor("#F44336"))
            } finally {
                stopRecordingVisuals()
                resetUI()

                setBackDisabled(false)
                binding.btnBack.isEnabled = true
                binding.btnBack.alpha = 1f
                binding.btnChooseSentence.isEnabled = true
                binding.btnChooseSentence.alpha = 1f
                binding.btnSpeak.isEnabled = true
                binding.btnSpeak.alpha = 1f
                binding.recordButton.isEnabled = true
            }
        }
    }

    private fun updateUIRecordingState() {
        binding.resultText.text = "발음 평가 진행 중..."
        binding.resultText.setTextColor(Color.BLACK)
    }

    private fun resetUI() {
        speechRecognizer?.close()
        speechRecognizer = null
        isEvaluating = false
        binding.recordButton.isEnabled = true
        stopRecordingVisuals()
    }

    // 마이크 버튼
    private fun setupRoundRecordButton() {
        val btn = binding.recordButton
        btn.text = "🎤"
        btn.contentDescription = "발음 시작 버튼"
        btn.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(100).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }
            false
        }
    }

    // 마이크 버튼 효과
    private fun startRecordingVisuals() {
        val btn = binding.recordButton as MaterialButton
        btn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.red))
        btn.text = "⏹"
        blinkAnimator?.cancel()
        blinkAnimator = ObjectAnimator.ofFloat(btn, "alpha", 1f, 0.4f, 1f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun stopRecordingVisuals() {
        val btn = binding.recordButton as MaterialButton
        blinkAnimator?.cancel()
        blinkAnimator = null
        btn.alpha = 1f
        btn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.feature_speech))
        btn.text = "🎤"
    }

    override fun onPause() {
        super.onPause()
        AudioPlayer.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.close()
        speechRecognizer = null
        AudioPlayer.release()
    }
}
