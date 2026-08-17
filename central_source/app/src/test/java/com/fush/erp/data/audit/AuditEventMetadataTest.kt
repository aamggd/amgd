package com.fush.erp.data.audit

import com.fush.erp.data.entity.AuditEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditEventMetadataTest {
    @Test
    fun `p1 enrichment captures actor session device time entity action source and reason`() {
        val row = AuditEventEntity(
            id = 41,
            eventAt = 1_786_910_400_000L,
            userId = 7,
            action = "POST",
            entityType = "JOURNAL_ENTRY",
            entityId = "JE-77",
            oldValue = "status=DRAFT",
            newValue = "status=POSTED",
            reason = "approved close",
            deviceInfo = "",
            sessionId = "",
            source = ""
        )

        val enriched = AuditEventMetadata.enrich(
            row = row,
            sessionVersion = 12,
            runtimeDeviceInfo = "ANDROID;MANUFACTURER=TEST;MODEL=EMULATOR;SDK=36"
        )

        assertEquals(7L, enriched.userId)
        assertEquals("ACTOR:7;SESSION_VERSION:12", enriched.sessionId)
        assertEquals("ANDROID;MANUFACTURER=TEST;MODEL=EMULATOR;SDK=36", enriched.deviceInfo)
        assertEquals(1_786_910_400_000L, enriched.eventAt)
        assertEquals("JOURNAL_ENTRY", enriched.entityType)
        assertEquals("JE-77", enriched.entityId)
        assertEquals("POST", enriched.action)
        assertEquals("ANDROID_APP", enriched.source)
        assertEquals("approved close", enriched.reason)
        assertEquals("status=DRAFT", enriched.oldValue)
        assertEquals("status=POSTED", enriched.newValue)
    }

    @Test
    fun `default android marker is upgraded to runtime device context`() {
        val row = AuditEventEntity(
            userId = 8,
            action = "UPDATE",
            entityType = "MASTER",
            entityId = "15"
        )

        val enriched = AuditEventMetadata.enrich(
            row = row,
            sessionVersion = 2,
            runtimeDeviceInfo = "ANDROID;MANUFACTURER=TEST;MODEL=PHONE;SDK=36"
        )

        assertEquals("ANDROID;MANUFACTURER=TEST;MODEL=PHONE;SDK=36", enriched.deviceInfo)
    }

    @Test
    fun `explicit source device and session are preserved`() {
        val row = AuditEventEntity(
            userId = 3,
            action = "IMPORT",
            entityType = "BANK_STATEMENT",
            entityId = "88",
            deviceInfo = "ANDROID_TEST_DEVICE",
            sessionId = "SESSION-EXPLICIT",
            source = "FILE_IMPORT"
        )

        val enriched = AuditEventMetadata.enrich(
            row = row,
            sessionVersion = 9,
            runtimeDeviceInfo = "ANDROID;MANUFACTURER=TEST;MODEL=OTHER;SDK=36"
        )

        assertEquals("ANDROID_TEST_DEVICE", enriched.deviceInfo)
        assertEquals("SESSION-EXPLICIT", enriched.sessionId)
        assertEquals("FILE_IMPORT", enriched.source)
    }

    @Test
    fun `unresolved session is explicit and never resembles a credential`() {
        val reference = AuditEventMetadata.sessionReference(actorUserId = 99, sessionVersion = null)

        assertEquals("ACTOR:99;SESSION:UNRESOLVED", reference)
        assertFalse(reference.contains("password", ignoreCase = true))
        assertFalse(reference.contains("token", ignoreCase = true))
        assertFalse(reference.contains("secret", ignoreCase = true))
        assertTrue(reference.contains("ACTOR:99"))
    }

    @Test
    fun `legacy migration marker is explicit`() {
        assertEquals("LEGACY_PRE_P1", AuditEventMetadata.LEGACY_VALUE)
    }
}
