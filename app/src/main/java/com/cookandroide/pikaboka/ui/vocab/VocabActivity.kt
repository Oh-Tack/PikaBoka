package com.cookandroide.pikaboka.ui.vocab

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cookandroide.pikaboka.BuildConfig
import com.cookandroide.pikaboka.R
import com.cookandroide.pikaboka.audio.AudioPlayer
import com.cookandroide.pikaboka.base.BaseActivity
import com.cookandroide.pikaboka.data.AppDatabase
import com.cookandroide.pikaboka.data.WordEntity
import com.cookandroide.pikaboka.databinding.ActivityVocabBinding
import com.cookandroide.pikaboka.net.TtsApi
import com.cookandroide.pikaboka.net.TtsRequest
import com.cookandroide.pikaboka.ui.model.WordUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class VocabActivity : BaseActivity() {

    private lateinit var binding: ActivityVocabBinding
    private val adapter = VocabAdapter(::onSpeakClick, ::onFavoriteClick)

    private val ngrok = "https://" + BuildConfig.ngrok + "/"

    private val db by lazy { AppDatabase.get(this) }
    private val dao by lazy { db.wordDao() }

    private val ttsApi by lazy { TtsApi.create(ngrok) }

    private var isLoading = false

    // 즐겨찾기 필터 상태/구독
    private var observeJob: Job? = null
    private var showFavOnly: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVocabBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = getColor(R.color.feature_vocab)

        addButtonClickEffect(binding.btnBack)

        // 뒤로가기
        binding.btnBack.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // 리스트
        binding.rvWords.layoutManager = LinearLayoutManager(this)
        binding.rvWords.adapter = adapter

        // 최초 1회 시드
        lifecycleScope.launch { seedFromAssetsIfNeeded() }

        // 스위치 초기값 복원(+리스너)
        val pref = getSharedPreferences("vocab_ui", MODE_PRIVATE)
        showFavOnly = pref.getBoolean("showFavOnly", false)
        binding.switchFavorites.isChecked = showFavOnly
        binding.switchFavorites.setOnCheckedChangeListener { _, checked ->
            showFavOnly = checked
            pref.edit().putBoolean("showFavOnly", checked).apply()
            startObserving()
        }

        // Flow 구독 시작
        startObserving()
    }

    /** 스위치 상태에 따라 다른 Flow를 구독 */
    private fun startObserving() {
        observeJob?.cancel()
        observeJob = lifecycleScope.launch {
            val flow = if (showFavOnly) dao.getFavoritesFlow() else dao.getAllFlow()
            flow.collectLatest { list ->
                val ui = list.map { e -> WordUi(e.id, e.jp, e.kana, e.mean, e.isFavorite) }
                adapter.submitList(ui) // ListAdapter 표준 API
            }
        }
    }

    /** assets/vocab.csv → DB (한 번만) */
    private suspend fun seedFromAssetsIfNeeded() {
        val prefs = getSharedPreferences("seed", MODE_PRIVATE)
        val done = prefs.getBoolean("vocab_seeded_v2", false)
        if (done) return

        withContext(Dispatchers.IO) {
            val items = mutableListOf<WordEntity>()
            try {
                assets.open("vocab.csv").use { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { br ->
                        // 1) 헤더 파싱
                        val headerLine = br.readLine() ?: return@use
                        val rawHeaders = parseCsvLine(headerLine)

                        // BOM/공백/대소문자 정리
                        fun norm(h: String) = h.replace("\uFEFF", "").trim().lowercase()
                        val headers = rawHeaders.map(::norm)

                        // 2) 유연한 컬럼 매핑
                        val idxJp   = headers.indexOfFirst { it in setOf("jp","japanese","word","original","term","kanji") }
                        val idxKana = headers.indexOfFirst { it in setOf("kana","furigana","reading","yomi","ruby") }
                        val idxMean = headers.indexOfFirst { it in setOf("mean","meaning","english","translation","ko","kor","korean") }
                        val idxFav  = headers.indexOfFirst { it in setOf("favorite","fav","isfavorite","star","bookmark","liked") }

                        // 3) 폴백
                        val finalIdxJp   = if (idxJp   >= 0) idxJp   else 0.coerceAtMost(headers.lastIndex)
                        val finalIdxKana = if (idxKana >= 0) idxKana else 1.coerceAtMost(headers.lastIndex)
                        val finalIdxMean = if (idxMean >= 0) idxMean else 2.coerceAtMost(headers.lastIndex)
                        val finalIdxFav  = idxFav

                        fun ensureValid(i: Int, name: String) {
                            if (i !in headers.indices) {
                                throw IllegalArgumentException(
                                    "CSV에 '$name' 컬럼 매핑 실패. 헤더: ${rawHeaders.joinToString()} (정규화: ${headers.joinToString()})"
                                )
                            }
                        }
                        ensureValid(finalIdxJp,   "jp/original")
                        ensureValid(finalIdxKana, "kana/furigana")
                        ensureValid(finalIdxMean, "mean/english")

                        // 4) 데이터 파싱
                        var line: String?
                        while (br.readLine().also { line = it } != null) {
                            val s = line!!.trim()
                            if (s.isEmpty()) continue

                            val cols = parseCsvLine(s)
                            fun col(i: Int) = if (i in cols.indices) cols[i].trim() else ""

                            val jp   = col(finalIdxJp);   if (jp.isEmpty()) continue
                            val kana = col(finalIdxKana)
                            val mean = col(finalIdxMean)

                            val favRaw = if (finalIdxFav >= 0) col(finalIdxFav) else ""
                            val fav = favRaw.equals("1", true) ||
                                    favRaw.equals("true", true) ||
                                    favRaw.equals("y", true) ||
                                    favRaw.equals("yes", true)

                            items += WordEntity(jp = jp, kana = kana, mean = mean, isFavorite = fav)
                        }
                    }
                }

                if (items.isNotEmpty()) {
                    dao.insertAll(items) // OnConflict IGNORE 가정
                }
                prefs.edit().putBoolean("vocab_seeded_v2", true).apply()
            } catch (e: Exception) {
                Log.e("VocabActivity", "CSV seed error", e)
            }
        }
    }

    /** 따옴표/쉼표 처리 지원하는 간단 CSV 파서 */
    private fun parseCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when (c) {
                '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"'); i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ',' -> if (inQuotes) sb.append(c) else {
                    out += sb.toString(); sb.setLength(0)
                }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }

    private fun onFavoriteClick(word: WordUi) {
        lifecycleScope.launch(Dispatchers.IO) {
            dao.setFavorite(word.id, !word.isFavorite)
        }
    }

    private fun onSpeakClick(word: WordUi) {
        if (isLoading) return
        isLoading = true

        // 뒤로가기 비활성화
        setBackDisabled(true)
        binding.btnBack.isEnabled = false
        binding.btnBack.alpha = 0.4f

        lifecycleScope.launch {
            try {
                val res = ttsApi.ttsBasic(TtsRequest(text = word.jp, speaker = 47))
                AudioPlayer.playBase64Wav(this@VocabActivity, res.audioBase64, cacheKey = "${word.jp}_47")
            } catch (e: Exception) {
                Log.e("VocabActivity", "TTS 실패", e)
                Toast.makeText(this@VocabActivity, "TTS 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false

                // 뒤로가기 다시 활성화
                setBackDisabled(false)
                binding.btnBack.isEnabled = true
                binding.btnBack.alpha = 1f
            }
        }
    }


    override fun onPause() { super.onPause(); AudioPlayer.pause() }
    override fun onStop() { super.onStop(); AudioPlayer.pause() }

    override fun onDestroy() {
        // 관찰 Job 정리
        observeJob?.cancel()
        AudioPlayer.release()
        super.onDestroy()
    }
}
