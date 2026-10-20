/*
 * Call_Record: FOSS call recording for Android, based on ShizuCallRecorder.
 *  Copyright (C) 2026-present kitsumed (Med)   (original ShizuCallRecorder)
 *  Copyright (C) 2026 Call_Record contributors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.declan.callrecord.verify

/**
 * The result of a transcription.
 *
 * @param text The full transcript, one utterance per line.
 * @param segments Time coded utterances.
 */
data class Transcript(
    val text: String = "",
    val segments: List<TranscriptSegment> = emptyList()
) {
    /** True when the transcript does not contain any usable text. */
    val isEmpty: Boolean get() = text.isBlank()
}

/**
 * A single time coded utterance of a transcript.
 *
 * @param startMs Start of the utterance in milliseconds.
 * @param endMs End of the utterance in milliseconds.
 * @param text The spoken text.
 */
data class TranscriptSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String
) {
    /** Formats the segment as an SRT subtitle entry. */
    fun toSrt(index: Int): String {
        return "${index + 1}\n${formatSrtTime(startMs)} --> ${formatSrtTime(endMs)}\n$text\n"
    }

    companion object {
        private fun formatSrtTime(ms: Long): String {
            val totalMs = ms.coerceAtLeast(0)
            val hours = totalMs / 3_600_000
            val minutes = (totalMs % 3_600_000) / 60_000
            val seconds = (totalMs % 60_000) / 1000
            val millis = totalMs % 1000
            return "%02d:%02d:%02d,%03d".format(hours, minutes, seconds, millis)
        }
    }
}
