package com.cookandroide.pikaboka.ui.conversation

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.MotionEvent
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.cookandroide.pikaboka.BuildConfig
import com.cookandroide.pikaboka.base.BaseActivity
import com.cookandroide.pikaboka.R
import com.cookandroide.pikaboka.databinding.ActivityConversationBinding
import com.google.android.material.button.MaterialButton
import com.microsoft.cognitiveservices.speech.SpeechConfig
import com.microsoft.cognitiveservices.speech.SpeechRecognizer
import com.microsoft.cognitiveservices.speech.RecognitionResult
import com.microsoft.cognitiveservices.speech.audio.AudioConfig
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject

class ConversationActivity : BaseActivity() {

    private lateinit var binding: ActivityConversationBinding
    private var webSocket: WebSocket? = null
    private var wsClient: OkHttpClient? = null

    private var blinkAnimator: ObjectAnimator? = null
    private var isRecording = false

    // API 키
    private val AZURE_SPEECH_KEY = BuildConfig.AZURE_SPEECH_KEY
    private val AZURE_SERVICE_REGION = BuildConfig.AZURE_SERVICE_REGION
    private val ngrok = "wss://" + BuildConfig.ngrok

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConversationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = getColor(R.color.feature_conversation)

        addButtonClickEffect(binding.btnBack)
        setupRoundSpeakButton()

        checkMicrophonePermission()
        initWebSocket()

        binding.btnSpeak.setOnClickListener {
            if (isRecording) return@setOnClickListener
            CoroutineScope(Dispatchers.Main).launch { startSpeechRecognition() }
        }

        binding.btnBack.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private fun checkMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                1
            )
        }
    }

    private fun initWebSocket() {
        wsClient = OkHttpClient()
        val request = Request.Builder().url(ngrok).build()

        webSocket = wsClient?.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WS", "✅ Connected to server")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    if (json.getString("type") == "response") {
                        val ja = json.getString("text_ja")
                        val ko = json.getString("text_ko")
                        val emotion = json.optString("emotion", "落ち着き")
                        val audioBase64 = json.getString("audio")

                        runOnUiThread {
                            binding.tvJapanese.text = "$ja\n"
                            binding.tvKorean.text = ko

                            stopRecordingVisuals() // 인식 종료 시 복구
                            binding.btnSpeak.isEnabled = true
                            isRecording = false
                        }

                        playBase64Audio(audioBase64)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WS", "❌ WebSocket error: ${t.message}")
                runOnUiThread {
                    stopRecordingVisuals()
                    binding.btnSpeak.isEnabled = true
                    isRecording = false
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WS", "🔌 WebSocket closed: $reason")
            }
        })
    }

    // 음성 인식
    private suspend fun startSpeechRecognition() {
        var recognizer: SpeechRecognizer? = null
        isRecording = true
        startRecordingVisuals() // 마이크 버튼 효과(깜박임)

        setBackDisabled(true)
        binding.btnBack.isEnabled = false
        binding.btnBack.alpha = 0.4f
        binding.btnSpeak.isEnabled = false

        try {
            val speechConfig = SpeechConfig.fromSubscription(AZURE_SPEECH_KEY, AZURE_SERVICE_REGION)
            speechConfig.speechRecognitionLanguage = "ja-JP"

            val audioConfig = AudioConfig.fromDefaultMicrophoneInput()
            recognizer = SpeechRecognizer(speechConfig, audioConfig)

            val result: RecognitionResult = withContext(Dispatchers.IO) {
                recognizer.recognizeOnceAsync().get()
            }

            val recognizedText = result.text ?: ""
            binding.tvJapanese.text = recognizedText

            if (recognizedText.isNotEmpty()) sendToServer(recognizedText)
            else {
                stopRecordingVisuals()
                binding.btnSpeak.isEnabled = true
                isRecording = false
            }

        } catch (e: Exception) {
            e.printStackTrace()
            binding.tvJapanese.text = "인식 실패"
            binding.tvKorean.text = ""
            stopRecordingVisuals()
            binding.btnSpeak.isEnabled = true
            isRecording = false
        } finally {
            recognizer?.close()
            setBackDisabled(false)
            binding.btnBack.isEnabled = true
            binding.btnBack.alpha = 1f
        }
    }

    private fun sendToServer(text: String) {
        webSocket?.send("""{"text":"$text"}""") ?: Log.e("WS", "WebSocket not connected")
    }

    private fun playBase64Audio(base64: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val decoded = Base64.decode(base64, Base64.DEFAULT)
                val minBufferSize = AudioTrack.getMinBufferSize(
                    24000,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(24000)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack.play()
                audioTrack.write(decoded, 0, decoded.size)
                audioTrack.stop()
                audioTrack.release()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 마이크 버튼 효과 설정
    private fun setupRoundSpeakButton() {
        val btn = binding.btnSpeak
        btn.text = "🎤"
        btn.contentDescription = "음성 입력 버튼"

        btn.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(120).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            false
        }
    }

    // 녹음 중 시각 효과
    private fun startRecordingVisuals() {
        val btn = binding.btnSpeak as MaterialButton
        btn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.red))
        btn.text = "⏹"

        blinkAnimator?.cancel()
        blinkAnimator = ObjectAnimator.ofFloat(btn, "alpha", 1f, 0.4f, 1f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    // 인식 종료 후 복구
    private fun stopRecordingVisuals() {
        val btn = binding.btnSpeak as MaterialButton
        blinkAnimator?.cancel()
        blinkAnimator = null
        btn.alpha = 1f
        btn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.feature_conversation))
        btn.text = "🎤"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
            AlertDialog.Builder(this)
                .setTitle("권한 필요")
                .setMessage("마이크 권한이 필요합니다 🎙️")
                .setPositiveButton("허용") { _, _ -> checkMicrophonePermission() }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocket?.close(1000, null)
        wsClient?.dispatcher?.executorService?.shutdown()
        blinkAnimator?.cancel()
    }
}
