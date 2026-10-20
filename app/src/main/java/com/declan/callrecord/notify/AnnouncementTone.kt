/*
 * Call_Record: FOSS call recording for Android, based on ShizuCallRecorder.
 *  Copyright (C) 2026-present kitsumed (Med)   (original ShizuCallRecorder)
 *  Copyright (C) 2026 Call_Record contributors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.declan.callrecord.notify

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import com.declan.callrecord.utils.AppLogger
import kotlin.math.PI
import kotlin.math.sin

/**
 * Plays a short announcement tone when the recording starts.
 *
 * The tone is injected into the call path with [AudioTrack] in `MODE_IN_COMMUNICATION`, which is the
 * same path voice call applications use for their own audio. That means the other party of the call
 * hears the tone, and the local user gets an audible confirmation that the recording is running.
 *
 * Legal note: announcing the recording is strongly recommended. In South Korea the
 * 통신비밀보호법 (Act on the Protection of Communications Secrecy) can apply even to a one to one
 * call when the other party did not agree to the recording.
 */
object AnnouncementTone {

    /** A single short beep. */
    const val TYPE_SHORT = "short"

    /** Two short beeps. */
    const val TYPE_DOUBLE = "double"

    /** A two tone chime. */
    const val TYPE_CHIME = "chime"

    private const val SAMPLE_RATE = 44100
    private const val AMPLITUDE = 0.45

    /**
     * Plays the announcement tone.
     *
     * @param context The context used to play the tone.
     * @param type One of [TYPE_SHORT], [TYPE_DOUBLE] or [TYPE_CHIME].
     * @param playLocally true to also play the tone on the local speaker, false for call path only.
     */
    fun play(context: Context, type: String = TYPE_SHORT, playLocally: Boolean = false) {
        val pcm = buildPcm(type) ?: return
        val played = playOnCallPath(pcm)
        if (!played) {
            AppLogger.w( "Could not play the announcement tone on the call path, falling back to the speaker" )
            playOnSpeaker(context, type)
        } else if (playLocally) {
            playOnSpeaker(context, type)
        }
    }

    /**
     * Builds the raw PCM of the requested tone.
     */
    private fun buildPcm(type: String): ShortArray? {
        val bursts = when (type) {
            TYPE_DOUBLE -> listOf(Burst(1000.0, 180), Burst(0.0, 120), Burst(1000.0, 180))
            TYPE_CHIME -> listOf(Burst(880.0, 220), Burst(0.0, 60), Burst(1174.0, 320))
            else -> listOf(Burst(1000.0, 320))
        }
        val totalSamples = bursts.sumOf { burst ->
            (SAMPLE_RATE * burst.durationMs / 1000).toInt()
        }
        val samples = ShortArray(totalSamples)
        var offset = 0
        bursts.forEach { burst ->
            val count = (SAMPLE_RATE * burst.durationMs / 1000).toInt()
            for (i in 0 until count) {
                val envelope = envelope(i, count)
                val value = if (burst.frequency <= 0.0) {
                    0.0
                } else {
                    val t = i.toDouble() / SAMPLE_RATE
                    sin(2.0 * PI * burst.frequency * t) * AMPLITUDE * envelope
                }
                samples[offset + i] = (value * Short.MAX_VALUE).toInt().toShort()
            }
            offset += count
        }
        return samples
    }

    /** Simple attack/release envelope so the tone does not click. */
    private fun envelope(index: Int, count: Int): Double {
        val fade = (SAMPLE_RATE * 0.01).toInt().coerceAtLeast(1)
        return when {
            index < fade -> index.toDouble() / fade
            index > count - fade -> (count - index).toDouble() / fade
            else -> 1.0
        }
    }

    /**
     * Injects the tone into the call audio path.
     */
    private fun playOnCallPath(pcm: ShortArray): Boolean {
        return runCatching {
            val minBuffer = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuffer <= 0) return@runCatching false
            val bufferSize = minBuffer * 2

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            val track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()

            track.write(pcm, 0, pcm.size)
            track.setNotificationMarkerPosition(pcm.size)
            track.play()
            // Wait for the tone to finish (max 2 seconds).
            val deadline = System.currentTimeMillis() + 2000
            while (track.playState == AudioTrack.PLAYSTATE_PLAYING && System.currentTimeMillis() < deadline) {
                Thread.sleep(50)
            }
            track.stop()
            track.release()
            true
        }.getOrElse {
            AppLogger.w( "Announcement tone playback failed: ${it.message}" )
            false
        }
    }

    /**
     * Fallback that plays the tone on the speaker.
     */
    private fun playOnSpeaker(context: Context, type: String) {
        runCatching {
            val generator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)
            when (type) {
                TYPE_DOUBLE -> {
                    generator.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
                    Thread.sleep(280)
                    generator.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
                    Thread.sleep(280)
                }
                TYPE_CHIME -> {
                    generator.startTone(ToneGenerator.TONE_PROP_BEEP2, 320)
                    Thread.sleep(400)
                }
                else -> {
                    generator.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
                    Thread.sleep(380)
                }
            }
            generator.release()
        }.onFailure {
            AppLogger.w( "Speaker announcement tone failed: ${it.message}" )
        }
    }

    private data class Burst(val frequency: Double, val durationMs: Int)
}
