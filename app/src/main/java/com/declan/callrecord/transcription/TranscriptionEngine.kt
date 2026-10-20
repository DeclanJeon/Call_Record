/*
 * Call_Record: FOSS call recording for Android, based on ShizuCallRecorder.
 *  Copyright (C) 2026-present kitsumed (Med)   (original ShizuCallRecorder)
 *  Copyright (C) 2026 Call_Record contributors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.declan.callrecord.transcription

import com.declan.callrecord.verify.PcmAudio
import com.declan.callrecord.verify.Transcript

/**
 * A speech to text engine.
 */
interface TranscriptionEngine {

    /** Human readable name of the engine, e.g. "Vosk (한국어 소형 모델)". */
    val displayName: String

    /** True when the engine has everything it needs to run (model present and loaded). */
    val isReady: Boolean

    /**
     * Transcribes the given audio.
     *
     * @param pcm Mono 16 kHz PCM samples of the recording.
     * @param language Language hint, e.g. "ko".
     * @param onProgress Progress callback, receives a percentage between 0 and 100.
     * @return the transcript, or null when the transcription failed.
     */
    suspend fun transcribe(
        pcm: PcmAudio,
        language: String = "ko",
        onProgress: (Int) -> Unit = {}
    ): Transcript?
}
