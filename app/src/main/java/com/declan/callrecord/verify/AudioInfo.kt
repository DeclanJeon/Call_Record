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
import android.media.MediaMetadataRetriever
import android.net.Uri

/**
 * Metadata of a recorded audio file, extracted with the platform [MediaMetadataRetriever].
 *
 * @param durationMs Duration of the media in milliseconds.
 * @param mimeType Container/codec mime type, e.g. "audio/ogg" or "audio/mp4".
 * @param bitrate Average bitrate in bits per second, 0 when unknown.
 * @param sampleRate Sample rate in Hz, 0 when unknown.
 * @param channels Number of audio channels, 0 when unknown.
 * @param sizeBytes Size of the file in bytes.
 */
data class AudioInfo(
    val durationMs: Long = 0L,
    val mimeType: String = "unknown",
    val bitrate: Int = 0,
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val sizeBytes: Long = 0L
) {
    /** Human readable duration, e.g. "03:05" or "01:02:03". */
    val formattedDuration: String
        get() {
            val totalSeconds = durationMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                "%02d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                "%02d:%02d".format(minutes, seconds)
            }
        }

    /** Codec name derived from the mime type, e.g. "Opus" for "audio/ogg". */
    val codecLabel: String
        get() = when {
            mimeType.contains("ogg") -> "OGG/Opus"
            mimeType.contains("mp4") || mimeType.contains("aac") -> "M4A/AAC"
            mimeType.contains("wav") -> "WAV/PCM"
            mimeType.contains("amr") -> "AMR"
            else -> mimeType
        }
}

/**
 * Reads technical metadata of a recorded file.
 */
object AudioProbe {

    /**
     * Probes the given recording. Never throws: on failure a best-effort result is returned.
     */
    fun probe(context: Context, uri: Uri): AudioInfo {
        val retriever = MediaMetadataRetriever()
        var result = AudioInfo()
        try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "unknown"
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
            result = AudioInfo(
                durationMs = duration,
                mimeType = mime,
                bitrate = bitrate,
                sizeBytes = runCatching {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
                }.getOrNull() ?: 0L
            )
        } catch (_: Exception) {
            // Metadata is best-effort; the analysis stage will report the real problem.
        } finally {
            runCatching { retriever.release() }
        }
        return result
    }
}
