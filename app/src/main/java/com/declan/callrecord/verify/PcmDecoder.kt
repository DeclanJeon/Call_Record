/*
 * Call_Record: FOSS call recording for Android, based on ShizuCallRecorder.
 *  Copyright (C) 2026-present kitsumed (Med)   (original ShizuCallRecorder)
 *  Copyright (C) 2026 Call_Record contributors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.declan.callrecord.verify

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A decoded mono PCM stream at a fixed sample rate, kept in memory.
 *
 * @param samples Interleaved-free mono samples, 16 bit signed.
 * @param sampleRate Sample rate of [samples].
 */
data class PcmAudio(
    val samples: ShortArray,
    val sampleRate: Int,
    /** Sample rate of the original file, 0 when unknown. */
    val sourceSampleRate: Int = 0,
    /** Channel count of the original file, 0 when unknown. */
    val sourceChannels: Int = 0
) {
    val durationMs: Long
        get() = if (sampleRate <= 0) 0L else samples.size * 1000L / sampleRate

    /** Writes the stream as a RIFF/WAVE PCM file. */
    fun writeWav(target: File) {
        val byteCount = samples.size * 2
        RandomAccessFile(target, "rw").use { raf ->
            raf.setLength(0)
            raf.writeBytes("RIFF")
            raf.write(intLe(36 + byteCount))
            raf.writeBytes("WAVE")
            raf.writeBytes("fmt ")
            raf.write(intLe(16))              // PCM chunk size
            raf.write(shortLe(1))             // format = PCM
            raf.write(shortLe(1))             // channels = mono
            raf.write(intLe(sampleRate))
            raf.write(intLe(sampleRate * 2))  // byte rate
            raf.write(shortLe(2))             // block align
            raf.write(shortLe(16))            // bits per sample
            raf.writeBytes("data")
            raf.write(intLe(byteCount))
            val buffer = ByteBuffer.allocate(byteCount).order(ByteOrder.LITTLE_ENDIAN)
            for (s in samples) buffer.putShort(s)
            raf.write(buffer.array())
        }
    }

    private fun intLe(value: Int) = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte()
    )

    private fun shortLe(value: Int) = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte()
    )
}

/**
 * Decodes an audio container into mono PCM using the platform decoders (MediaExtractor + MediaCodec).
 *
 * No native code is involved, which keeps the APK small and works on every device with the
 * standard decoders (Opus/AAC are part of AOSP since Android 9/10).
 */
object PcmDecoder {

    /**
     * Decodes [uri] to mono 16 kHz PCM.
     *
     * @return the decoded audio, or null when the file cannot be decoded.
     */
    fun decode(context: Context, uri: Uri, targetSampleRate: Int = 16000): PcmAudio? {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        return try {
            extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)

            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = trackFormat
                    break
                }
            }
            if (trackIndex < 0 || format == null) {
                null
            } else {
                extractor.selectTrack(trackIndex)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
                codec = MediaCodec.createDecoderByType(mime)
                codec.configure(format, null, null, 0)
                codec.start()

                val chunks = ArrayList<ShortArray>()
                var totalSamples = 0L
                var inputDone = false
                var outputDone = false
                var sourceSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                var sourceChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

                val info = MediaCodec.BufferInfo()
                while (!outputDone) {
                    if (!inputDone) {
                        val inputIndex = codec.dequeueInputBuffer(10_000)
                        if (inputIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputIndex)
                            val sampleSize = if (inputBuffer != null) extractor.readSampleData(inputBuffer, 0) else -1
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outputIndex = codec.dequeueOutputBuffer(info, 10_000)
                    when {
                        outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val outputFormat = codec.outputFormat
                            sourceSampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            sourceChannels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }

                        outputIndex >= 0 -> {
                            val outputBuffer = codec.getOutputBuffer(outputIndex)
                            if (outputBuffer != null && info.size > 0) {
                                outputBuffer.position(info.offset)
                                outputBuffer.limit(info.offset + info.size)
                                val shortCount = info.size / 2
                                val shorts = ShortArray(shortCount)
                                for (i in 0 until shortCount) {
                                    shorts[i] = outputBuffer.getShort()
                                }
                                val mono = if (sourceChannels >= 2) downmixToMono(shorts, sourceChannels) else shorts
                                chunks.add(mono)
                                totalSamples += mono.size
                            }
                            codec.releaseOutputBuffer(outputIndex, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                outputDone = true
                            }
                        }
                    }
                }

                if (totalSamples == 0L) {
                    null
                } else {
                    val merged = ShortArray(totalSamples.toInt())
                    var offset = 0
                    for (chunk in chunks) {
                        System.arraycopy(chunk, 0, merged, offset, chunk.size)
                        offset += chunk.size
                    }
                    PcmAudio(
                        samples = resample(merged, sourceSampleRate, targetSampleRate),
                        sampleRate = targetSampleRate,
                        sourceSampleRate = sourceSampleRate,
                        sourceChannels = sourceChannels
                    )
                }
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor?.release() }
        }
    }

    /** Averages all channels of an interleaved buffer into a single mono channel. */
    private fun downmixToMono(interleaved: ShortArray, channels: Int): ShortArray {
        if (channels <= 1) return interleaved
        val frames = interleaved.size / channels
        val mono = ShortArray(frames)
        for (frame in 0 until frames) {
            var sum = 0
            for (channel in 0 until channels) {
                sum += interleaved[frame * channels + channel]
            }
            mono[frame] = (sum / channels).toShort()
        }
        return mono
    }

    /** Linear resampling to the target sample rate. */
    private fun resample(samples: ShortArray, sourceRate: Int, targetRate: Int): ShortArray {
        if (sourceRate <= 0 || targetRate <= 0 || sourceRate == targetRate) return samples
        val ratio = sourceRate.toDouble() / targetRate.toDouble()
        val outLength = (samples.size / ratio).toInt().coerceAtLeast(1)
        val out = ShortArray(outLength)
        for (i in 0 until outLength) {
            val position = i * ratio
            val index = position.toInt()
            val fraction = (position - index).toFloat()
            val a = samples.getOrElse(index) { 0 }
            val b = samples.getOrElse(index + 1) { a }
            out[i] = (a + (b - a) * fraction).toInt().toShort()
        }
        return out
    }
}
