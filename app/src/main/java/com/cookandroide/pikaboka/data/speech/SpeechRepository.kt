package com.cookandroide.pikaboka.data.speech

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicReference

object SpeechRepository {
    private val cache = AtomicReference<List<SpeechSentence>?>(null)

    fun loadAll(context: Context): List<SpeechSentence> {
        // 초기 캐시 사용 (앱 재실행 시 재로딩)
        cache.get()?.let { return it }

        val out = mutableListOf<SpeechSentence>()

        try {
            context.assets.open("conversation.csv").use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { br ->
                    val headerLine = br.readLine() ?: return emptyList()
                    val headers = parseCsvLine(headerLine).map { it.trim() }
                    val headersLower = headers.map { it.lowercase() }

                    // 명시적 헤더 매핑 (우선순위 세트)
                    val idxJa   = indexOfAny(headersLower, listOf("textja","ja","japanese","jp","original","sentence","日本語","原文"))
                    val idxPron = indexOfAny(headersLower, listOf("pronunciation","pron","kana","furigana","reading","yomi","romaji","よみ","読み","ふりがな"))
                    val idxTr   = indexOfAny(headersLower, listOf("translation","ko","kor","korean","kr","번역","en","eng","english"))
                    val idxCat  = indexOfAny(headersLower, listOf("category","cat","topic","분류","카테고리"))

                    var line: String?
                    while (br.readLine().also { line = it } != null) {
                        val raw = line!!.trim()
                        if (raw.isEmpty()) continue

                        val cols = parseCsvLine(raw)
                        fun col(i: Int) = if (i in cols.indices) cols[i].trim() else ""

                        val ja  = col(idxJa)
                        val pr  = col(idxPron)
                        val tr  = col(idxTr)
                        val cat = col(idxCat)

                        if (ja.isEmpty()) continue  // 원문 없으면 스킵
                        out += SpeechSentence(
                            category = cat,
                            textJa = ja,
                            pronunciation = pr,
                            translation = tr
                        )
                    }
                }
            }
        } catch (_: Throwable) {
            // 에러시 out은 비어있을 수 있음
        }

        cache.set(out)
        return out
    }

    fun getCategories(ctx: Context): List<Pair<String, Int>> =
        loadAll(ctx).groupBy { it.category.trim() }
            .map { it.key to it.value.size }
            .sortedBy { it.first.lowercase() }

    fun getByCategory(ctx: Context, category: String): List<SpeechSentence> =
        loadAll(ctx).filter { it.category.trim().equals(category.trim(), ignoreCase = true) }

    private fun indexOfAny(headersLower: List<String>, keys: List<String>): Int {
        for (k in keys) {
            val i = headersLower.indexOf(k)
            if (i >= 0) return i
        }
        // 못 찾으면 0으로 fallback (항상 존재 보장 X → col()에서 빈문자 처리)
        return 0
    }

    // CSV 1줄 파서 (따옴표/콤마 안전)
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
                ',' -> if (inQuotes) sb.append(c) else { out += sb.toString(); sb.setLength(0) }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }
}
