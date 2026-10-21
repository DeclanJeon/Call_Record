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
import com.declan.callrecord.data.AppPreferences
import com.declan.callrecord.utils.AppLogger
import com.declan.callrecord.verify.PcmAudio
import com.declan.callrecord.verify.TranscriptProvider

/**
 * Facade around the available on-device speech to text engines.
 */
object TranscriptionService {

    /** The lightweight engine (small Korean model, fast). */
    const val ENGINE_VOSK = "vosk"

    /** The accurate engine (whisper.cpp, larger model). */
    const val ENGINE_WHISPER = "whisper"

    /**
     * The engine that the user selected, or null when it is not ready.
     */
    fun engine(context: Context): TranscriptionEngine? {
        val preferences = AppPreferences(context)
        return when (preferences.getTranscriptionEngine()) {
            ENGINE_WHISPER -> {
                val model = WhisperModelManager.modelById(preferences.getWhisperModelId())
                WhisperModelManager.modelFile(context, model)?.let { WhisperTranscriptionEngine(context, it) }
            }

            else -> {
                val modelDirectory = ModelManager.modelDirectory(context) ?: return null
                VoskTranscriptionEngine(context, modelDirectory)
            }
        }
    }

    /**
     * True when the selected engine has everything it needs to run.
     */
    fun isReady(context: Context): Boolean = engine(context) != null

    /**
     * A short description of the state of the selected engine, for the settings screen.
     */
    fun statusText(context: Context): String {
        val preferences = AppPreferences(context)
        return when (preferences.getTranscriptionEngine()) {
            ENGINE_WHISPER -> {
                val model = WhisperModelManager.modelById(preferences.getWhisperModelId())
                if (WhisperModelManager.isModelReady(context, model)) {
                    "Whisper 모델 설치됨 (${model.fileName}, ${model.formattedSize})"
                } else {
                    "Whisper 모델이 필요합니다 (${model.formattedSize})"
                }
            }

            else -> if (ModelManager.isModelReady(context)) {
                "Vosk 모델 설치됨 (${ModelManager.MODEL_ID})"
            } else {
                "음성 인식 모델이 필요합니다 (약 40MB)"
            }
        }
    }

    /**
     * Downloads the model of the selected engine if it is not present yet.
     */
    suspend fun ensureModel(context: Context, onProgress: (Int) -> Unit = {}): Boolean {
        val preferences = AppPreferences(context)
        return if (preferences.getTranscriptionEngine() == ENGINE_WHISPER) {
            val model = WhisperModelManager.modelById(preferences.getWhisperModelId())
            WhisperModelManager.ensureModel(context, model, onProgress)
        } else {
            ModelManager.ensureModel(context, onProgress)
        }
    }

    /**
     * Removes the model of the selected engine.
     */
    suspend fun deleteModel(context: Context): Boolean {
        val preferences = AppPreferences(context)
        return if (preferences.getTranscriptionEngine() == ENGINE_WHISPER) {
            val model = WhisperModelManager.modelById(preferences.getWhisperModelId())
            WhisperModelManager.deleteModel(context, model)
        } else {
            ModelManager.deleteModel(context)
        }
    }

    /**
     * Returns a provider that transcribes a recording, or null when no model is installed.
     */
    fun transcriptProvider(context: Context): TranscriptProvider? {
        val engine = engine(context) ?: return null
        return TranscriptProvider { pcm: PcmAudio, language: String, onProgress: (Int) -> Unit ->
            engine.transcribe(pcm, language, onProgress)
        }
    }
}
