/*
 * Call_Record: FOSS call recording for Android, based on ShizuCallRecorder.
 *  Copyright (C) 2026-present kitsumed (Med)   (original ShizuCallRecorder)
 *  Copyright (C) 2026 Call_Record contributors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.declan.callrecord.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * An application wide coroutine scope.
 *
 * Work that must survive the death of a service (for example the verification report and the
 * transcription of a finished recording) is launched here, because a service scope is cancelled
 * in `onDestroy()`.
 */
object AppScope {

    /** The application wide scope. Never cancelled during the lifetime of the process. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
