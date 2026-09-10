package com.fush.erp.domain.audit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditEventCatalogTest {
    @Test
    fun everySectionHasMandatoryEvents() {
        val covered = AuditEventCatalog.mandatory.map { it.section }.toSet()
        assertEquals(AuditSection.entries.toSet(), covered)
    }

    @Test
    fun eventCodesAreUniqueAndDefinitionsAreComplete() {
        val events = AuditEventCatalog.mandatory
        assertEquals(events.size, events.map { it.code }.distinct().size)
        assertTrue(events.all { it.code.isNotBlank() && it.entityType.isNotBlank() && it.action.isNotBlank() })
    }

    @Test
    fun destructiveBusinessCorrectionsRequireReasonAndOriginalLink() {
        val linkedActions = setOf("REVERSE", "CANCEL", "CORRECT", "UNMATCH", "REVOKE")
        val events = AuditEventCatalog.mandatory.filter { it.action in linkedActions }
        assertFalse(events.isEmpty())
        assertTrue(events.all { it.requiresReason })
        assertTrue(events.all { it.requiresOriginalLink })
    }

    @Test
    fun sensitiveConfigurationChangesCaptureBeforeAfterRequirement() {
        val sensitiveCodes = setOf(
            "SEC_PERMISSION_CHANGED",
            "SEC_ROLE_CHANGED",
            "SEC_POLICY_CHANGED",
            "MASTER_PRICE_CHANGED",
            "MASTER_CREDIT_LIMIT_CHANGED",
            "MASTER_UNIT_FACTOR_CHANGED",
            "SALES_PRICE_OVERRIDE",
            "PURCHASE_PRICE_OVERRIDE",
            "GL_BACKDATED_POST",
            "HR_COMPENSATION_CHANGED",
        )
        val selected = AuditEventCatalog.mandatory.filter { it.code in sensitiveCodes }
        assertEquals(sensitiveCodes, selected.map { it.code }.toSet())
        assertTrue(selected.all { it.requiresBeforeAfter && it.requiresReason })
    }

    @Test
    fun catalogNeverDefinesSecretPayloadFields() {
        val serializedNames = AuditEventCatalog.mandatory.flatMap {
            listOf(it.code, it.entityType, it.action)
        }.joinToString(" ").lowercase()
        assertFalse("password" in serializedNames)
        assertFalse("secret" in serializedNames)
        assertFalse("token" in serializedNames)
    }
}
