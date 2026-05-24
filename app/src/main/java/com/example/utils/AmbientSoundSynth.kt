package com.example.utils

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sin

object AmbientSoundSynth {
    private const val SAMPLE_RATE = 44100

    /**
     * Synthesizes and plays a beautiful futuristic ascending crystal chime.
     */
    suspend fun playCrystalChime() = withContext(Dispatchers.Default) {
        try {
            val duration = 0.5 // seconds
            val numSamples = (duration * SAMPLE_RATE).toInt()
            val sample = DoubleArray(numSamples)
            val buffer = ShortArray(numSamples)

            // Ascending frequency sweeps (sine waves overlay)
            for (i in 0 until numSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                // Sweep from 520Hz (C5) up to 1040Hz (C6) and overlay another tone at 1.5x frequency
                val progress = t / duration
                val freq = 520.0 + (520.0 * progress)
                
                // Exponential fade-in and soft exponential fade-out
                val envelope = if (progress < 0.15) {
                    progress / 0.15
                } else {
                    Math.exp(-4.5 * (progress - 0.15))
                }
                
                sample[i] = (sin(2.0 * Math.PI * freq * t) + 0.4 * sin(2.0 * Math.PI * (freq * 1.5) * t)) * envelope
                buffer[i] = (sample[i] * 32767.0).toInt().toShort()
            }

            val audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                buffer.size * 2,
                AudioTrack.MODE_STATIC
            )
            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()
            // Cleanup static track after playing
            Thread.sleep((duration * 1000).toLong() + 100)
            audioTrack.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Synthesizes a soft futuristic sound blip.
     */
    suspend fun playSoftBlip() = withContext(Dispatchers.Default) {
        try {
            val duration = 0.12 // seconds
            val numSamples = (duration * SAMPLE_RATE).toInt()
            val sample = DoubleArray(numSamples)
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val progress = t / duration
                val freq = 880.0 // A5 pitch
                val envelope = Math.exp(-6.0 * progress) // rapid decay

                sample[i] = sin(2.0 * Math.PI * freq * t) * envelope
                buffer[i] = (sample[i] * 28000.0).toInt().toShort()
            }

            val audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                buffer.size * 2,
                AudioTrack.MODE_STATIC
            )
            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()
            Thread.sleep((duration * 1000).toLong() + 50)
            audioTrack.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Synthesizes a soft, deep cosmic focal hum (used during concentration mode init).
     */
    suspend fun playCosmicHum() = withContext(Dispatchers.Default) {
        try {
            val duration = 1.2f // seconds
            val numSamples = (duration * SAMPLE_RATE).toInt()
            val sample = DoubleArray(numSamples)
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val progress = t / duration
                // Deep low frequency resonance (110Hz overlaid with 55Hz sub)
                val freq1 = 110.0
                val freq2 = 55.0
                
                // Slow sinusoidal swell
                val envelope = sin(progress * Math.PI)

                sample[i] = (0.6 * sin(2.0 * Math.PI * freq1 * t) + 0.4 * sin(2.0 * Math.PI * freq2 * t)) * envelope
                buffer[i] = (sample[i] * 24000.0).toInt().toShort()
            }

            val audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                buffer.size * 2,
                AudioTrack.MODE_STATIC
            )
            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()
            Thread.sleep((duration * 1000).toLong() + 50)
            audioTrack.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
