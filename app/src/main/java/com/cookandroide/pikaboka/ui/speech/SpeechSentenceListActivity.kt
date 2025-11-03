package com.cookandroide.pikaboka.ui.speech

import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.cookandroide.pikaboka.R
import com.cookandroide.pikaboka.base.BaseActivity
import com.cookandroide.pikaboka.data.speech.SpeechRepository
import com.cookandroide.pikaboka.databinding.ActivitySpeechSentenceListBinding

class SpeechSentenceListActivity : BaseActivity() {
    private lateinit var binding: ActivitySpeechSentenceListBinding
    private lateinit var adapter: SpeechSentenceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpeechSentenceListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = getColor(R.color.feature_speech)

        addButtonClickEffect(binding.btnBack)
        binding.btnBack.setOnClickListener {
            finish()
            @Suppress("DEPRECATION") overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        val category = intent.getStringExtra("category") ?: ""
        binding.tvTitle.text = category

        val lines = SpeechRepository.getByCategory(this, category)
        adapter = SpeechSentenceAdapter(lines) { line ->
            val data = Intent().apply {
                putExtra(SpeechActivity.EXTRA_JA, line.textJa)
                putExtra(SpeechActivity.EXTRA_PRON, line.pronunciation)
                putExtra(SpeechActivity.EXTRA_TR, line.translation)
            }
            setResult(RESULT_OK, data)
            finish()
            @Suppress("DEPRECATION") overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
    }
}
