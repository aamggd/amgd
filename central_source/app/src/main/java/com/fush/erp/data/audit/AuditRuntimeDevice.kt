package com.fush.erp.data.audit

import android.os.Build

/**
 * Non-secret device context for Audit Controls P1.
 *
 * Deliberately excludes serial numbers, Android IDs, advertising IDs and any credential/token.
 */
object AuditRuntimeDevice {
    fun current(): String {
        val manufacturer = Build.MANUFACTURER?.trim().orEmpty().ifBlank { "UNKNOWN" }
        val model = Build.MODEL?.trim().orEmpty().ifBlank { "UNKNOWN" }
        return "ANDROID;MANUFACTURER=$manufacturer;MODEL=$model;SDK=${Build.VERSION.SDK_INT}"
    }
}
