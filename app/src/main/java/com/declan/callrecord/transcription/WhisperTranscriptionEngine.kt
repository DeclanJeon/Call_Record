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
import java.io.File

/**
 * On-device speech to text engine based on whisper.cpp (https://github.com/ggml-org/whisper.cpp).
 *
 * Compared to the lightweight Vosk engine this gives a clearly better accuracy for Korean,
 * at the cost of a larger model and a slower transcription.
 */
class WhisperTranscriptionEngine(
    private val context: Context,
    private val modelFile: File
) : TranscriptionEngine {

    override val displayName: String = "Whisper (${modelFile.name})"

    override val isReady: Boolean get() = modelFile.exists()

    override suspend fun transcribe(
        pcm: PcmAudio,
        language: String,
        onProgress: (Int) -> Unit
    ): Transcript? = withContext(Dispatchers.IO) {
        if (pcm.sampleRate != 16000) {
            AppLogger.w( "Whisper expects 16 kHz audio, got ${pcm.sampleRate}" )
            return@withContext null
        }
        if (!modelFile.exists()) {
            AppLogger.w( "The whisper model is missing: ${modelFile.absolutePath}" )
            return@withContext null
        }

        val samples = FloatArray(pcm.samples.size) { index -> pcm.samples[index] / 32768.0f }
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)

        onProgress(5)
        val json = runCatching {
            WhisperBridge.nativeTranscribe(modelFile.absolutePath, samples, language, threads)
        }.getOrNull()

        onProgress(90)
        if (json.isNullOrBlank()) {
            AppLogger.w( "Whisper returned no result" )
            onProgress(100)
            return@withContext null
        }

        val transcript = runCatching { parse(json) }.getOrNull()
        onProgress(100)
        transcript
    }

    /**
     * Parses the JSON document returned by the native layer.
     */
    private fun parse(json: String): Transcript {
        val root = JSONObject(json)
        val text = root.optString("text", "").trim()
        val segmentsArray = root.optJSONArray("segments")
        val segments = ArrayList<TranscriptSegment>()
        if (segmentsArray != null) {
            for (index in 0 until segmentsArray.length()) {
                val item = segmentsArray.optJSONObject(index) ?: continue
                val segmentText = item.optString("text", "").trim()
                if (segmentText.isEmpty()) continue
                segments.add(
                    TranscriptSegment(
                        startMs = item.optLong("start", 0L),
                        endMs = item.optLong("end", 0L),
                        text = segmentText
                    )
                )
            }
        }
        return Transcript(text = text, segments = segments)
    }
}
