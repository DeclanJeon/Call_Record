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
import com.declan.callrecord.verify.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests of the rule based call summarizer.
 */
class RuleBasedCallSummarizerTest {

    private val summarizer = RuleBasedCallSummarizer()

    private fun transcript(vararg lines: String): Transcript {
        val segments = lines.mapIndexed { index, line ->
            TranscriptSegment(
                startMs = index * 2000L,
                endMs = index * 2000L + 1800L,
                text = line
            )
        }
        return Transcript(text = lines.joinToString(" "), segments = segments)
    }

    @Test
    fun `extracts action items`() {
        val summary = summarizer.summarize(
            transcript(
                "안녕하세요, 내일 회의는 오후 3시에 할게요.",
                "자료는 오늘 안에 보내주면 됩니다.",
                "예약금 3만원은 카드 결제해 주세요."
            ),
            AudioAnalysis(durationMs = 6000)
        )
        assertTrue(
            "action items should mention the request, was: ${summary.actionItems}",
            summary.actionItems.any { it.contains("보내") || it.contains("결제") }
        )
    }

    @Test
    fun `extracts schedules and amounts`() {
        val summary = summarizer.summarize(
            transcript(
                "다음 달 5일에 오후 2시 반에 만나요.",
                "금액은 15만원입니다."
            ),
            AudioAnalysis(durationMs = 4000)
        )
        assertTrue("schedule expected, was: ${summary.schedules}", summary.schedules.any { it.contains("5일") })
        assertTrue("time expected, was: ${summary.schedules}", summary.schedules.any { it.contains("2시") })
        assertTrue("amount expected, was: ${summary.amounts}", summary.amounts.any { it.contains("만원") })
    }

    @Test
    fun `finds key points and ignores fillers`() {
        val summary = summarizer.summarize(
            transcript(
                "네.",
                "내일 오후 3시 회의로 확정했습니다.",
                "알겠습니다."
            ),
            AudioAnalysis(durationMs = 6000)
        )
        assertTrue("key points expected", summary.keyPoints.isNotEmpty())
        assertTrue(
            "the decision sentence should be a key point, was: ${summary.keyPoints}",
            summary.keyPoints.any { it.contains("확정") }
        )
    }

    @Test
    fun `counts the talk time from the segments`() {
        val summary = summarizer.summarize(
            transcript("하나", "둘", "셋"),
            AudioAnalysis(durationMs = 6000)
        )
        assertEquals(5400L, summary.talkTimeMs)
    }

    @Test
    fun `handles an empty transcript`() {
        val summary = summarizer.summarize(Transcript(), AudioAnalysis())
        assertTrue(summary.isEmpty)
    }
}
