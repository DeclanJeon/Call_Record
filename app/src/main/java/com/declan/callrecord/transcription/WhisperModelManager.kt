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

/**
 * A whisper.cpp model that can be downloaded on demand.
 *
 * @param id The identifier used in the preferences.
 * @param fileName The file name of the ggml model.
 * @param description A short human readable description.
 * @param sizeBytes The approximate download size in bytes.
 */
data class WhisperModel(
    val id: String,
    val fileName: String,
    val description: String,
    val sizeBytes: Long
) {
    /** The download size in a human readable form, e.g. "60 MB". */
    val formattedSize: String get() = "%.0f MB".format(sizeBytes / 1024.0 / 1024.0)
}

/**
 * Downloads and manages the whisper.cpp models.
 *
 * The models are fetched once from the whisper.cpp model repository and stored in the private
 * app storage, so no account, no payment and no per-call network access is required afterwards.
 */
object WhisperModelManager {

    /** The models offered to the user, from the smallest to the most accurate. */
    val MODELS: List<WhisperModel> = listOf(
        WhisperModel(
            id = "base-q5_1",
            fileName = "ggml-base-q5_1.bin",
            description = "가볍고 빠름 (정확도 낮음)",
            sizeBytes = 59_707_625L
        ),
        WhisperModel(
            id = "small-q5_1",
            fileName = "ggml-small-q5_1.bin",
            description = "균형 (한국어 권장)",
            sizeBytes = 190_085_487L
        ),
        WhisperModel(
            id = "small",
            fileName = "ggml-small.bin",
            description = "가장 정확 (용량 큼)",
            sizeBytes = 487_601_967L
        )
    )

    private const val MODEL_BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main"

    /** The model that is used when nothing else is selected. */
    val DEFAULT_MODEL: WhisperModel = MODELS[1]

    /**
     * Finds the model with the given id.
     */
    fun modelById(id: String): WhisperModel = MODELS.firstOrNull { it.id == id } ?: DEFAULT_MODEL

    /**
     * The directory that holds the downloaded models.
     */
    fun modelsDirectory(context: Context): File = File(context.filesDir, "models/whisper").apply { mkdirs() }

    /**
     * The file of the given model when it is already downloaded, otherwise null.
     */
    fun modelFile(context: Context, model: WhisperModel): File? {
        val file = File(modelsDirectory(context), model.fileName)
        return if (file.exists() && file.length() > 0) file else null
    }

    /**
     * True when the given model is installed.
     */
    fun isModelReady(context: Context, model: WhisperModel): Boolean = modelFile(context, model) != null

    /**
     * Downloads the given model if it is not present yet.
     *
     * @param onProgress Progress callback with a percentage between 0 and 100.
     */
    suspend fun ensureModel(
        context: Context,
        model: WhisperModel,
        onProgress: (Int) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        modelFile(context, model)?.let {
            onProgress(100)
            return@withContext true
        }
        val target = File(modelsDirectory(context), model.fileName)
        val temporary = File(target.parentFile, "${target.name}.part")
        try {
            onProgress(0)
            val connection = URL("$MODEL_BASE_URL/${model.fileName}").openConnection() as HttpURLConnection
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
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            onProgress(((downloaded * 100) / total).toInt().coerceIn(0, 99))
                        }
                    }
                }
            }
            connection.disconnect()
            if (target.exists()) target.delete()
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            onProgress(100)
            AppLogger.i( "Whisper model ${model.fileName} downloaded (${target.length()} bytes)" )
            true
        } catch (e: Exception) {
            AppLogger.e( "Whisper model download failed: ${e.message}", e)
            runCatching { temporary.delete() }
            false
        }
    }

    /**
     * Removes a downloaded model to reclaim storage.
     */
    suspend fun deleteModel(context: Context, model: WhisperModel): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            WhisperBridge.nativeReleaseModels()
            File(modelsDirectory(context), model.fileName).delete()
        }.isSuccess
    }
}
