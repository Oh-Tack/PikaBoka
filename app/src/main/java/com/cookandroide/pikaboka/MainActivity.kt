package com.cookandroide.pikaboka

import android.content.Intent
import android.os.Bundle
import com.google.android.material.button.MaterialButton

class MainActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // 업로드하신 activity_main.xml 사용. :contentReference[oaicite:8]{index=8}

        val btnSpeech = findViewById<MaterialButton>(R.id.btnSpeech)
        val btnHandwriting = findViewById<MaterialButton>(R.id.btnHandwriting)

        addButtonClickEffect(btnSpeech)
        addButtonClickEffect(btnHandwriting)

        btnSpeech.setOnClickListener {
            startActivity(Intent(this, SpeechActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        btnHandwriting.setOnClickListener {
            startActivity(Intent(this, HandwritingActivity::class.java))
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }
    }
}
