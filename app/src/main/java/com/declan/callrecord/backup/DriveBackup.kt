/*
 * Call_Record: FOSS call recording for Android, based on ShizuCallRecorder.
 *  Copyright (C) 2026-present kitsumed (Med)   (original ShizuCallRecorder)
 *  Copyright (C) 2026 Call_Record contributors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.declan.callrecord.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.declan.callrecord.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

/**
 * Mirrors recordings and their reports into a Google Drive folder.
 *
 * The backup folder is picked with the Storage Access Framework, which means the user selects
 * "Google Drive" in the system folder picker. No OAuth client, no Google Cloud project and no
 * extra account handling is needed: the Google account is already configured in the system.
 */
object DriveBackup {

    /**
     * Copies the given files into the Drive backup folder.
     *
     * @param sourceUris Files to copy.
     * @param driveTreeUri Tree uri of the backup folder.
     * @return the number of files that were copied successfully.
     */
    suspend fun backup(context: Context, sourceUris: List<Uri>, driveTreeUri: Uri): Int = withContext(Dispatchers.IO) {
        val target = DocumentFile.fromTreeUri(context, driveTreeUri) ?: return@withContext 0
        if (!target.canWrite()) {
            AppLogger.w( "The Drive backup folder is not writable" )
            return@withContext 0
        }
        var copied = 0
        for (sourceUri in sourceUris) {
            val source = DocumentFile.fromSingleUri(context, sourceUri) ?: continue
            val name = source.name ?: continue
            if (target.findFile(name) != null) {
                AppLogger.d( "Skipping $name, it already exists in the backup folder" )
                continue
            }
            val created = runCatching { target.createFile(mimeTypeOf(name), name) }.getOrNull()
            if (created == null) {
                AppLogger.w( "Could not create $name in the backup folder" )
                continue
            }
            val success = runCatching {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    context.contentResolver.openOutputStream(created.uri, "wt")?.use { output ->
                        input.copyTo(output)
                    } ?: throw FileNotFoundException("Could not open the source file")
                } ?: throw FileNotFoundException("Could not open the source file")
            }.isSuccess
            if (success) {
                copied++
                AppLogger.i( "Backed up $name to Google Drive" )
            } else {
                runCatching { created.delete() }
            }
        }
        copied
    }

    private fun mimeTypeOf(name: String): String = when {
        name.endsWith(".ogg", true) -> "audio/ogg"
        name.endsWith(".m4a", true) || name.endsWith(".mp4", true) -> "audio/mp4"
        name.endsWith(".json", true) -> "application/json"
        name.endsWith(".srt", true) -> "application/x-subrip"
        name.endsWith(".txt", true) -> "text/plain"
        else -> "application/octet-stream"
    }
}
