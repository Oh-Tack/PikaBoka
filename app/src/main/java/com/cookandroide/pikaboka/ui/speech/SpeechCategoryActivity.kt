package com.cookandroide.pikaboka.ui.speech

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import com.cookandroide.pikaboka.R
import com.cookandroide.pikaboka.base.BaseActivity
import com.cookandroide.pikaboka.data.speech.SpeechRepository
import com.cookandroide.pikaboka.databinding.ActivitySpeechCategoryBinding

class SpeechCategoryActivity : BaseActivity() {

    private lateinit var binding: ActivitySpeechCategoryBinding
    private lateinit var adapter: SpeechCategoryAdapter

    // 카테고리 → 문장 리스트 → 선택 결과를 그대로 되돌려주고 종료
    private val sentencePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == RESULT_OK && res.data != null) {
            setResult(RESULT_OK, res.data)
            finish()
            @Suppress("DEPRECATION") overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpeechCategoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = getColor(R.color.feature_speech)

        addButtonClickEffect(binding.btnBack)
        binding.btnBack.setOnClickListener {
            finish()
            @Suppress("DEPRECATION") overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        val categories = SpeechRepository.getCategories(this)
        adapter = SpeechCategoryAdapter(categories) { (name, _) ->
            val intent = Intent(this, SpeechSentenceListActivity::class.java).putExtra("category", name)
            sentencePicker.launch(intent)
            @Suppress("DEPRECATION") overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
    }
}
