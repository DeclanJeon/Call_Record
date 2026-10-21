/*
 * Call_Record: FOSS call recording for Android, based on ShizuCallRecorder.
 *  Copyright (C) 2026-present kitsumed (Med)   (original ShizuCallRecorder)
 *  Copyright (C) 2026 Call_Record contributors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.declan.callrecord.verify

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything we know about a finished recording, used to render the human readable report.
 *
 * @param fileName Name of the recording file.
 * @param info Technical metadata of the recording.
 * @param analysis Result of the voice activity analysis.
 * @param direction "in" / "out" / null when unknown.
 * @param phoneNumber The phone number of the call, when known.
 * @param callDurationMs Duration of the call reported by the telephony stack.
 * @param transcript The speech to text transcript, when a transcription was produced.
 * @param summary The call summary, when it could be produced.
 * @param createdAtMs Timestamp of the recording.
 */
data class RecordingReport(
    val fileName: String,
    val info: AudioInfo,
    val analysis: AudioAnalysis,
    val direction: String? = null,
    val phoneNumber: String? = null,
    val callDurationMs: Long? = null,
    val transcript: String? = null,
    val summary: com.declan.callrecord.summary.CallSummary? = null,
    val createdAtMs: Long = System.currentTimeMillis()
)

/**
 * Writes the verification report of a recording next to the recording itself.
 *
 * Two files are produced:
 *  - `<name>.txt` : human readable report (the "test file" of Call_Record)
 *  - `<name>.json`: machine readable counterpart for tooling
 */
object ReportWriter {

    private const val TEXT_MIME = "text/plain"
    private const val JSON_MIME = "application/json"

    /**
     * Writes both report files into [parent].
     *
     * @return the created text report uri, or null when the files could not be created.
     */
    fun write(
        context: Context,
        parent: DocumentFile,
        baseName: String,
        report: RecordingReport
    ): Uri? {
        val textFile = createFile(parent, "$baseName.txt", TEXT_MIME) ?: return null
        writeContent(context, textFile, buildTextReport(report).toByteArray(StandardCharsets.UTF_8))

        val jsonFile = createFile(parent, "$baseName.json", JSON_MIME)
        if (jsonFile != null) {
            writeContent(context, jsonFile, buildJsonReport(report).toString(2).toByteArray(StandardCharsets.UTF_8))
        }
        return textFile.uri
    }

    /**
     * Writes a plain text file into [parent].
     */
    fun writeText(context: Context, parent: DocumentFile, fileName: String, content: String): Uri? {
        val file = createFile(parent, fileName, TEXT_MIME) ?: return null
        writeContent(context, file, content.toByteArray(StandardCharsets.UTF_8))
        return file.uri
    }

    private fun createFile(parent: DocumentFile, name: String, mime: String): DocumentFile? {
        return runCatching { parent.createFile(mime, name) }.getOrNull()?.takeIf { it.exists() }
    }

    private fun writeContent(context: Context, file: DocumentFile, bytes: ByteArray) {
        runCatching {
            context.contentResolver.openOutputStream(file.uri, "wt")?.use { stream ->
                stream.write(bytes)
            }
        }
    }

    private fun formatTimestamp(ms: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return format.format(Date(ms))
    }

    private fun formatMs(ms: Long): String {
        val totalSeconds = ms / 1000
        return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }

