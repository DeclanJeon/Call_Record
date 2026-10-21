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
 * Rule based, on-device summarizer for Korean calls.
 *
 * It needs no model and no network: the transcript is split into sentences, scored by
 * heuristics that are typical for business calls, and grouped into key points, action items,
 * dates and amounts. The result is deterministic and instant, which makes it a good default
 * next to (or fallback for) a language model based summarizer.
 */
class RuleBasedCallSummarizer : CallSummarizer {

    override val displayName: String = "온디바이스 규칙 기반 요약"

    override fun summarize(transcript: Transcript, analysis: AudioAnalysis): CallSummary {
        val sentences = splitIntoSentences(transcript)
        if (sentences.isEmpty()) {
            return CallSummary(
                talkTimeMs = talkTime(transcript),
                coverageRatio = coverage(transcript, analysis)
            )
        }

        val scored = sentences.map { sentence -> sentence to score(sentence) }
        val keyPoints = scored
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(MAX_KEY_POINTS)
            .sortedBy { sentences.indexOf(it.first) }
            .map { it.first }

        val actionItems = sentences
            .filter { containsAny(it, ACTION_KEYWORDS) }
            .distinct()
            .take(MAX_ACTION_ITEMS)

        val schedules = extractMatches(sentences, SCHEDULE_PATTERNS)
        val amounts = extractMatches(sentences, AMOUNT_PATTERNS)
        val keywords = extractKeywords(sentences)

        return CallSummary(
            keyPoints = keyPoints,
            actionItems = actionItems,
            schedules = schedules.distinct().take(MAX_ITEMS),
            amounts = amounts.distinct().take(MAX_ITEMS),
            keywords = keywords,
            talkTimeMs = talkTime(transcript),
            coverageRatio = coverage(transcript, analysis)
        )
    }

    // ── Sentence handling ────────────────────────────────────────────────────────

    /**
     * Splits the transcript into sentences.
     *
     * When the transcript carries time coded segments (whisper) those are used, because they
     * are already split at the natural utterance boundaries. Otherwise the plain text is split
     * at the sentence terminators.
     */
    private fun splitIntoSentences(transcript: Transcript): List<String> {
        val fromSegments = transcript.segments
            .mapNotNull { it.text.trim().takeIf { text -> text.isNotEmpty() } }
        if (fromSegments.isNotEmpty()) {
            return fromSegments.flatMap { splitByPunctuation(it) }
        }
        return splitByPunctuation(transcript.text)
    }

    private fun splitByPunctuation(text: String): List<String> {
        return text
            .split(Regex("(?<=[.!?。？！…])\\s+|\\n+"))
            .map { it.trim().trim('-', ' ', '「', '」', '"', '\'') }
            .filter { it.isNotEmpty() }
    }

    /**
     * Scores a sentence. Higher is more interesting.
     */
    private fun score(sentence: String): Int {
        if (sentence.length < MIN_SENTENCE_LENGTH) return 0
        if (isFiller(sentence)) return 0
        var points = 0
        if (Regex("[0-9]").containsMatchIn(sentence)) points += 2
        if (containsAny(sentence, DECISION_KEYWORDS)) points += 3
        if (containsAny(sentence, ACTION_KEYWORDS)) points += 2
        if (sentence.contains('?') || sentence.contains('?')) points += 1
        if (sentence.length in 12..80) points += 1
        if (sentence.length > 120) points -= 1
        return points
    }

    private fun isFiller(sentence: String): Boolean {
        val normalized = sentence.replace("[\\s.,!?~ㅡ]", "").lowercase()
        if (normalized.isEmpty()) return true
        return FILLERS.contains(normalized)
    }

    private fun containsAny(sentence: String, keywords: List<String>): Boolean {
        return keywords.any { sentence.contains(it) }
    }

    // ── Extraction ───────────────────────────────────────────────────────────────

    private fun extractMatches(sentences: List<String>, patterns: List<Regex>): List<String> {
        val found = ArrayList<String>()
        sentences.forEach { sentence ->
            patterns.forEach { pattern ->
                pattern.findAll(sentence).forEach { match ->
                    val value = match.value.trim()
                    if (value.isNotEmpty()) found.add(value)
                }
            }
        }
        return found
    }

