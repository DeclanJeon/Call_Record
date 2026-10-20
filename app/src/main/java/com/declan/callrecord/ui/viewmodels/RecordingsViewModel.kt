/*
 * Call_Record: FOSS call recording for Android, based on ShizuCallRecorder.
 *  Copyright (C) 2026-present kitsumed (Med)   (original ShizuCallRecorder)
 *  Copyright (C) 2026 Call_Record contributors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.declan.callrecord.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.declan.callrecord.data.AppPreferences
import com.declan.callrecord.transcription.TranscriptionService
import com.declan.callrecord.utils.AppLogger
import com.declan.callrecord.verify.RecordingPostProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A single recording shown in the recordings list.
 *
 * @param name Display name of the file.
 * @param uri Uri of the file.
 * @param sizeBytes Size of the file in bytes.
 * @param lastModified Last modification time in milliseconds.
 * @param isAudio True for the audio files, false for the generated reports.
 */
data class RecordingItem(
    val name: String,
    val uri: Uri,
    val sizeBytes: Long,
    val lastModified: Long,
    val isAudio: Boolean
)

/**
 * State of the recordings screen.
 *
 * @param items The files found in the recording folder.
 * @param busyUri The file that is currently being processed, null when idle.
 * @param message A short message to show to the user, null when there is nothing to show.
 */
data class RecordingsUiState(
    val items: List<RecordingItem> = emptyList(),
    val busyUri: Uri? = null,
    val message: String? = null,
    val isLoading: Boolean = false
)

/**
 * Loads the recordings of the selected folder and runs the Call_Record verification and
 * transcription pipeline on them.
 */
class RecordingsViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val preferences = AppPreferences(appContext)

    private val _uiState = MutableStateFlow(RecordingsUiState())
    val uiState: StateFlow<RecordingsUiState> = _uiState.asStateFlow()

    /** Refreshes the list of files in the recording folder. */
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val items = loadItems()
            _uiState.value = _uiState.value.copy(items = items, isLoading = false)
        }
    }

    /** Runs the verification report pipeline for the given file. */
    fun verify(item: RecordingItem) = runPipeline(item, withTranscript = false)

    /** Runs the verification report pipeline including the on-device transcription. */
    fun transcribe(item: RecordingItem) = runPipeline(item, withTranscript = true)

    /** Clears the current message. */
    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    /** Deletes a file from the recording folder. */
    fun delete(item: RecordingItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val deleted = runCatching {
                DocumentFile.fromSingleUri(appContext, item.uri)?.delete() ?: false
            }.getOrDefault(false)
            _uiState.value = _uiState.value.copy(
                items = loadItems(),
                message = if (deleted) "삭제했습니다: ${item.name}" else "삭제할 수 없습니다: ${item.name}"
            )
        }
    }

    private fun runPipeline(item: RecordingItem, withTranscript: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busyUri = item.uri, message = null)
            val provider = if (withTranscript && TranscriptionService.isReady(appContext)) {
                TranscriptionService.transcriptProvider(appContext)
            } else {
                null
            }
            val result = RecordingPostProcessor(appContext).process(
                recordingUri = item.uri,
                transcriptProvider = provider
            )
            val message = when {
                result.error != null -> "처리 실패: ${result.error}"
                withTranscript -> "전사 완료: ${item.name}"
                else -> "검증 리포트 생성 완료: ${item.name}"
            }
            AppLogger.i( "Pipeline finished for ${item.name}: $message" )
            _uiState.value = _uiState.value.copy(
                busyUri = null,
                items = loadItems(),
                message = message
            )
        }
    }

    private suspend fun loadItems(): List<RecordingItem> = withContext(Dispatchers.IO) {
        val folderUri = preferences.getRecordingFolderUri() ?: return@withContext emptyList()
        val folder = DocumentFile.fromTreeUri(appContext, folderUri) ?: return@withContext emptyList()
        folder.listFiles()
            .filter { it.isFile }
            .map { file ->
                RecordingItem(
                    name = file.name ?: "unknown",
                    uri = file.uri,
                    sizeBytes = file.length(),
                    lastModified = file.lastModified(),
                    isAudio = file.type?.startsWith("audio/") == true || file.name?.endsWith(".ogg", true) == true
                )
            }
            .sortedByDescending { it.lastModified }
    }
}
