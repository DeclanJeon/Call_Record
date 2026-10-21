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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.declan.callrecord.data.AppPreferences
import com.declan.callrecord.data.CallAppRegistry
import com.declan.callrecord.notify.AnnouncementTone
import com.declan.callrecord.transcription.TranscriptionService
import com.declan.callrecord.transcription.WhisperModelManager
import com.declan.callrecord.ui.common.M3DropdownField
import com.declan.callrecord.ui.common.OptionItem
import com.declan.callrecord.ui.common.ToggleListItem
import com.declan.callrecord.ui.viewmodels.SettingsActions

/**
 * The Call_Record settings section.
 *
 * It exposes the features that are specific to this app:
 *  - the announcement tone that tells the other party that the call is recorded
 *  - recording of calls that are placed by messaging applications
 *  - automatic verification report and transcription after every call
 *  - mirroring the recordings to a Google Drive folder
 *
 * @param preferences The preferences store.
 * @param updateTrigger Trigger value that forces a recomposition when settings change.
 * @param actions The settings actions used to persist changes.
 * @param onOpenRecordings Called when the user wants to see the list of recordings.
 * @param onSelectDriveFolder Called when the user wants to pick the Google Drive backup folder.
 * @param onDownloadModel Called when the user wants to download the speech recognition model.
 * @param isModelReady True when the speech recognition model is installed.
 * @param modelDownloadProgress Download progress in percent, or null when no download is running.
 */
