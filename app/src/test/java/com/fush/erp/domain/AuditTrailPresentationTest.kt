package com.fush.erp.domain

import com.fush.erp.data.entity.AuditTrailRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditTrailPresentationTest {
    private fun row(userId: Long, display: String? = null, username: String? = null, action: String = "UPDATE") = AuditTrailRow(
        id = 1, eventAt = 1787536051000L, userId = userId,
        actorDisplayName = display, actorUsername = username,
        action = action, entityType = "CUSTOMER", entityId = "31",
        oldValue = "777", newValue = "782", reason = "تصحيح رقم الهاتف", deviceInfo = "",
        sectionCode = "SALES", referenceDisplay = "CUS-000031 — أرض الجنتين"
    )

    @Test fun humanActorShowsDisplayNameAndUsername() {
        assertEquals("أمجد — amgd", AuditTrailPresentation.actor(row(1, "أمجد", "amgd")))
    }

    @Test fun systemActorNeverShowsNumericZero() {
        assertEquals("النظام", AuditTrailPresentation.actor(row(0)))
    }

    @Test fun technicalActionsHaveFriendlyLabels() {
        assertEquals("تسجيل دخول ناجح", AuditTrailPresentation.actionLabel("LOGIN_SUCCESS"))
        assertEquals("تعديل", AuditTrailPresentation.actionLabel("UPDATE"))
        assertEquals("اعتماد تجاوز حد الكمية المجانية", AuditTrailPresentation.actionLabel("FREE_QTY_OVERRIDE_APPROVED"))
    }

    @Test fun dateAndTimeAreRenderedFromBusinessTimezone() {
        val r = row(1, "أمجد", "amgd")
        val date = AuditTrailPresentation.dateText(r.eventAt)
        assertTrue(date.contains("2026") || date.contains("٢٠٢٦"))
        assertTrue(AuditTrailPresentation.timeText(r.eventAt).isNotBlank())
    }
}
