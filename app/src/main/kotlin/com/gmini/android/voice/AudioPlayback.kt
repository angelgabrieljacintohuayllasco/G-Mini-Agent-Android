package com.gmini.android.voice

import android.content.Context
import android.media.MediaPlayer
import java.io.File

/** Reproduce audio WAV que devuelve el servidor (TTS o turno de voz). */
class AudioPlayback(private val context: Context) {

    private var player: MediaPlayer? = null

    fun play(wav: ByteArray) {
        stop()
        if (wav.isEmpty()) return
        val file = File(context.cacheDir, "gmini_voice_${System.currentTimeMillis()}.wav")
        runCatching {
            file.writeBytes(wav)
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    it.release()
                    file.delete()
                    if (player === it) player = null
                }
                prepare()
                start()
            }
        }.onFailure { file.delete() }
    }

    fun stop() {
        player?.let { active ->
            runCatching { active.stop() }
            active.release()
        }
        player = null
    }
}
