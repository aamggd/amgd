package com.fush.erp.domain

import com.fush.erp.data.entity.SupplierItemMovementSummaryReportRow
import org.junit.Assert.assertEquals
import org.junit.Test

class SupplierItemMovementReportMathTest {
    @Test
    fun `same sku stays separated between supplier summaries`() {
        val rows = listOf(
            SupplierItemMovementSummaryReportRow(
                supplierId = 1L, supplierCode = "SUP-A", supplierName = "المورد A",
                itemId = 100L, code = "SKU-100", itemName = "صنف مشترك", baseUnitName = "قطعة",
                openingQtyBase = 0.0, purchasesQtyBase = 10.0, purchaseReturnsQtyBase = 0.0,
                salesQtyBase = 4.0, salesReturnsQtyBase = 1.0, otherNetQtyBase = 0.0,
                closingQtyBase = 7.0, closingValueBase = 700.0, attributionStatus = "EXACT"
            ),
            SupplierItemMovementSummaryReportRow(
                supplierId = 2L, supplierCode = "SUP-B", supplierName = "المورد B",
                itemId = 100L, code = "SKU-100", itemName = "صنف مشترك", baseUnitName = "قطعة",
                openingQtyBase = 0.0, purchasesQtyBase = 8.0, purchaseReturnsQtyBase = 2.0,
                salesQtyBase = 3.0, salesReturnsQtyBase = 0.0, otherNetQtyBase = 0.0,
                closingQtyBase = 3.0, closingValueBase = 330.0, attributionStatus = "EXACT"
            )
        )

        val report = SupplierItemMovementReportMath.buildTotals(rows)
        assertEquals(18.0, report.purchasesQtyBase, 1e-9)
        assertEquals(2.0, report.purchaseReturnsQtyBase, 1e-9)
        assertEquals(7.0, report.salesQtyBase, 1e-9)
        assertEquals(1.0, report.salesReturnsQtyBase, 1e-9)
        assertEquals(10.0, report.closingQtyBase, 1e-9)
        assertEquals(1030.0, report.closingValueBase, 1e-9)
        assertEquals(2, report.exactSummaryCount)
        assertEquals(0, report.unattributedSummaryCount)
    }

    @Test
    fun `unattributed history is counted explicitly and never promoted to exact supplier`() {
        val rows = listOf(
            SupplierItemMovementSummaryReportRow(
                supplierId = null, supplierCode = null, supplierName = "غير منسوب",
                itemId = 100L, code = "SKU-100", itemName = "صنف قديم", baseUnitName = "قطعة",
                openingQtyBase = 5.0, purchasesQtyBase = 0.0, purchaseReturnsQtyBase = 0.0,
                salesQtyBase = 2.0, salesReturnsQtyBase = 0.0, otherNetQtyBase = 0.0,
                closingQtyBase = 3.0, closingValueBase = 270.0, attributionStatus = "UNATTRIBUTED"
            )
        )
        val report = SupplierItemMovementReportMath.buildTotals(rows)
        assertEquals(0, report.exactSummaryCount)
        assertEquals(1, report.unattributedSummaryCount)
        assertEquals(3.0, report.unattributedClosingQtyBase, 1e-9)
    }
}
