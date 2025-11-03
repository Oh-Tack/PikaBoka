package com.cookandroide.pikaboka.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityOptionsCompat
import com.cookandroide.pikaboka.base.BaseActivity
import com.cookandroide.pikaboka.R
import com.cookandroide.pikaboka.ui.vocab.VocabActivity
import com.cookandroide.pikaboka.ui.conversation.ConversationActivity
import com.cookandroide.pikaboka.ui.handwriting.HandwritingActivity
import com.cookandroide.pikaboka.ui.speech.SpeechActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.util.Locale

class MainActivity : BaseActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val REQUEST_LOCATION = 1001
    private val WEATHER_API_KEY = "5f7f1eb0a7cf8dd218285bb653050025"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.statusBarColor = getColor(R.color.toolbar_primary)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val btnSpeech = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardSpeech)
        val btnHandwriting = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardHandwriting)
        val btnConversation = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardConversation)
        val btnVocab = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardVocab)

        val weatherTitle = findViewById<TextView>(R.id.txtWeatherTitle)
        val weatherMain = findViewById<TextView>(R.id.txtWeatherMain)
        val weatherIcon = findViewById<ImageView>(R.id.iconWeather)
        val btnRefresh = findViewById<ImageButton>(R.id.btnRefreshWeather)

        addButtonClickEffect(btnSpeech)
        addButtonClickEffect(btnHandwriting)
        addButtonClickEffect(btnConversation)
        addButtonClickEffect(btnVocab)

        btnSpeech.setOnClickListener { fadeStartActivity(SpeechActivity::class.java) }
        btnHandwriting.setOnClickListener { fadeStartActivity(HandwritingActivity::class.java) }
        btnConversation.setOnClickListener { fadeStartActivity(ConversationActivity::class.java) }
        btnVocab.setOnClickListener { fadeStartActivity(VocabActivity::class.java) }

        // 초기 위치 기반 날씨 불러오기
        getUserLocation(weatherTitle, weatherMain, weatherIcon)

        // 새로고침 버튼 클릭 시 (회전 + 재요청)
        btnRefresh.setOnClickListener {
            startRotateAnimation(btnRefresh)
            getUserLocation(weatherTitle, weatherMain, weatherIcon)
        }
    }

    private fun fadeStartActivity(target: Class<*>) {
        val intent = Intent(this, target)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            val options = ActivityOptionsCompat.makeCustomAnimation(
                this,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            startActivity(intent, options.toBundle())
        } else {
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private fun startRotateAnimation(view: ImageButton) {
        val rotate = RotateAnimation(
            0f, 360f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        rotate.duration = 800
        rotate.interpolator = LinearInterpolator()
        rotate.repeatCount = 0
        view.startAnimation(rotate)
    }

    private fun getUserLocation(titleView: TextView, mainView: TextView, iconView: ImageView) {
        try {
            val fineGranted = ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val coarseGranted = ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!fineGranted && !coarseGranted) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    REQUEST_LOCATION
                )
                return
            }

            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        fetchWeatherAndPlace(location.latitude, location.longitude, titleView, mainView, iconView)
                    } else {
                        titleView.text = "No location"
                        mainView.text = "Unable to get current position"
                        iconView.setImageResource(R.drawable.ic_cloud_off_24)
                    }
                }
                .addOnFailureListener {
                    titleView.text = "Location error"
                    mainView.text = "Failed to fetch location"
                    iconView.setImageResource(R.drawable.ic_cloud_off_24)
                }

        } catch (_: SecurityException) {
            titleView.text = "Permission denied"
            mainView.text = "Location permission is required"
            iconView.setImageResource(R.drawable.ic_cloud_off_24)
        }
    }

    // 날씨 + 영어 도시명 동시 갱신
    private fun fetchWeatherAndPlace(
        lat: Double,
        lon: Double,
        titleView: TextView,
        mainView: TextView,
        iconView: ImageView
    ) {
        Thread {
            val client = OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(15))
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(15))
                .build()

            // 1️) 영어 도시명 해석
            val englishPlace = resolveEnglishPlace(lat, lon, client)

            // 2️) 날씨 호출 (영어 설명 + 섭씨 유지)
            try {
                val url = "https://api.openweathermap.org/data/2.5/weather" +
                        "?lat=$lat&lon=$lon&units=metric&lang=en&appid=$WEATHER_API_KEY"

                val req = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .build()

                val res = client.newCall(req).execute()
                val body = res.body?.string()

                if (!res.isSuccessful || body.isNullOrBlank()) {
                    runOnUiThread {
                        titleView.text = englishPlace ?: "Current Location"
                        mainView.text = "Weather request failed (${res.code})"
                        iconView.setImageResource(R.drawable.ic_cloud_off_24)
                    }
                    return@Thread
                }

                val obj = JSONObject(body)
                val main = obj.optJSONObject("main")
                val weather0 = obj.optJSONArray("weather")?.optJSONObject(0)

                if (main == null || weather0 == null) {
                    runOnUiThread {
                        titleView.text = englishPlace ?: "Current Location"
                        mainView.text = "Weather data missing"
                        iconView.setImageResource(R.drawable.ic_cloud_off_24)
                    }
                    return@Thread
                }

                val weatherId = weather0.optInt("id", 800)
                val desc = weather0.optString("description", "Weather")
                    .replaceFirstChar { it.uppercase() }
                val temp = main.optDouble("temp", Double.NaN)
                val feels = main.optDouble("feels_like", Double.NaN)

                val iconRes = when {
                    weatherId in 200..232 -> R.drawable.ic_weather_thunder_24
                    weatherId in 300..321 -> R.drawable.ic_weather_drizzle_24
                    weatherId in 500..531 -> R.drawable.ic_weather_rain_24
                    weatherId in 600..622 -> R.drawable.ic_weather_snow_24
                    weatherId in 700..781 -> R.drawable.ic_weather_mist_24
                    weatherId in 801..804 -> R.drawable.ic_cloud_24
                    else -> R.drawable.ic_wb_sunny_24
                }

                runOnUiThread {
                    titleView.text = englishPlace ?: "Current Location"
                    val tempStr = if (temp.isNaN()) "—" else "${temp.toInt()}℃"
                    val feelStr = if (feels.isNaN()) "" else " / Feels like ${feels.toInt()}℃"
                    mainView.text = "$desc · $tempStr$feelStr"
                    iconView.setImageResource(iconRes)
                }

            } catch (t: Throwable) {
                runOnUiThread {
                    titleView.text = englishPlace ?: "Weather error"
                    mainView.text = "Network or parse error: ${t.localizedMessage}"
                    iconView.setImageResource(R.drawable.ic_cloud_off_24)
                }
            }
        }.start()
    }

    /**
     * 영어 도시명 해석:
     * 1) Android Geocoder(Locale.ENGLISH)
     * 2) 실패 시 OpenWeatherMap Reverse Geocoding (/geo/1.0/reverse)
     */
    private fun resolveEnglishPlace(lat: Double, lon: Double, client: OkHttpClient): String? {
        // (A) Geocoder(Locale.ENGLISH)
        try {
            val geocoder = Geocoder(this, Locale.ENGLISH)
            val list = geocoder.getFromLocation(lat, lon, 1)
            if (!list.isNullOrEmpty()) {
                val a = list[0]
                val city = a.subAdminArea ?: a.locality ?: a.subLocality
                val state = a.adminArea
                val countryCode = a.countryCode
                val parts = listOfNotNull(city, state, countryCode).filter { it.isNotBlank() }
                if (parts.isNotEmpty()) return parts.joinToString(", ")
            }
        } catch (_: Throwable) {
            // 무시하고 다음 단계로
        }

        // (B) OWM Reverse Geocoding
        return try {
            val url = "https://api.openweathermap.org/geo/1.0/reverse?lat=$lat&lon=$lon&limit=3&appid=$WEATHER_API_KEY"
            val req = Request.Builder().url(url).build()
            val res = client.newCall(req).execute()
            val body = res.body?.string() ?: return null
            val arr = JSONArray(body)
            if (arr.length() == 0) return null
            val obj = arr.getJSONObject(0)
            val name = obj.optString("name")
            val state = obj.optString("state")
            val country = obj.optString("country")

            val city = when (country) {
                "JP" -> normalizeJapaneseLocalName(name)
                else -> normalizeKoreanLocalName(name)
            }

            val parts = listOfNotNull(city, if (state.isNotBlank()) state else null, country)
            parts.filter { it.isNotBlank() }.joinToString(", ")
        } catch (_: Throwable) {
            null
        }
    }

    // 일본용 지명 정규화 (City 제거)
    private fun normalizeJapaneseLocalName(raw: String?): String? {
        if (raw.isNullOrBlank()) return raw
        var s: String = raw!!  // ← non-null 보장

        // 세부 주소 단위 제거
        val junkPatterns = listOf(
            Regex("\\d+[- ]?chome", RegexOption.IGNORE_CASE),
            Regex("丁目"),
            Regex("番地?"),
            Regex("号"),
            Regex("字"),
            Regex("通り"),
            Regex("-dori", RegexOption.IGNORE_CASE)
        )
        for (p in junkPatterns) {          // ← forEach(lambda) 대신 for-loop
            s = s.replace(p, "")
        }

        // 접미어 제거
        val suffixes = listOf(
            "(?:-|\\s)?ku\\b", "(?:-|\\s)?shi\\b", "(?:-|\\s)?cho\\b", "(?:-|\\s)?chō\\b",
            "(?:-|\\s)?machi\\b", "(?:-|\\s)?mura\\b", "(?:-|\\s)?son\\b", "(?:-|\\s)?gun\\b"
        )
        for (suf in suffixes) {            // ← 여기도 for-loop
            s = s.replace(Regex("(?i)$suf"), "")
        }

        s = s.replace(Regex("\\s{2,}"), " ").trim()
        return if (s.isNotBlank()) s else raw.trim()
    }


    // 한국 등 기타 지역 이름 단순 정리용 (동/리 → dong/ri)
    private fun normalizeKoreanLocalName(raw: String?): String? {
        if (raw.isNullOrBlank()) return raw
        return raw
            .replace(Regex("(?i)-tong$"), "-dong")
            .replace(Regex("(?i)-ri$"), "ri")
            .trim()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION &&
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            val weatherTitle = findViewById<TextView>(R.id.txtWeatherTitle)
            val weatherMain = findViewById<TextView>(R.id.txtWeatherMain)
            val weatherIcon = findViewById<ImageView>(R.id.iconWeather)
            getUserLocation(weatherTitle, weatherMain, weatherIcon)
        }
    }
}
