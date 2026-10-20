/*
 * Call_Record: FOSS call recording for Android, based on ShizuCallRecorder.
 *  Copyright (C) 2026-present kitsumed (Med)   (original ShizuCallRecorder)
 *  Copyright (C) 2026 Call_Record contributors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.declan.callrecord.transcription

import android.content.Context
import com.declan.callrecord.utils.AppLogger
import com.declan.callrecord.verify.PcmAudio
import com.declan.callrecord.verify.Transcript
import com.declan.callrecord.verify.TranscriptSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.ByteArrayOutputStream

/**
 * On-device speech to text engine based on [Vosk](https://alphacephei.com/vosk/).
 *
 * Everything runs on the phone, no audio ever leaves the device.
 */
class VoskTranscriptionEngine(
    private val context: Context,
    private val modelDirectory: java.io.File
) : TranscriptionEngine {

    override val displayName: String = "Vosk (한국어 소형 모델, ${ModelManager.MODEL_ID})"

    @Volatile
    private var model: Model? = null

    override val isReady: Boolean
        get() = model != null || modelDirectory.exists()

    /**
     * Loads the model into memory. Safe to call multiple times.
     */
    suspend fun loadModel(): Boolean = withContext(Dispatchers.IO) {
        if (model != null) return@withContext true
        runCatching {
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            model = Model(modelDirectory.absolutePath)
        }.onFailure {
            AppLogger.e( "Could not load the Vosk model: ${it.message}", it)
        }.isSuccess
    }

    override suspend fun transcribe(
        pcm: PcmAudio,
        language: String,
        onProgress: (Int) -> Unit
    ): Transcript? = withContext(Dispatchers.IO) {
        if (pcm.sampleRate != 16000) {
            AppLogger.w( "Vosk expects 16 kHz audio, got ${pcm.sampleRate}")
            return@withContext null
        }
        if (!loadModel()) return@withContext null
        val activeModel = model ?: return@withContext null

        val segments = ArrayList<TranscriptSegment>()
        val lines = ArrayList<String>()
        val bytes = pcm.samples.toLittleEndianBytes()
        val chunkSize = 4096
        var offset = 0

        runCatching {
            Recognizer(activeModel, 16000.0f).use { recognizer ->
                recognizer.setWords(true)
                while (offset < bytes.size) {
                    val length = minOf(chunkSize, bytes.size - offset)
                    val chunk = bytes.copyOfRange(offset, offset + length)
                    offset += length
                    if (recognizer.acceptWaveForm(chunk, chunk.size)) {
                        val result = recognizer.result
                        val segment = parseResult(result)
                        if (segment != null) {
                            segments.add(segment)
                            lines.add(segment.text)
                        }
                    }
                    onProgress(((offset * 100) / bytes.size).coerceIn(0, 99))
                }
                val finalSegment = parseResult(recognizer.finalResult)
                if (finalSegment != null) {
                    segments.add(finalSegment)
                    lines.add(finalSegment.text)
                }
            }
        }.onFailure {
            AppLogger.e( "Transcription failed: ${it.message}", it)
        }

        onProgress(100)
        if (lines.isEmpty()) {
            Transcript(text = "", segments = emptyList())
        } else {
            Transcript(text = lines.joinToString("\n"), segments = segments)
        }
    }

    /**
     * Parses a Vosk result JSON document into a transcript segment.
     */
    private fun parseResult(raw: String): TranscriptSegment? {
        if (raw.isBlank()) return null
        return runCatching {
            val json = JSONObject(raw)
            val text = json.optString("text", "").trim()
            val words = json.optJSONArray("result")
            if (text.isEmpty() && words == null) {
                null
            } else {
                var startMs = 0L
                var endMs = 0L
                if (words != null && words.length() > 0) {
                    val first = words.optJSONObject(0)
                    val last = words.optJSONObject(words.length() - 1)
                    startMs = ((first?.optDouble("start", 0.0) ?: 0.0) * 1000.0).toLong()
                    endMs = ((last?.optDouble("end", 0.0) ?: 0.0) * 1000.0).toLong()
                }
                TranscriptSegment(startMs = startMs.toLong(), endMs = endMs.toLong(), text = text)
            }
        }.getOrNull()
    }
}

/**
 * Converts 16 bit PCM samples into a little endian byte array.
 */
private fun ShortArray.toLittleEndianBytes(): ByteArray {
    val output = ByteArrayOutputStream(size * 2)
    for (sample in this) {
        val value = sample.toInt()
        output.write(value and 0xFF)
        output.write((value shr 8) and 0xFF)
    }
    return output.toByteArray()
}
