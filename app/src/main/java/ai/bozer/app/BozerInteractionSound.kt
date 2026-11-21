package ai.bozer.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * Tiny original UI chimes synthesized locally: no downloaded audio, no music,
 * and no sound is played unless the user leaves UI sound effects enabled.
 */
object BozerInteractionSound {
    enum class Event { OPEN, CLOSE }

    fun play(event: Event) {
        Thread({
            var track: AudioTrack? = null
            try {
                val sampleRate = 24_000
                val samples = synthesize(event, sampleRate)
                val created = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(samples.size * Float.SIZE_BYTES)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track = created
                val written = created.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                if (written != samples.size) {
                    created.release()
                    return@Thread
                }
                created.setNotificationMarkerPosition(samples.size)
                created.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(audioTrack: AudioTrack) {
                        runCatching { audioTrack.stop() }
                        audioTrack.release()
                    }
                    override fun onPeriodicNotification(audioTrack: AudioTrack) = Unit
                })
                created.play()
            } catch (_: Exception) {
                runCatching { track?.release() }
            }
        }, "bozer-ui-chime").apply { isDaemon = true }.start()
    }

    private fun synthesize(event: Event, sampleRate: Int): FloatArray {
        val notes = when (event) {
            Event.OPEN -> listOf(660.0 to 0.075, 880.0 to 0.095)
            Event.CLOSE -> listOf(880.0 to 0.065, 587.0 to 0.085)
        }
        val silenceSamples = (0.012 * sampleRate).toInt()
        val total = notes.sumOf { (frequency, duration) ->
            (duration * sampleRate).toInt()
        } + silenceSamples
        val output = FloatArray(total)
        var cursor = 0
        notes.forEachIndexed { noteIndex, (frequency, duration) ->
            val count = (duration * sampleRate).toInt()
            val attack = (0.007 * sampleRate).toInt().coerceAtLeast(1)
            val release = (0.028 * sampleRate).toInt().coerceAtLeast(1)
            for (index in 0 until count) {
                val fadeIn = (index.toFloat() / attack).coerceIn(0f, 1f)
                val fadeOut = ((count - index).toFloat() / release).coerceIn(0f, 1f)
                val envelope = minOf(fadeIn, fadeOut)
                val phase = 2.0 * PI * frequency * index / sampleRate
                val fundamental = sin(phase)
                val harmonic = sin(phase * 2.0) * 0.18
                val noteGain = if (noteIndex == 0) 0.045 else 0.038
                output[cursor + index] = ((fundamental + harmonic) * envelope * noteGain).toFloat()
            }
            cursor += count + silenceSamples
        }
        return output
    }
}
