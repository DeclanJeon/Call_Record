/*
 * Call_Record: FOSS call recording for Android, based on ShizuCallRecorder.
 *  Copyright (C) 2026-present kitsumed (Med)   (original ShizuCallRecorder)
 *  Copyright (C) 2026 Call_Record contributors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.declan.callrecord.transcription

/**
 * JNI bridge to the whisper.cpp speech recognition engine.
 *
 * The native library is built from the whisper.cpp sources during the Gradle build
 * (see `app/src/main/cpp/CMakeLists.txt`).
 */
object WhisperBridge {

    init {
        System.loadLibrary("callrecord_whisper")
    }

    /**
     * Transcribes the given samples with the model at [modelPath].
     *
     * @param modelPath Absolute path of the ggml model file.
     * @param samples 16 kHz mono samples in the range [-1, 1].
     * @param language Language code, e.g. "ko".
     * @param numThreads Number of threads to use.
     * @return A JSON document with the transcript, or null when the model failed to load.
     */
    external fun nativeTranscribe(
        modelPath: String,
        samples: FloatArray,
        language: String,
        numThreads: Int
    ): String?

    /** Frees every model that is currently loaded in the native layer. */
    external fun nativeReleaseModels()
}