    /**
     * Extracts the most frequent meaningful words of the call.
     */
    private fun extractKeywords(sentences: List<String>): List<String> {
        val counter = HashMap<String, Int>()
        sentences.forEach { sentence ->
            sentence.split(TOKEN_SPLIT)
                .map { it.trim() }
                .filter { it.length >= 2 }
                .forEach { token ->
                    val cleaned = stripParticle(token)
                    if (cleaned.length < 2) return@forEach
                    if (cleaned in STOP_WORDS) return@forEach
                    if (Regex("^[0-9]+$").matches(cleaned)) return@forEach
                    counter[cleaned] = (counter[cleaned] ?: 0) + 1
                }
        }
        return counter.entries
            .filter { entry -> entry.value >= 2 }
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { entry -> entry.value }
                    .thenBy { entry -> entry.key }
            )
            .take(MAX_KEYWORDS)
            .map { entry -> entry.key }
    }

    /**
     * Removes the most common Korean postfixes so that "회의를" and "회의" are counted as one word.
     */
    private fun stripParticle(token: String): String {
        var result = token
        val particles = listOf("에서는", "에게서", "으로써", "이라고", "라고", "에서", "에게", "으로", "에는", "에도", "부터", "까지", "처럼", "만큼", "보다", "한테", "께서", "은", "는", "이", "가", "을", "를", "에", "와", "과", "도", "만", "의", "로", "야", "요")
        var changed = true
        while (changed) {
            changed = false
            for (particle in particles) {
                if (result.length - particle.length >= 1 && result.endsWith(particle)) {
                    result = result.dropLast(particle.length)
                    changed = true
                    break
                }
            }
        }
        return result
    }

    private fun talkTime(transcript: Transcript): Long {
        return transcript.segments.sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }
    }

    private fun coverage(transcript: Transcript, analysis: AudioAnalysis): Double {
        val total = if (analysis.durationMs > 0) analysis.durationMs else 0L
        if (total <= 0) return 0.0
        return (talkTime(transcript) * 100.0 / total).coerceIn(0.0, 1.0)
    }

    companion object {
        private const val MAX_KEY_POINTS = 5
        private const val MAX_ACTION_ITEMS = 6
        private const val MAX_KEYWORDS = 8
        private const val MAX_ITEMS = 8
        private const val MIN_SENTENCE_LENGTH = 6

        private val TOKEN_SPLIT = Regex("[^가-힣A-Za-z0-9]+")

        private val FILLERS = setOf(
            "응", "어", "아", "음", "네", "예", "오", "아니요", "아뇨", "네네", "어어", "ㅇㅇ", "ㅇㅋ",
            "맞아요", "그래요", "그래", "좋아요", "알겠습니다", "감사합니다", "挂了"
        )

        private val DECISION_KEYWORDS = listOf(
            "확정", "결정", "정해", "정하자", "하자", "할게요", "로 할", "으로 할", "취소", "변경",
            "연장", "연기", "대신", "대신에", "안 해도", "보류", " agreeable", "문제없", "괜찮", "맞다"
        )

        private val ACTION_KEYWORDS = listOf(
            "해야", "해야겠다", "해 주", "해주", "해줘", "부탁", "준비", "보내", "공유", "확인해",
            "정해서", "결제", "서명", "예약", "신청", "처리", "마감", "제출", "연락", "답변", "방문",
            "수리", "설치", "도착", "보내드", "회신", "검토", "보고", "정산", "입금", "출금"
        )

        private val SCHEDULE_PATTERNS = listOf(
            Regex("\\d{4}년\\s?\\d{1,2}월\\s?\\d{1,2}일"),
            Regex("\\d{1,2}월\\s?\\d{1,2}일"),
            Regex("\\d{1,2}월\\s?\\d{1,2}\\s?일"),
            Regex("(오전|오후|새벽|저녁)?\\s?\\d{1,2}시(\\s?\\d{1,2}분)?"),
            Regex("\\d{1,2}:\\d{2}"),
            Regex("\\d{1,2}\\s?일(요일)?"),
            Regex("\\d{1,2}\\s?월"),
            Regex("(오늘|내일|모레|글피|이번\\s?주|다음\\s?주|이번\\s?달|다음\\s?달|월요일|화요일|수요일|목요일|금요일|토요일|일요일)")
        )

        private val AMOUNT_PATTERNS = listOf(
            Regex("\\d[\\d,]*\\s?(억원|만원|천원|원|달러|유로|엔)"),
            Regex("[\\$₩€¥]\\s?\\d[\\d,]*")
        )

        private val STOP_WORDS = setOf(
            "그대", "저는", "저희", "우리", "해당", "경우", "때문", "정도", "가지", "이런", "저런",
            "그리고", "하지만", "그래서", "그러면", "이제", "아직", "이미", "방금", "조금", "다시",
            "이번", "지난", "다음", "오늘", "어제", "내일", "사람", "생각", "정말", "조금", "바로",
            "하나", "둘", "거", "것", "수", "때", "후", "중", "안", "좀", "더", "제일"
        )
    }
}
