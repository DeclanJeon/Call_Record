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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.declan.callrecord.data.AppPreferences
import com.declan.callrecord.ui.common.ToggleListItem
import com.declan.callrecord.ui.viewmodels.SettingsActions

/**
 * The Call_Record settings section.
 *
 * It exposes the three features that are specific to this app:
 *  - automatic verification report after every call
 *  - automatic on-device transcription of the call
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
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Call Record",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "녹음 후 자동으로 검증 리포트와 통화 내용 전사를 만듭니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Button(
            onClick = onOpenRecordings,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text("녹음 목록 열기")
        }

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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onDownloadModel, enabled = modelDownloadProgress == null) {
                if (modelDownloadProgress != null) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    Text("내려받는 중 $modelDownloadProgress%")
                } else {
                    Text(if (isModelReady) "음성 인식 모델 다시 받기" else "음성 인식 모델 받기 (약 40MB)")
                }
            }
        }
        Text(
            text = if (isModelReady) {
                "음성 인식 모델이 설치되어 있습니다. 통화 내용은 기기 안에서만 처리됩니다."
            } else {
                "모델이 없어도 녹음과 검증 리포트는 정상 동작합니다."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ToggleListItem(
            label = "Google Drive 백업",
            description = "녹음과 리포트를 Google Drive 폴더에 복사합니다. (계정 연동은 시스템 백업 설정을 그대로 사용)",
            checked = preferences.isDriveBackupEnabled(),
            onCheckedChange = { actions.setDriveBackupEnabled(it) }
        )

        OutlinedButton(
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
