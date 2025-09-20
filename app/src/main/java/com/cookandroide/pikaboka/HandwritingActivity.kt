package com.cookandroide.pikaboka

import android.app.AlertDialog
import android.os.Bundle
import com.google.android.material.button.MaterialButton
import com.cookandroide.pikaboka.views.DrawingView

class HandwritingActivity : BaseActivity() {
    private lateinit var drawView: DrawingView
    private lateinit var evaluateButton: MaterialButton
    private lateinit var btnBack: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_handwriting) // 업로드된 layout 사용. :contentReference[oaicite:12]{index=12}

        drawView = findViewById(R.id.drawView)
        evaluateButton = findViewById(R.id.evaluateButton)
        btnBack = findViewById(R.id.btnBack)

        addButtonClickEffect(evaluateButton)
        addButtonClickEffect(btnBack)

        btnBack.setOnClickListener { finish() }

        evaluateButton.setOnClickListener {
            evaluateButton.isEnabled = false
            evaluateButton.text = "평가 중..."
            // 실제 TFLite 추론 로직은 여기에 연결하세요.
            // 지금은 예시로 간단한 더미 점수 생성
            Thread {
                try {
                    Thread.sleep(400) // 모킹 처리
                    val bitmap = drawView.getBitmap()
                    // TODO: bitmap 전처리 후 TFLite 모델에 넣어 결과 받아오기
                    val dummyScore = (50..100).random()

                    runOnUiThread {
                        val dialog = AlertDialog.Builder(this)
                            .setTitle("평가 결과")
                            .setMessage("손글씨 점수: $dummyScore")
                            .setPositiveButton("확인") { d, _ -> d.dismiss() }
                            .create()
                        dialog.show()
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
}
