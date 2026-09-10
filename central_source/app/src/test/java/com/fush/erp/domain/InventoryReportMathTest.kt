package com.fush.erp.domain

import com.fush.erp.data.entity.InventoryActivityReportRow
import com.fush.erp.data.entity.InventoryExpiryLotReportRow
import com.fush.erp.data.entity.InventoryMovementDetailReportRow
import org.junit.Assert.assertEquals
import org.junit.Test

class InventoryReportMathTest {
    private val day = 86_400_000L
    private val asOf = 200L * day

    @Test
    fun activity_classifies_slow_and_never_issued_stock_without_inventing_outbound_date() {
        val slow = activity(lastOutbound = asOf - 120L * day, firstInbound = asOf - 150L * day)
        val neverIssued = activity(lastOutbound = null, firstInbound = asOf - 100L * day)

        assertEquals("بطيء 90+ يوم", InventoryReportMath.activity(slow, asOf).status)
        assertEquals(120L, InventoryReportMath.activity(slow, asOf).daysSinceLastOutbound)
        assertEquals("بدون صرف 90+ يوم", InventoryReportMath.activity(neverIssued, asOf).status)
        assertEquals(null, InventoryReportMath.activity(neverIssued, asOf).daysSinceLastOutbound)
    }

    @Test
    fun expiry_classifies_expired_near_and_later_lots() {
        assertEquals("منتهي", InventoryReportMath.expiry(expiry(asOf - day), asOf).status)
        assertEquals("ينتهي خلال 30 يوم", InventoryReportMath.expiry(expiry(asOf + 20L * day), asOf).status)
        assertEquals("ينتهي خلال 31–90 يوم", InventoryReportMath.expiry(expiry(asOf + 60L * day), asOf).status)
        assertEquals("أكثر من 90 يوم", InventoryReportMath.expiry(expiry(asOf + 120L * day), asOf).status)
    }

    @Test
    fun movement_summary_separates_inbound_and_outbound_values() {
        val rows = listOf(movement(10.0, 5.0), movement(-4.0, 5.0), movement(2.0, 7.0))
        val result = InventoryReportMath.movementSummary(rows)
        assertEquals(12.0, result.inboundQtyBase, 0.0001)
        assertEquals(4.0, result.outboundQtyBase, 0.0001)
        assertEquals(8.0, result.netQtyBase, 0.0001)
        assertEquals(64.0, result.inboundValueBase, 0.0001)
        assertEquals(20.0, result.outboundValueBase, 0.0001)
        assertEquals(3, result.movementCount)
    }

    private fun activity(lastOutbound: Long?, firstInbound: Long?) = InventoryActivityReportRow(
        itemId = 1, code = "I1", itemName = "صنف", baseUnitName = "وحدة",
        quantityBase = 10.0, inventoryValueBase = 100.0, firstInboundDate = firstInbound,
        lastMovementDate = asOf - day, lastOutboundDate = lastOutbound
    )

    private fun expiry(date: Long) = InventoryExpiryLotReportRow(
        warehouseName = "الرئيسي", itemId = 1, code = "I1", itemName = "صنف", baseUnitName = "وحدة",
        lotNo = "L1", expiryDate = date, quantityBase = 5.0, inventoryValueBase = 50.0
    )

    private fun movement(qty: Double, cost: Double) = InventoryMovementDetailReportRow(
        id = 1, movementDate = asOf, warehouseName = "الرئيسي", itemId = 1, code = "I1", itemName = "صنف",
        baseUnitName = "وحدة", movementType = "TEST", quantityBase = qty, unitCostBase = cost,
        movementValueBase = qty * cost, lotNo = null, expiryDate = null, referenceType = "TEST", referenceId = 1
    )
}
