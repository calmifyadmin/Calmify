package com.lifo.meditation

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Generates and plays a meditation bell tone programmatically.
 *
 * Uses a 528 Hz sine wave with exponential decay (≈2.5 s ring-out) rendered
 * via AudioTrack. No audio asset required.
 */
object MeditationBellPlayer {

    private const val SAMPLE_RATE = 44_100
    private const val FREQUENCY_HZ = 528.0   // solfeggio "Mi" tone
    private const val DURATION_S = 2.5
    private const val DECAY = 2.2            // higher = shorter ring
    private const val AMPLITUDE = 0.6f

    suspend fun play() = withContext(Dispatchers.IO) {
        val numSamples = (SAMPLE_RATE * DURATION_S).toInt()
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = exp(-DECAY * t).toFloat()
            val sample = (AMPLITUDE * envelope * sin(2.0 * PI * FREQUENCY_HZ * t)).toFloat()
            buffer[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }

        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, buffer.size * 2))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        try {
            track.write(buffer, 0, buffer.size)
            track.play()
            // Wait for playback to finish before releasing
            val durationMs = (DURATION_S * 1000).toLong()
            kotlinx.coroutines.delay(durationMs + 200L)
        } finally {
            track.stop()
            track.release()
        }
    }
}
