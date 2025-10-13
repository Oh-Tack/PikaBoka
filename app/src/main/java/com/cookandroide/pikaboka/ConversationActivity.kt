package com.cookandroide.pikaboka

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.cookandroide.pikaboka.databinding.ActivityConversationBinding
import com.microsoft.cognitiveservices.speech.SpeechConfig
import com.microsoft.cognitiveservices.speech.SpeechRecognizer
import com.microsoft.cognitiveservices.speech.RecognitionResult
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject

class ConversationActivity : BaseActivity() {

    private lateinit var binding: ActivityConversationBinding

    private var webSocket: WebSocket? = null
    private var wsClient: OkHttpClient? = null

    private val AZURE_SPEECH_KEY = "key"
    private val AZURE_SERVICE_REGION = "key"
    private val WSUrl = "key"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConversationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkMicrophonePermission()
        initWebSocket()

        binding.btnSpeak.setOnClickListener {
            binding.btnSpeak.isEnabled = false
            binding.progressBar.visibility = View.VISIBLE
            CoroutineScope(Dispatchers.Main).launch {
                startSpeechRecognition()
            }
        }

        addButtonClickEffect(binding.btnBack)

        // 뒤로가기 버튼
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
        val request = Request.Builder().url(WSUrl).build()

        webSocket = wsClient?.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WS", "✅ Connected to server")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WS", "📩 Received: $text")
                try {
                    val json = JSONObject(text)
                    if (json.getString("type") == "response") {
                        val ja = json.getString("text_ja")
                        val ko = json.getString("text_ko")
                        val emotion = json.optString("emotion", "落ち着き")
                        val audioBase64 = json.getString("audio")

                        runOnUiThread {
                            binding.progressBar.visibility = View.GONE
                            binding.tvJapanese.text = "$ja\n\n🎭 감정: $emotion"
                            binding.tvKorean.text = ko
                            binding.btnSpeak.isEnabled = true
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
                    binding.progressBar.visibility = View.GONE
                    binding.btnSpeak.isEnabled = true
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WS", "🔌 WebSocket closed: $reason")
            }
        })
    }

    // 🎙️ suspend 기반 음성 인식
    private suspend fun startSpeechRecognition() {
        var recognizer: SpeechRecognizer? = null
        try {
            val speechConfig = SpeechConfig.fromSubscription(AZURE_SPEECH_KEY, AZURE_SERVICE_REGION)
            speechConfig.speechRecognitionLanguage = "ja-JP"

            val audioConfig = com.microsoft.cognitiveservices.speech.audio.AudioConfig.fromDefaultMicrophoneInput()
            recognizer = SpeechRecognizer(speechConfig, audioConfig)

            val result: RecognitionResult = withContext(Dispatchers.IO) {
                recognizer.recognizeOnceAsync().get()
            }

            val recognizedText = result.text ?: ""

            binding.tvJapanese.text = recognizedText

            if (recognizedText.isNotEmpty()) {
                sendToServer(recognizedText)
            } else {
                binding.progressBar.visibility = View.GONE
                binding.btnSpeak.isEnabled = true
            }

        } catch (e: Exception) {
            e.printStackTrace()
            binding.tvJapanese.text = "인식 실패"
            binding.tvKorean.text = ""
            binding.progressBar.visibility = View.GONE
            binding.btnSpeak.isEnabled = true
        } finally {
            recognizer?.close()
        }
    }

    private fun sendToServer(text: String) {
        webSocket?.let {
            val json = """{"text":"$text"}"""
            it.send(json)
        } ?: Log.e("WS", "WebSocket not connected")
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
    }
}
