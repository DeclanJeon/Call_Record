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
import com.declan.callrecord.verify.TranscriptProvider

/**
 * Facade around the available on-device speech to text engines.
 */
object TranscriptionService {

    /** The engine used by Call_Record. */
    fun engine(context: Context): TranscriptionEngine? {
        val modelDirectory = ModelManager.modelDirectory(context) ?: return null
        return VoskTranscriptionEngine(context, modelDirectory)
    }

    /** True when a model is installed and the engine can run. */
    fun isReady(context: Context): Boolean = ModelManager.isModelReady(context)

    /**
     * Downloads the speech recognition model if it is not present yet.
     */
    suspend fun ensureModel(context: Context, onProgress: (Int) -> Unit = {}): Boolean {
        val result = ModelManager.ensureModel(context, onProgress)
        if (result) AppLogger.i( "Speech recognition model is ready" )
        return result
    }

    /** Removes the speech recognition model. */
    suspend fun deleteModel(context: Context): Boolean = ModelManager.deleteModel(context)

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
