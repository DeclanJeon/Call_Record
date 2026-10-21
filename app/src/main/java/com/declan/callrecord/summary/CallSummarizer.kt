/*
 * Call_Record: FOSS call recording for Android, based on ShizuCallRecorder.
 *  Copyright (C) 2026-present kitsumed (Med)   (original ShizuCallRecorder)
 *  Copyright (C) 2026 Call_Record contributors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.declan.callrecord.summary

import com.declan.callrecord.verify.AudioAnalysis
import com.declan.callrecord.verify.Transcript

/**
 * The summary of a call.
 *
 * @param keyPoints The most important sentences of the call, in the order they were spoken.
 * @param actionItems Sentences that contain a task or a request.
 * @param schedules Dates, times and deadlines that were mentioned.
 * @param amounts Amounts of money that were mentioned.
 * @param keywords The most frequent meaningful words.
 * @param talkTimeMs The total time that was covered by speech segments.
 * @param coverageRatio The share of the recording that was covered by speech, between 0 and 1.
 */
data class CallSummary(
    val keyPoints: List<String> = emptyList(),
    val actionItems: List<String> = emptyList(),
    val schedules: List<String> = emptyList(),
    val amounts: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val talkTimeMs: Long = 0L,
    val coverageRatio: Double = 0.0
) {
    /** True when there is nothing worth showing. */
    val isEmpty: Boolean
        get() = keyPoints.isEmpty() && actionItems.isEmpty() && schedules.isEmpty() &&
                amounts.isEmpty() && keywords.isEmpty()
}

/**
 * Creates a summary of a call.
 */
interface CallSummarizer {

    /** A short name of the summarizer, shown in the report. */
    val displayName: String

    /**
     * Summarizes the given transcript.
     */
    fun summarize(transcript: Transcript, analysis: AudioAnalysis): CallSummary
}
