package com.gmini.android.voice

import java.io.ByteArrayOutputStream

/** Empaqueta PCM16 crudo en un contenedor WAV (cabecera canonica de 44 bytes). */
object WavUtils {

    const val SAMPLE_RATE = 16_000

    fun pcm16ToWav(pcm: ByteArray, sampleRate: Int = SAMPLE_RATE, channels: Int = 1): ByteArray {
        val byteRate = sampleRate * channels * 2
        val blockAlign = channels * 2
        val dataLength = pcm.size
        val out = ByteArrayOutputStream(44 + dataLength)

        fun ascii(value: String) = out.write(value.toByteArray(Charsets.US_ASCII))
        fun intLE(value: Int) {
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
            out.write((value shr 16) and 0xFF)
            out.write((value shr 24) and 0xFF)
        }
        fun shortLE(value: Int) {
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
        }

        ascii("RIFF")
        intLE(36 + dataLength)
        ascii("WAVE")
        ascii("fmt ")
        intLE(16)          // tamano del bloque fmt
        shortLE(1)         // PCM
        shortLE(channels)
        intLE(sampleRate)
        intLE(byteRate)
        shortLE(blockAlign)
        shortLE(16)        // bits por muestra
        ascii("data")
        intLE(dataLength)
        out.write(pcm)
        return out.toByteArray()
    }
}
