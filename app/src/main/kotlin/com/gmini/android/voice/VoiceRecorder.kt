package com.gmini.android.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Graba audio del microfono a 16 kHz mono PCM16 y lo entrega como WAV, el
 * formato que espera `POST /api/v1/voice/turn`. El permiso RECORD_AUDIO debe
 * concederse antes de llamar a [start].
 */
class VoiceRecorder {

    private var record: AudioRecord? = null
    private var worker: Thread? = null

    @Volatile
    private var recording = false

    private val pcm = ByteArrayOutputStream()

    val isRecording: Boolean get() = recording

    @SuppressLint("MissingPermission")
    fun start() {
        if (recording) return
        val minBuffer = AudioRecord.getMinBufferSize(WavUtils.SAMPLE_RATE, CHANNEL, ENCODING)
        val bufferSize = max(minBuffer, WavUtils.SAMPLE_RATE * 2)
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            WavUtils.SAMPLE_RATE,
            CHANNEL,
            ENCODING,
            bufferSize,
        )
        synchronized(pcm) { pcm.reset() }
        record = audioRecord
        audioRecord.startRecording()
        recording = true
        worker = Thread {
            val chunk = ByteArray(bufferSize)
            while (recording) {
                val read = audioRecord.read(chunk, 0, chunk.size)
                if (read > 0) {
                    synchronized(pcm) { pcm.write(chunk, 0, read) }
                }
            }
        }.also { it.start() }
    }

    /** Detiene la grabacion y devuelve el audio en WAV. */
    fun stop(): ByteArray {
        if (!recording) return ByteArray(0)
        recording = false
        worker?.join(500)
        worker = null
        record?.runCatching { stop() }
        record?.release()
        record = null
        val data = synchronized(pcm) { pcm.toByteArray() }
        return WavUtils.pcm16ToWav(data)
    }

    fun cancel() {
        recording = false
        worker?.join(200)
        worker = null
        record?.runCatching { stop() }
        record?.release()
        record = null
        synchronized(pcm) { pcm.reset() }
    }

    private companion object {
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }
}
