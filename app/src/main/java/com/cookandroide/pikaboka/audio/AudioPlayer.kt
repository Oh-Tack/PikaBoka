package com.cookandroide.pikaboka.audio

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import java.io.File
import java.security.MessageDigest

object AudioPlayer {
    private var player: ExoPlayer? = null

    fun playBase64Wav(context: Context, base64: String, cacheKey: String? = null) {
        val file = cacheKey?.let { cacheFile(context, it) } ?: File.createTempFile("tts_", ".wav", context.cacheDir)
        if (!file.exists() || file.length() == 0L) {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            file.outputStream().use { it.write(bytes) }
        }
        if (player == null) player = ExoPlayer.Builder(context).build()
        player!!.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        player!!.prepare()
        player!!.play()
    }

    fun pause() = player?.pause()
    fun release() { player?.release(); player = null }

    private fun cacheFile(context: Context, key: String): File {
        val md5 = MessageDigest.getInstance("MD5").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(context.cacheDir, "tts_$md5.wav")
    }
}
