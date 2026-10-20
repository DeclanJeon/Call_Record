/*
 * Call_Record: FOSS call recording for Android, based on ShizuCallRecorder.
 *  Copyright (C) 2026-present kitsumed (Med)   (original ShizuCallRecorder)
 *  Copyright (C) 2026 Call_Record contributors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.declan.callrecord.data

import android.content.Context
import android.content.pm.PackageManager
import android.telecom.TelecomManager

/**
 * A messenger / calling application that can place calls through the Android telecom system.
 *
 * @param packageName The Android package name of the application.
 * @param label The human readable name shown in the settings.
 */
data class KnownCallApp(
    val packageName: String,
    val label: String
)

/**
 * Registry of the messaging applications that Call_Record can record.
 *
 * These applications place their calls through the Android telecom system (self managed calls),
 * which is what makes them visible to the [android.telecom.InCallService] of this app.
 */
object CallAppRegistry {

    /**
     * The known calling applications, in the order they are shown in the settings.
     */
    val KNOWN_APPS: List<KnownCallApp> = listOf(
        KnownCallApp("com.kakao.talk", "카카오톡"),
        KnownCallApp("com.kakao.work", "카카오워크"),
        KnownCallApp("org.telegram.messenger", "텔레그램"),
        KnownCallApp("com.line.android", "LINE"),
        KnownCallApp("com.whatsapp", "WhatsApp"),
        KnownCallApp("com.whatsapp.w4b", "WhatsApp Business"),
        KnownCallApp("com.skype.raider", "Skype"),
        KnownCallApp("com.google.android.apps.meet", "Google Meet"),
        KnownCallApp("us.zoom.videomeetings", "Zoom"),
        KnownCallApp("com.microsoft.teams", "Microsoft Teams"),
        KnownCallApp("org.thoughtcrime.securesms", "Signal"),
        KnownCallApp("com.viber.voip", "Viber"),
        KnownCallApp("com.discord", "Discord"),
        KnownCallApp("com.facebook.orca", "Facebook Messenger"),
        KnownCallApp("com.tencent.mm", "WeChat"),
        KnownCallApp("com.tencent.mobileqq", "QQ"),
        KnownCallApp("com.imo", "imo"),
        KnownCallApp("com.google.android.apps.talk", "Google Voice")
    )

    /**
     * Returns the known calling applications that are installed on the device.
     */
    fun installedApps(context: Context): List<KnownCallApp> {
        val packageManager = context.packageManager
        return KNOWN_APPS.filter { app ->
            runCatching {
                packageManager.getPackageInfo(app.packageName, 0)
                true
            }.getOrElse { false }
        }
    }

    /**
     * Returns the label of a package, falling back to the package name.
     */
    fun labelOf(context: Context, packageName: String): String {
        val packageManager = context.packageManager
        return runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrElse { packageName }
    }
}

/**
 * Helpers around the package that owns a call.
 */
object CallPackageUtils {

    /**
     * True when the package is the system dialer or the default dialer, which means the call is a
     * regular carrier call and not an app (OTT) call.
     */
    fun isSystemDialer(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return true
        val telecomManager = context.getSystemService(TelecomManager::class.java)
        return packageName == telecomManager?.systemDialerPackage ||
                packageName == telecomManager?.defaultDialerPackage ||
                packageName == "com.android.phone"
    }

    /**
     * True when the call comes from a messaging application instead of the carrier.
     */
    fun isAppCall(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return !isSystemDialer(context, packageName)
    }
}