    private fun buildTextReport(report: RecordingReport): String {
        val analysis = report.analysis
        val builder = StringBuilder()
        builder.appendLine("Call_Record — 녹음 검증 리포트")
        builder.appendLine("=".repeat(48))
        builder.appendLine("녹음 파일    : ${report.fileName}")
        builder.appendLine("생성 시각    : ${formatTimestamp(report.createdAtMs)}")
        report.direction?.let { builder.appendLine("통화 방향    : ${if (it == "in") "수신 (incoming)" else "발신 (outgoing)"}") }
        report.phoneNumber?.takeIf { it.isNotBlank() }?.let { builder.appendLine("전화번호     : $it") }
        report.callDurationMs?.let { builder.appendLine("통화 길이    : ${formatMs(it)}") }
        builder.appendLine("녹음 길이    : ${report.info.formattedDuration} (${report.analysis.durationMs} ms)")
        builder.appendLine("포맷        : ${report.info.codecLabel}")
        if (report.info.sampleRate > 0) builder.appendLine("샘플레이트   : ${report.info.sampleRate} Hz")
        if (report.info.channels > 0) builder.appendLine("채널        : ${report.info.channels}")
        if (report.info.bitrate > 0) builder.appendLine("비트레이트   : ${report.info.bitrate / 1000} kbps")
        builder.appendLine("파일 크기    : ${report.info.sizeBytes} bytes")
        builder.appendLine()
        builder.appendLine("[음성 분석]")
        builder.appendLine("전체 RMS     : ${"%.1f".format(analysis.overallRmsDb)} dBFS")
        builder.appendLine("최대 피크    : ${"%.1f".format(analysis.peakDb)} dBFS")
        builder.appendLine("노이즈 바닥  : ${"%.1f".format(analysis.noiseFloorDb)} dBFS")
        builder.appendLine("음성 구간    : ${analysis.voiceSegments.size} 개 / 총 ${"%.1f".format(analysis.voiceRatioPercent)} %")
        builder.appendLine("최장 무음    : ${formatMs(analysis.longestSilenceMs)}")
        builder.appendLine("판정        : ${verdictText(analysis.verdict)}")
        builder.appendLine()
        if (analysis.voiceSegments.isNotEmpty()) {
            builder.appendLine("[음성 구간 목록]")
            analysis.voiceSegments.take(50).forEach { segment ->
                builder.appendLine("  ${formatMs(segment.startMs)} ~ ${formatMs(segment.endMs)}  (${"%.1f".format(segment.durationMs / 1000.0)}초)")
            }
            if (analysis.voiceSegments.size > 50) {
                builder.appendLine("  ... 외 ${analysis.voiceSegments.size - 50}개")
            }
            builder.appendLine()
        }
        val summary = report.summary
        if (summary != null) {
            builder.appendLine("[통화 요약]")
            if (summary.isEmpty) {
                builder.appendLine("(요약할 만한 내용이 없습니다)")
            } else {
                if (summary.keyPoints.isNotEmpty()) {
                    builder.appendLine("• 핵심 내용")
                    summary.keyPoints.forEach { builder.appendLine("  - $it") }
                }
                if (summary.actionItems.isNotEmpty()) {
                    builder.appendLine("• 해야 할 일 / 요청")
                    summary.actionItems.forEach { builder.appendLine("  - $it") }
                }
                if (summary.schedules.isNotEmpty()) {
                    builder.appendLine("• 날짜·시간")
                    builder.appendLine("  " + summary.schedules.joinToString(", "))
                }
                if (summary.amounts.isNotEmpty()) {
                    builder.appendLine("• 금액")
                    builder.appendLine("  " + summary.amounts.joinToString(", "))
                }
                if (summary.keywords.isNotEmpty()) {
                    builder.appendLine("• 자주 나온 말")
                    builder.appendLine("  " + summary.keywords.joinToString(", "))
                }
                builder.appendLine("• 발화 시간 : ${formatMs(summary.talkTimeMs)} (전체 ${"%.1f".format(summary.coverageRatio * 100)} %)")
            }
            builder.appendLine()
        }
        if (!report.transcript.isNullOrBlank()) {
            builder.appendLine("[통화 내용 전사]")
            builder.appendLine("-".repeat(48))
            builder.appendLine(report.transcript)
            builder.appendLine("-".repeat(48))
        } else {
            builder.appendLine("[통화 내용 전사]")
            builder.appendLine("(아직 전사되지 않음 — 전사를 실행하면 이 파일이 갱신됩니다)")
        }
        return builder.toString()
    }

    private fun verdictText(verdict: AudioVerdict): String = when (verdict) {
        AudioVerdict.VOICE_DETECTED -> "통화 음성으로 보임 — 녹음에 대화 내용이 포함되어 있습니다"
        AudioVerdict.AUDIO_BUT_NO_SPEECH -> "오디오는 있으나 음성으로 판정되지 않음 — 신호음/잡음만 기록되었을 수 있습니다"
        AudioVerdict.SILENT_OR_EMPTY -> "무음 또는 빈 파일 — 녹음이 제대로 되지 않았을 수 있습니다"
        AudioVerdict.TOO_SHORT -> "녹음이 너무 짧아 판정할 수 없습니다"
    }

    private fun buildJsonReport(report: RecordingReport): JSONObject {
        val analysis = report.analysis
        val json = JSONObject()
        json.put("app", "Call_Record")
        json.put("file", report.fileName)
        json.put("createdAt", formatTimestamp(report.createdAtMs))
        json.put("createdAtMs", report.createdAtMs)
        report.direction?.let { json.put("direction", it) }
        report.phoneNumber?.takeIf { it.isNotBlank() }?.let { json.put("phoneNumber", it) }
        report.callDurationMs?.let { json.put("callDurationMs", it) }

        val info = JSONObject()
        info.put("durationMs", report.info.durationMs)
        info.put("mimeType", report.info.mimeType)
        info.put("codec", report.info.codecLabel)
        info.put("bitrate", report.info.bitrate)
        info.put("sampleRate", report.info.sampleRate)
        info.put("channels", report.info.channels)
        info.put("sizeBytes", report.info.sizeBytes)
        json.put("media", info)

        val stats = JSONObject()
        stats.put("durationMs", analysis.durationMs)
        stats.put("overallRmsDb", analysis.overallRmsDb)
        stats.put("peakDb", analysis.peakDb)
        stats.put("noiseFloorDb", analysis.noiseFloorDb)
        stats.put("voiceDurationMs", analysis.voiceDurationMs)
        stats.put("voiceRatioPercent", analysis.voiceRatioPercent)
        stats.put("longestSilenceMs", analysis.longestSilenceMs)
        stats.put("verdict", analysis.verdict.name)
        json.put("analysis", stats)

        val segments = JSONArray()
        analysis.voiceSegments.forEach { segment ->
            segments.put(
                JSONObject()
                    .put("startMs", segment.startMs)
                    .put("endMs", segment.endMs)
                    .put("durationMs", segment.durationMs)
            )
        }
        json.put("voiceSegments", segments)
        report.transcript?.let { json.put("transcript", it) }
        report.summary?.let { summary ->
            val summaryJson = JSONObject()
            summaryJson.put("keyPoints", JSONArray(summary.keyPoints))
            summaryJson.put("actionItems", JSONArray(summary.actionItems))
            summaryJson.put("schedules", JSONArray(summary.schedules))
            summaryJson.put("amounts", JSONArray(summary.amounts))
            summaryJson.put("keywords", JSONArray(summary.keywords))
            summaryJson.put("talkTimeMs", summary.talkTimeMs)
            summaryJson.put("coverageRatio", summary.coverageRatio)
            json.put("summary", summaryJson)
        }
        return json
    }
}