@Composable
fun CallRecordSection(
    preferences: AppPreferences,
    updateTrigger: Int,
    actions: SettingsActions,
    onOpenRecordings: () -> Unit,
    onSelectDriveFolder: () -> Unit,
    onDownloadModel: () -> Unit,
    isModelReady: Boolean,
    modelDownloadProgress: Int?,
    modelStatusText: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Call Record",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "통화가 시작되면 자동으로 녹음되고, 끝나면 검증 리포트와 통화 내용이 자동으로 만들어집니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // ── 녹음 고지음 ──────────────────────────────────────────────
        ToggleListItem(
            label = "녹음 고지음",
            description = "녹음이 시작될 때 짧은 신호음을 통화 경로에 넣습니다. 상대방도 들을 수 있어 통화 내용 recording에 동의한 것으로 볼 수 있습니다.",
            checked = preferences.isAnnouncementToneEnabled(),
            onCheckedChange = { actions.setAnnouncementToneEnabled(it) }
        )

        val previewContext = LocalContext.current
        val previewScope = rememberCoroutineScope()
        val toneOptions = listOf(
            OptionItem(AnnouncementTone.TYPE_SHORT, "짧은 삐 소리", "한 번 짧게"),
            OptionItem(AnnouncementTone.TYPE_DOUBLE, "두 번 삐 소리", "확실하게 알림"),
            OptionItem(AnnouncementTone.TYPE_CHIME, "차임", "띵동 음")
        )
        val selectedTone = toneOptions.firstOrNull { it.key == preferences.getAnnouncementToneType() } ?: toneOptions.first()
        M3DropdownField(
            label = "고지음 종류",
            selected = selectedTone,
            options = toneOptions,
            onOptionSelected = { actions.setAnnouncementToneType(it.key) }
        )

        androidx.compose.material3.OutlinedButton(
            onClick = {
                previewScope.launch {
                    AnnouncementTone.play(previewContext, selectedTone.key, playLocally = true)
                }
            },
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        ) {
            Text("고지음 미리듣기")
        }

        // ── 메신저 앱 통화 ───────────────────────────────────────────
        Text(
            text = "메신저 앱 통화",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
        )
        Text(
            text = "카카오톡, 텔레그램, LINE, WhatsApp 등에서 하는 통화도 녹음합니다. " +
                "(안드로이드 12 이상 필요 · 상대 음성 품질은 기기/앱마다 차이가 있습니다)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ToggleListItem(
            label = "모든 앱 통화 녹음",
            description = "켜면 아래 목록과 관계없이 모든 메신저 통화를 녹음합니다.",
            checked = preferences.isRecordAllAppCallsEnabled(),
            onCheckedChange = { actions.setRecordAllAppCallsEnabled(it) }
        )

        if (!preferences.isRecordAllAppCallsEnabled()) {
            val appContext = LocalContext.current
            val installedApps = remember(appContext) { CallAppRegistry.installedApps(appContext) }
            if (installedApps.isEmpty()) {
                Text(
                    text = "설치된 통화 앱을 찾지 못했습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val selectedPackages = preferences.getRecordedAppPackages()
                installedApps.forEach { app ->
                    val checked = selectedPackages.contains(app.packageName)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                val updated = selectedPackages.toMutableSet()
                                if (isChecked) updated.add(app.packageName) else updated.remove(app.packageName)
                                actions.setRecordedAppPackages(updated)
                            }
                        )
                        Text(text = app.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        val appSourceOptions = listOf(
            OptionItem("mic-voice-communication", "마이크 (기본)", "상대 음성이 약하게 잡힐 수 있습니다"),
            OptionItem("output", "스피커 출력", "스피커폰 통화에서 상대 음성이 선명합니다"),
            OptionItem("playback", "앱 재생음 캡처", "앱이 허용한 경우에만 잡힙니다"),
            OptionItem("mic-voice-recognition", "마이크 (음성 인식 모드)", "잡음은 적지만 음이 작을 수 있습니다")
        )
        val selectedSource = appSourceOptions.firstOrNull { it.key == preferences.getAppCallAudioSource() } ?: appSourceOptions.first()
        M3DropdownField(
            label = "앱 통화 녹음 소스",
            selected = selectedSource,
            options = appSourceOptions,
            onOptionSelected = { actions.setAppCallAudioSource(it.key) }
        )

        // ── 녹음 목록 ────────────────────────────────────────────────
        Text(
            text = "녹음 결과",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.material3.Button(
                onClick = onOpenRecordings,
                modifier = Modifier.weight(1f)
            ) {
                Text("녹음 목록 열기")
            }
        }

        // ── 검증 리포트 / 전사 ───────────────────────────────────────
        ToggleListItem(
            label = "검증 리포트 자동 생성",
            description = "통화가 끝나면 같은 폴더에 .txt(사람용)와 .json(기계용) 리포트를 만듭니다.",
            checked = preferences.isVerificationReportEnabled(),
            onCheckedChange = { actions.setVerificationReportEnabled(it) }
        )

        ToggleListItem(
            label = "통화 내용 자동 전사",
            description = "폰에서만 동작하는 음성 인식으로 통화 내용을 텍스트로 남깁니다.",
            checked = preferences.isTranscriptionEnabled(),
            onCheckedChange = { actions.setTranscriptionEnabled(it) }
        )

        // ── 전사 엔진 선택 ──────────────────────────────────────
        val engineOptions = listOf(
            OptionItem(TranscriptionService.ENGINE_VOSK, "Vosk (가벼움, 빠름)", "작은 한국어 모델, 약 40MB"),
            OptionItem(TranscriptionService.ENGINE_WHISPER, "Whisper (정확함)", "whisper.cpp 기반, 한국어 정확도가 훨씬 높음")
        )
        val selectedEngine = engineOptions.firstOrNull { it.key == preferences.getTranscriptionEngine() } ?: engineOptions.first()
        M3DropdownField(
            label = "전사 엔진",
            selected = selectedEngine,
            options = engineOptions,
            onOptionSelected = { actions.setTranscriptionEngine(it.key) }
        )

        if (selectedEngine.key == TranscriptionService.ENGINE_WHISPER) {
            val modelOptions = WhisperModelManager.MODELS.map {
                OptionItem(it.id, it.description, "${it.fileName} · ${it.formattedSize}")
            }
            val selectedModel = modelOptions.firstOrNull { it.key == preferences.getWhisperModelId() } ?: modelOptions.first()
            M3DropdownField(
                label = "Whisper 모델",
                selected = selectedModel,
                options = modelOptions,
                onOptionSelected = { actions.setWhisperModelId(it.key) }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.material3.OutlinedButton(onClick = onDownloadModel, enabled = modelDownloadProgress == null) {
                if (modelDownloadProgress != null) {
                    Text("내려받는 중 $modelDownloadProgress%")
                } else {
                    Text(if (isModelReady) "모델 다시 받기" else "음성 인식 모델 받기")
                }
            }
        }
        Text(
            text = modelStatusText + if (isModelReady) {
                " · 통화 내용은 기기 안에서만 처리됩니다."
            } else {
                " · 모델이 없어도 녹음과 검증 리포트는 정상 동작합니다."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ── Google Drive 백업 ────────────────────────────────────────
        ToggleListItem(
            label = "Google Drive 백업",
            description = "녹음과 리포트를 Google Drive 폴더에 복사합니다. (계정 연동은 시스템 백업 설정을 그대로 사용)",
            checked = preferences.isDriveBackupEnabled(),
            onCheckedChange = { actions.setDriveBackupEnabled(it) }
        )

        androidx.compose.material3.OutlinedButton(
            onClick = onSelectDriveFolder,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        ) {
            Text(
                if (preferences.getDriveBackupFolderUri() != null) {
                    "백업 폴더 변경 (현재: ${preferences.getDriveBackupFolderUri()?.lastPathSegment})"
                } else {
                    "백업 폴더 선택 (Google Drive)"
                }
            )
        }
    }
}
