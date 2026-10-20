/*
 * Call_Record: FOSS call recording for Android, based on ShizuCallRecorder.
 *  Copyright (C) 2026-present kitsumed (Med)   (original ShizuCallRecorder)
 *  Copyright (C) 2026 Call_Record contributors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.declan.callrecord.transcription

import android.content.Context
import com.declan.callrecord.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Downloads and manages the on-device speech recognition model.
 *
 * The model is fetched once from the Vosk model repository and stored in the app private
 * storage, so no account, no payment and no per-call network access is required afterwards.
 */
object ModelManager {

    /** The model used for Korean transcription. */
    const val MODEL_ID = "vosk-model-small-ko-0.22"

    private const val MODEL_URL = "https://alphacephei.com/vosk/models/$MODEL_ID.zip"

    /** Marker file written after a successful extraction. */
    private const val READY_MARKER = ".model_ready"

    /**
     * Returns the directory that holds the extracted model, or null when the model is missing.
     */
    fun modelDirectory(context: Context): File? {
        val modelsDir = File(context.filesDir, "models")
        val modelDir = File(modelsDir, MODEL_ID)
        return if (modelDir.exists() && File(modelDir, READY_MARKER).exists()) modelDir else null
    }

    /** True when the model is present and can be used. */
    fun isModelReady(context: Context): Boolean = modelDirectory(context) != null

    /**
     * Downloads and extracts the model if needed.
     *
     * @param onProgress Progress callback with a percentage between 0 and 100.
     */
    suspend fun ensureModel(context: Context, onProgress: (Int) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        if (isModelReady(context)) {
            onProgress(100)
            return@withContext true
        }
        val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
        val zipFile = File(context.cacheDir, "$MODEL_ID.zip")
        try {
            onProgress(0)
            val total = downloadModel(zipFile, onProgress)
            AppLogger.i( "Model archive downloaded: ${total} bytes")
            onProgress(90)
            extract(zipFile, modelsDir)
            val modelDir = File(modelsDir, MODEL_ID)
            if (!modelDir.exists()) {
                // Some archives contain a different top level folder name, use the first directory found.
                val candidate = modelsDir.listFiles()?.firstOrNull { it.isDirectory } ?: return@withContext false
                candidate.renameTo(modelDir)
            }
            File(modelDir, READY_MARKER).writeText("ok")
            onProgress(100)
            true
        } catch (e: Exception) {
            AppLogger.e( "Model download failed: ${e.message}", e)
            runCatching { zipFile.delete() }
            false
        }
    }

    /** Removes the downloaded model to reclaim storage. */
    suspend fun deleteModel(context: Context): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            File(context.filesDir, "models").deleteRecursively()
            File(context.cacheDir, "$MODEL_ID.zip").delete()
        }.isSuccess
    }

    private fun downloadModel(target: File, onProgress: (Int) -> Unit): Long {
        val connection = URL(MODEL_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.connect()
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("Model download failed with HTTP ${connection.responseCode}")
        }
        val total = connection.contentLengthLong
        var downloaded = 0L
        connection.inputStream.use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    if (total > 0) {
                        onProgress(((downloaded * 100) / total).toInt().coerceIn(0, 89))
                    }
                }
            }
        }
        connection.disconnect()
        return downloaded
    }

    private fun extract(zipFile: File, targetDir: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val outFile = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { output -> zip.copyTo(output) }
                }
                zip.closeEntry()
            }
        }
    }
}
