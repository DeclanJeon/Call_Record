/*
 * Call_Record: FOSS call recording for Android, based on ShizuCallRecorder.
 *  Copyright (C) 2026-present kitsumed (Med)   (original ShizuCallRecorder)
 *  Copyright (C) 2026 Call_Record contributors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.declan.callrecord.verify

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * A detected region of the recording that contains speech-like energy.
 *
 * @param startMs Start of the region in milliseconds from the beginning of the file.
 * @param endMs End of the region in milliseconds from the beginning of the file.
 */
data class VoiceSegment(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

/**
 * Overall verdict of the analysis, used for the plain-language summary of the report.
 */
enum class AudioVerdict {
    /** Speech-like energy was found, the recording most likely contains the conversation. */
    VOICE_DETECTED,

    /** Some audio was captured but no speech-like energy was detected. */
    AUDIO_BUT_NO_SPEECH,

    /** The file is empty or contains only silence. */
    SILENT_OR_EMPTY,

    /** The recording is too short to judge. */
    TOO_SHORT
}

/**
 * Result of the audio analysis of a recording.
 */
data class AudioAnalysis(
    val durationMs: Long = 0L,
    val overallRmsDb: Double = -120.0,
    val peakDb: Double = -120.0,
    val noiseFloorDb: Double = -120.0,
    val voiceSegments: List<VoiceSegment> = emptyList(),
    val voiceDurationMs: Long = 0L,
    val longestSilenceMs: Long = 0L,
    val verdict: AudioVerdict = AudioVerdict.TOO_SHORT
) {
    /** Percentage of the recording that contains speech-like energy. */
    val voiceRatioPercent: Double
        get() = if (durationMs <= 0) 0.0 else voiceDurationMs * 100.0 / durationMs
}

/**
 * Energy based voice activity detection on a decoded PCM stream.
 *
 * The detector works on 20 ms frames, marks frames above [VOICE_THRESHOLD_DB] as speech and
 * applies a hangover so that short pauses inside a sentence do not split a segment.
 */
object AudioAnalyzer {

    /** Frame size used for the analysis. */
    const val FRAME_MS = 20

    /** Energy threshold that marks a frame as speech, in dBFS. */
    const val VOICE_THRESHOLD_DB = -45.0

    /** A silent gap shorter than this does not end the current speech segment. */
    const val HANGOVER_MS = 300L

    /** Speech segments shorter than this are discarded as noise. */
    const val MIN_SEGMENT_MS = 200L

    /**
     * Analyzes the given mono PCM stream.
     */
    fun analyze(pcm: PcmAudio): AudioAnalysis {
        val sampleRate = pcm.sampleRate
        if (sampleRate <= 0 || pcm.samples.isEmpty()) {
            return AudioAnalysis(verdict = AudioVerdict.SILENT_OR_EMPTY)
        }

        val frameSize = (sampleRate * FRAME_MS / 1000).coerceAtLeast(1)
        val frameCount = pcm.samples.size / frameSize
        if (frameCount == 0) {
            return AudioAnalysis(durationMs = pcm.durationMs, verdict = AudioVerdict.TOO_SHORT)
        }

        val frameDb = DoubleArray(frameCount)
        var sumSquares = 0.0
        var peak = 0
        for (frame in 0 until frameCount) {
            val start = frame * frameSize
            var frameSquares = 0.0
            for (i in start until start + frameSize) {
                val value = pcm.samples[i].toInt()
                frameSquares += value.toDouble() * value.toDouble()
                val magnitude = abs(value)
                if (magnitude > peak) peak = magnitude
            }
            sumSquares += frameSquares
            val rms = sqrt(frameSquares / frameSize)
            frameDb[frame] = toDb(rms)
        }

        val overallRms = sqrt(sumSquares / (frameCount.toDouble() * frameSize))
        val noiseFloor = percentile(frameDb, 0.10)

        // Mark speech frames, then apply the hangover.
        val segments = ArrayList<VoiceSegment>()
        var segmentStart = -1
        var lastVoiceFrame = -1
        for (frame in 0 until frameCount) {
            if (frameDb[frame] >= VOICE_THRESHOLD_DB) {
                if (segmentStart < 0) segmentStart = frame
                lastVoiceFrame = frame
            } else if (segmentStart >= 0) {
                val silenceMs = (frame - lastVoiceFrame) * FRAME_MS
                if (silenceMs >= HANGOVER_MS) {
                    segments.add(segmentOf(segmentStart, lastVoiceFrame, sampleRate))
                    segmentStart = -1
                }
            }
        }
        if (segmentStart >= 0) {
            segments.add(segmentOf(segmentStart, lastVoiceFrame, sampleRate))
        }

        val kept = segments.filter { it.durationMs >= MIN_SEGMENT_MS }
        val voiceDuration = kept.sumOf { it.durationMs }
        val longestSilence = longestSilence(kept, pcm.durationMs)

        val verdict = when {
            pcm.durationMs < 1_000L -> AudioVerdict.TOO_SHORT
            voiceDuration >= 500L -> AudioVerdict.VOICE_DETECTED
            toDb(peak.toDouble()) > -25.0 -> AudioVerdict.AUDIO_BUT_NO_SPEECH
            else -> AudioVerdict.SILENT_OR_EMPTY
        }

        return AudioAnalysis(
            durationMs = pcm.durationMs,
            overallRmsDb = toDb(overallRms),
            peakDb = toDb(peak.toDouble()),
            noiseFloorDb = noiseFloor,
            voiceSegments = kept,
            voiceDurationMs = voiceDuration,
            longestSilenceMs = longestSilence,
            verdict = verdict
        )
    }

    private fun segmentOf(startFrame: Int, endFrame: Int, sampleRate: Int): VoiceSegment {
        return VoiceSegment(
            startMs = startFrame.toLong() * FRAME_MS,
            endMs = (endFrame + 1).toLong() * FRAME_MS
        )
    }

    private fun longestSilence(segments: List<VoiceSegment>, totalMs: Long): Long {
        var longest = 0L
        var cursor = 0L
        for (segment in segments) {
            longest = max(longest, segment.startMs - cursor)
            cursor = max(cursor, segment.endMs)
        }
        longest = max(longest, totalMs - cursor)
        return longest
    }

    private fun toDb(amplitude: Double): Double {
        if (amplitude <= 0.0) return -120.0
        return 20.0 * log10(amplitude / 32768.0)
    }

    private fun percentile(values: DoubleArray, fraction: Double): Double {
        if (values.isEmpty()) return -120.0
        val sorted = values.clone()
        sorted.sort()
        val index = ((sorted.size - 1) * fraction).toInt().coerceIn(0, sorted.size - 1)
        return sorted[index]
    }
}
