package com.fush.erp.data.audit

import com.fush.erp.data.entity.AuditEventEntity

/**
 * Audit Controls P1 runtime metadata only.
 *
 * The session reference is derived from the persisted user sessionVersion and is not an
 * authentication token or secret. P1 does not change the result of the audited operation.
 */
object AuditEventMetadata {
    const val RUNTIME_DEVICE = "ANDROID"
    const val RUNTIME_SOURCE = "ANDROID_APP"
    const val LEGACY_VALUE = "LEGACY_PRE_P1"

    fun sessionReference(actorUserId: Long, sessionVersion: Long?): String =
        if (sessionVersion == null) {
            "ACTOR:$actorUserId;SESSION:UNRESOLVED"
        } else {
            "ACTOR:$actorUserId;SESSION_VERSION:$sessionVersion"
        }

    fun enrich(
        row: AuditEventEntity,
        sessionVersion: Long?,
        runtimeDeviceInfo: String = RUNTIME_DEVICE
    ): AuditEventEntity =
        row.copy(
            deviceInfo = when {
                row.deviceInfo.isBlank() || row.deviceInfo == RUNTIME_DEVICE ->
                    runtimeDeviceInfo.ifBlank { RUNTIME_DEVICE }
                else -> row.deviceInfo
            },
            sessionId = row.sessionId.ifBlank { sessionReference(row.userId, sessionVersion) },
            source = row.source.ifBlank { RUNTIME_SOURCE }
        )
}
