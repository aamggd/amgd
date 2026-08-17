package com.fush.erp.domain

import com.fush.erp.data.entity.TreasuryAccountEntity
import com.fush.erp.data.entity.TreasuryMovementReportRow
import org.junit.Assert.assertEquals
import org.junit.Test

class TreasuryReportMathTest {
    private val cash = TreasuryAccountEntity(id=1, code="CASH", nameAr="الصندوق", kind="CASH", accountId=101, createdBy=1)
    private val bank = TreasuryAccountEntity(id=2, code="BANK", nameAr="البنك", kind="BANK", accountId=102, createdBy=1)

    private fun movement(treasuryId: Long, date: Long, debit: Double=0.0, credit: Double=0.0, internal: Boolean=false, no: String="JV") =
        TreasuryMovementReportRow(treasuryId, if(treasuryId==1L) "CASH" else "BANK", if(treasuryId==1L) "الصندوق" else "البنك", if(treasuryId==1L) "CASH" else "BANK", "YER_NEW", "", "", date, no, date, no, if(internal) "TREASURY_TRANSFER" else "TREASURY_RECEIPT", debit, credit, internal)

    @Test fun `summary separates external flows from internal transfers`() {
        val rows = listOf(
            movement(1, 10, debit=100.0, no="OPEN"),
            movement(1, 30, debit=50.0, no="RV"),
            movement(1, 40, credit=20.0, no="PV"),
            movement(1, 50, credit=30.0, internal=true, no="TV"),
            movement(2, 50, debit=30.0, internal=true, no="TV")
        )
        val report = TreasuryReportMath.build(listOf(cash, bank), rows, 20, 60)
        assertEquals(100.0, report.openingBase, 0.0001)
        assertEquals(50.0, report.externalInBase, 0.0001)
        assertEquals(20.0, report.externalOutBase, 0.0001)
        assertEquals(30.0, report.transferInBase, 0.0001)
        assertEquals(30.0, report.transferOutBase, 0.0001)
        assertEquals(130.0, report.closingBase, 0.0001)
    }

    @Test fun `reversed transfer remains internal and does not inflate external cash flow`() {
        val rows = listOf(
            movement(1, 30, credit=40.0, internal=true, no="TV"),
            movement(2, 30, debit=40.0, internal=true, no="TV"),
            movement(1, 40, debit=40.0, internal=true, no="REV"),
            movement(2, 40, credit=40.0, internal=true, no="REV")
        )
        val report = TreasuryReportMath.build(listOf(cash, bank), rows, 20, 50)
        assertEquals(0.0, report.externalInBase, 0.0001)
        assertEquals(0.0, report.externalOutBase, 0.0001)
        assertEquals(80.0, report.transferInBase, 0.0001)
        assertEquals(80.0, report.transferOutBase, 0.0001)
        assertEquals(0.0, report.closingBase, 0.0001)
    }
}
