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
import com.declan.callrecord.backup.DriveBackup
import com.declan.callrecord.data.AppPreferences
import com.declan.callrecord.data.call.EnrichedCallData
import com.declan.callrecord.summary.CallSummarizer
import com.declan.callrecord.summary.RuleBasedCallSummarizer
import com.declan.callrecord.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Produces a transcript for a decoded recording.
 */
fun interface TranscriptProvider {
    /**
     * @param pcm The decoded mono 16 kHz PCM of the recording.
     * @param language Language hint for the transcription engine.
     * @param onProgress Optional progress callback, receives a percentage 0..100.
     * @return the transcript, or null when no transcript could be produced.
     */
    suspend fun transcribe(
        pcm: PcmAudio,
        language: String,
        onProgress: (Int) -> Unit
    ): Transcript?
}

/**
 * Result of the post processing of a recording.
 *
 * @param reportUri Uri of the generated text report, when it could be written.
 * @param analysis The analysis result, when the file could be decoded.
 */
data class PostProcessResult(
    val reportUri: Uri? = null,
    val analysis: AudioAnalysis? = null,
    val transcript: Transcript? = null,
    val summary: com.declan.callrecord.summary.CallSummary? = null,
    val backedUpToDrive: Boolean = false,
    val error: String? = null
)

/**
 * Runs the Call_Record verification pipeline on a finished recording:
 *
 * 1. probe the container for technical metadata
 * 2. decode the audio to mono 16 kHz PCM (platform decoders only)
 * 3. run the energy based voice activity detection
 * 4. optionally produce a transcript (on-device speech to text)
 * 5. write the human readable `.txt` report, the `.json` report and the `.srt` subtitles
 * 6. optionally mirror everything to a Google Drive folder
 *
 * The pipeline is safe to run on any file and never throws; problems are reported in the result.
 */
class RecordingPostProcessor(private val context: Context) {

    /**
     * Processes the recording at [recordingUri].
     *
     * @param recordingUri Uri of the recorded file.
     * @param metadata Call metadata, when known.
     * @param callDurationMs Duration of the call reported by the telephony stack.
     * @param transcriptProvider Optional transcription engine.
     * @param language Language hint for the transcription engine.
     */
    suspend fun process(
        recordingUri: Uri,
        metadata: EnrichedCallData? = null,
        callDurationMs: Long? = null,
        transcriptProvider: TranscriptProvider? = null,
        language: String = "ko",
        summarizer: CallSummarizer? = RuleBasedCallSummarizer()
    ): PostProcessResult = withContext(Dispatchers.IO) {
        try {
            val preferences = AppPreferences(context)
            val recordingFile = DocumentFile.fromSingleUri(context, recordingUri)
            val parent = recordingFile?.parentFile
            if (parent == null) {
                return@withContext PostProcessResult(error = "저장 위치를 확인할 수 없습니다")
            }

            val info = AudioProbe.probe(context, recordingUri)
            val pcm = PcmDecoder.decode(context, recordingUri)
            val analysis = pcm?.let { AudioAnalyzer.analyze(it) }

            var transcript: Transcript? = null
            if (pcm != null && transcriptProvider != null) {
                transcript = runCatching { transcriptProvider.transcribe(pcm, language) {} }.getOrNull()
            }

            // The summary is built from the transcript when there is one, otherwise from the
            // analysis alone so that the report still contains the basic call statistics.
            val summary = runCatching {
                summarizer?.summarize(transcript ?: Transcript(), analysis ?: AudioAnalysis())
            }.getOrNull()

            val recordingName = recordingFile.name ?: "recording"
            val baseName = recordingName.substringBeforeLast('.', recordingName)

            val report = RecordingReport(
                fileName = recordingFile.name ?: baseName,
                info = info.copy(
                    sampleRate = pcm?.sourceSampleRate ?: 0,
                    channels = pcm?.sourceChannels ?: 0
                ),
                analysis = analysis ?: AudioAnalysis(),
                direction = metadata?.direction?.name?.lowercase(),
                phoneNumber = metadata?.getBestNumber()?.takeIf { it.isNotBlank() },
                callDurationMs = callDurationMs,
                transcript = transcript?.text,
                summary = summary
            )

            val reportUri = ReportWriter.write(context, parent, baseName, report)

            // Subtitles next to the report, when the transcript has time coded segments.
            var srtUri: Uri? = null
            val segments = transcript?.segments.orEmpty()
            if (segments.isNotEmpty()) {
                val srt = segments.mapIndexed { index, segment -> segment.toSrt(index) }.joinToString("\n")
                srtUri = ReportWriter.writeText(context, parent, "$baseName.srt", srt)
            }

            // Optional Google Drive mirror.
            var backedUp = false
            val driveFolder = preferences.getDriveBackupFolderUri()
            if (preferences.isDriveBackupEnabled() && driveFolder != null) {
                val sources = listOfNotNull(recordingUri, reportUri, srtUri)
                val copied = DriveBackup.backup(context, sources, driveFolder)
                backedUp = copied > 0
            }

            AppLogger.i( "Post processing finished: report=$reportUri verdict=${analysis?.verdict} drive=$backedUp" )
            PostProcessResult(
                reportUri = reportUri,
                analysis = analysis,
                transcript = transcript,
                summary = summary,
                backedUpToDrive = backedUp
            )
        } catch (e: Exception) {
            AppLogger.e( "Post processing failed: ${e.message}", e)
            PostProcessResult(error = e.message)
        }
    }
}
