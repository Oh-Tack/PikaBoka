package com.cookandroide.pikaboka.net

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit

data class TtsRequest(val text: String, val speaker: Int = 47)
data class TtsResponse(val audioBase64: String, val mime: String)

interface TtsApi {
    @POST("/tts-basic")
    suspend fun ttsBasic(@Body req: TtsRequest): TtsResponse

    companion object {
        fun create(baseUrl: String): TtsApi {
            val log = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(log)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            // Moshi + Kotlin 어댑터
            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()

            return Retrofit.Builder()
                .baseUrl(
                    if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
                )
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .client(client)
                .build()
                .create(TtsApi::class.java)
        }
    }
}
