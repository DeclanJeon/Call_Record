/*
 * Call_Record: FOSS call recording for Android, based on ShizuCallRecorder.
 *  Copyright (C) 2026-present kitsumed (Med)   (original ShizuCallRecorder)
 *  Copyright (C) 2026 Call_Record contributors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.declan.callrecord.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.declan.callrecord.ui.viewmodels.RecordingsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lists the recordings of the selected folder and lets the user run the Call_Record
 * verification report and the on-device transcription on them.
 *
 * @param onBack Called when the user leaves the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: RecordingsViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.refresh() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("녹음 목록") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로 가기")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.items.isEmpty() && !state.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "저장된 녹음이 없습니다.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "녹음 폴더는 설정에서 지정합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.items, key = { it.uri.toString() }) { item ->
                RecordingRow(
                    name = item.name,
                    sizeBytes = item.sizeBytes,
                    lastModified = item.lastModified,
                    isAudio = item.isAudio,
                    isBusy = state.busyUri == item.uri,
                    onVerify = { viewModel.verify(item) },
                    onTranscribe = { viewModel.transcribe(item) },
                    onOpen = { openFile(context, item.uri) }
                )
            }
        }
    }
}

/**
 * A single row of the recordings list.
 */
@Composable
private fun RecordingRow(
    name: String,
    sizeBytes: Long,
    lastModified: Long,
    isAudio: Boolean,
    isBusy: Boolean,
    onVerify: () -> Unit,
    onTranscribe: () -> Unit,
    onOpen: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isAudio) Icons.Filled.AudioFile else Icons.Filled.Article,
                contentDescription = null
            )
            Spacer(Modifier.height(8.dp))
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(text = name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    text = "${dateFormat.format(Date(lastModified))} · ${formatSize(sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isBusy) {
                CircularProgressIndicator(modifier = Modifier.height(24.dp))
            }
        }
        if (isAudio) {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onVerify, enabled = !isBusy) {
                    Icon(Icons.Filled.GraphicEq, contentDescription = null)
                    Spacer(Modifier.height(8.dp))
                    Text("검증 리포트")
                }
                Button(onClick = onTranscribe, enabled = !isBusy) {
                    Icon(Icons.Filled.Subtitles, contentDescription = null)
                    Spacer(Modifier.height(8.dp))
                    Text("내용 전사")
                }
            }
        } else {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onOpen, enabled = !isBusy) {
                    Icon(Icons.Filled.Article, contentDescription = null)
                    Spacer(Modifier.height(8.dp))
                    Text(if (name.endsWith(".srt", true)) "자막 열기" else "리포트 열기")
                }
            }
        }
    }
}

/**
 * Opens the given file with the app that can handle it.
 */
private fun openFile(context: android.content.Context, uri: android.net.Uri) {
    runCatching {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, null)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return "%.1f KB".format(bytes / 1024.0)
    return "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
