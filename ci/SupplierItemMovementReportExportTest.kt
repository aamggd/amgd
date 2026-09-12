package com.fush.erp.ui.screens

import com.fush.erp.data.entity.SupplierItemMovementReportRow
import com.fush.erp.data.entity.SupplierItemMovementSummaryReportRow
import com.fush.erp.domain.SupplierItemMovementReportFilter
import com.fush.erp.domain.SupplierItemMovementReportMath
import com.fush.erp.domain.SupplierItemMovementReportResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupplierItemMovementReportExportTest {
    @Test
    fun exportPreservesExactSupplierAndUnattributedProvenance() {
        val summaries = listOf(
            SupplierItemMovementSummaryReportRow(
                supplierId = 1L,
                supplierCode = "SUP-A",
                supplierName = "المورد أ",
                itemId = 10L,
                code = "SKU-10",
                itemName = "الصنف المشترك",
                baseUnitName = "حبة",
                openingQtyBase = 10.0,
                purchasesQtyBase = 8.0,
                purchaseReturnsQtyBase = 1.0,
                salesQtyBase = 4.0,
                salesReturnsQtyBase = 1.0,
                otherNetQtyBase = 0.0,
                closingQtyBase = 14.0,
                closingValueBase = 1_400.0,
                attributionStatus = "EXACT"
            ),
            SupplierItemMovementSummaryReportRow(
                supplierId = null,
                supplierCode = null,
                supplierName = "غير منسوب",
                itemId = 10L,
                code = "SKU-10",
                itemName = "الصنف المشترك",
                baseUnitName = "حبة",
                openingQtyBase = 3.0,
                purchasesQtyBase = 0.0,
                purchaseReturnsQtyBase = 0.0,
                salesQtyBase = 1.0,
                salesReturnsQtyBase = 0.0,
                otherNetQtyBase = 0.0,
                closingQtyBase = 2.0,
                closingValueBase = 180.0,
                attributionStatus = "UNATTRIBUTED"
            )
        )
        val rows = listOf(
            SupplierItemMovementReportRow(
                eventDate = 2_000L,
                sourceId = 100L,
                supplierId = 1L,
                supplierCode = "SUP-A",
                supplierName = "المورد أ",
                itemId = 10L,
                code = "SKU-10",
                itemName = "الصنف المشترك",
                baseUnitName = "حبة",
                warehouseId = 7L,
                warehouseName = "المخزن الرئيسي",
                lotNo = "LOT-A",
                expiryDate = null,
                movementType = "SALE",
                referenceType = "SALES_LINE",
                referenceId = 501L,
                documentNo = "SI-501",
                quantityInBase = 0.0,
                quantityOutBase = 4.0,
                netQuantityBase = -4.0,
                unitCostBase = 100.0,
                inventoryValueDeltaBase = -400.0,
                attributionStatus = "EXACT"
            ),
            SupplierItemMovementReportRow(
                eventDate = 2_100L,
                sourceId = 101L,
                supplierId = null,
                supplierCode = null,
                supplierName = "غير منسوب",
                itemId = 10L,
                code = "SKU-10",
                itemName = "الصنف المشترك",
                baseUnitName = "حبة",
                warehouseId = 7L,
                warehouseName = "المخزن الرئيسي",
                lotNo = null,
                expiryDate = null,
                movementType = "SALE",
                referenceType = "SALES_LINE",
                referenceId = 502L,
                documentNo = "SI-502",
                quantityInBase = 0.0,
                quantityOutBase = 1.0,
                netQuantityBase = -1.0,
                unitCostBase = 90.0,
                inventoryValueDeltaBase = -90.0,
                attributionStatus = "UNATTRIBUTED"
            )
        )
        val result = SupplierItemMovementReportResult(
            filter = SupplierItemMovementReportFilter(
                fromDate = 1_000L,
                toDate = 3_000L
            ),
            rows = rows,
            summaries = summaries,
            totals = SupplierItemMovementReportMath.buildTotals(summaries)
        )

        val document = SupplierItemMovementReportExportFactory.build(
            result = result,
            periodLabel = "هذا الشهر",
            from = 1_000L,
            to = 3_000L,
            supplierLabel = "كل الموردين",
            itemLabel = "كل الأصناف",
            warehouseLabel = "كل المخازن"
        )

        assertEquals("تقرير حركة أصناف المورد — FAZ Solar ERP", document.title)
        assertTrue(document.summary.any { it.first == "الرصيد الافتتاحي" && it.second.contains("13") })
        assertTrue(document.summary.any { it.first == "الرصيد الختامي" && it.second.contains("16") })
        assertTrue(document.summary.any { it.first == "رصيد غير منسوب" && it.second.contains("2") })
        assertTrue(document.summary.any { it.first == "فلتر المورد" && it.second == "كل الموردين" })
        assertEquals(2, document.tables.size)
        assertTrue(document.tables.first().headers.contains("المورد"))
        assertTrue(document.tables.last().headers.contains("المستند"))
        assertTrue(document.tables.first().rows.any { row -> row.any { it.contains("المورد أ") } })
        assertTrue(document.tables.first().rows.any { row -> row.any { it.contains("غير منسوب") } })
        assertTrue(document.tables.last().rows.any { row -> row.any { it.contains("SI-501") } })
        assertTrue(document.notes.any { it.contains("غير منسوب") })
        assertTrue(document.notes.any { it.contains("لا يتم تخمين") })
    }
}
