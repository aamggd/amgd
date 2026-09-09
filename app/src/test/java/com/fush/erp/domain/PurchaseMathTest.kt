package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PurchaseMathTest {
    @Test
    fun convertsPurchaseUnitToBaseUnit() {
        val line = PurchaseDraftLine(
            itemId = 1,
            unitId = 2,
            quantity = 2.0,
            factorToBase = 360.0,
            unitPriceOriginal = 42_000.0
        )
        assertEquals(720.0, line.baseQuantity, 0.0001)
        assertEquals(84_000.0, line.lineTotalOriginal, 0.0001)
    }

    @Test
    fun convertsForeignCurrencyTotalToBase() {
        assertEquals(155_462.0, PurchaseMath.toBaseAmount(100.0, 1554.62), 0.001)
    }

    @Test
    fun computesUnitCostInBaseUnit() {
        val line = PurchaseDraftLine(1, 2, 2.0, 10.0, 50.0)
        assertEquals(5.0, PurchaseMath.unitCostBase(line, 1.0), 0.0001)
    }

    @Test
    fun returnCannotExceedRemainingPurchasedQuantity() {
        assertThrows(IllegalArgumentException::class.java) {
            PurchaseMath.validateReturn(requestedQuantity = 7.0, purchasedQuantity = 10.0, alreadyReturned = 4.0)
        }
    }

    @Test
    fun comparesCurrentPurchasePriceWithPreviousPrice() {
        val variance = PurchaseMath.priceVariance(currentPrice = 19_000.0, previousPrice = 18_000.0)
        assertEquals(1_000.0, variance.amount, 0.0001)
        assertEquals(5.555555, variance.percent ?: 0.0, 0.0001)
    }

    @Test
    fun zeroPreviousPriceHasNoPercentageVariance() {
        val variance = PurchaseMath.priceVariance(currentPrice = 10.0, previousPrice = 0.0)
        assertEquals(10.0, variance.amount, 0.0001)
        assertEquals(null, variance.percent)
    }

    @Test
    fun validPartialReturnPasses() {
        PurchaseMath.validateReturn(requestedQuantity = 6.0, purchasedQuantity = 10.0, alreadyReturned = 4.0)
    }

    @Test
    fun partialReturnDraftAcceptsMultipleInvoiceLines() {
        PurchaseMath.validateReturnDraft(
            listOf(
                PurchaseReturnDraftLine(purchaseLineId = 11, quantity = 2.5),
                PurchaseReturnDraftLine(purchaseLineId = 12, quantity = 1.0)
            )
        )
    }

    @Test
    fun partialReturnDraftRejectsDuplicatePurchaseLine() {
        assertThrows(IllegalArgumentException::class.java) {
            PurchaseMath.validateReturnDraft(
                listOf(
                    PurchaseReturnDraftLine(purchaseLineId = 11, quantity = 1.0),
                    PurchaseReturnDraftLine(purchaseLineId = 11, quantity = 2.0)
                )
            )
        }
    }

    @Test
    fun partialReturnRejectsNonFiniteQuantity() {
        assertThrows(IllegalArgumentException::class.java) {
            PurchaseMath.validateReturn(Double.NaN, purchasedQuantity = 10.0, alreadyReturned = 0.0)
        }
    }
    @Test
    fun invoiceAdjustmentsComputeNetPayable() {
        assertEquals(1_120.0, PurchaseMath.invoiceTotalOriginal(1_000.0, 100.0, 120.0, 50.0, 50.0), 0.0001)
    }

    @Test
    fun invoiceDiscountCannotExceedSubtotal() {
        assertThrows(IllegalArgumentException::class.java) {
            PurchaseMath.invoiceTotalOriginal(1_000.0, 1_001.0, 0.0, 0.0, 0.0)
        }
    }

    @Test
    fun landedCostAllocatesDiscountAndChargesProportionally() {
        val first = PurchaseDraftLine(1, 1, 10.0, 1.0, 60.0) // 600 / 1000 of subtotal
        val second = PurchaseDraftLine(2, 1, 10.0, 1.0, 40.0) // 400 / 1000 of subtotal
        val total = PurchaseMath.invoiceTotalOriginal(1_000.0, 100.0, 120.0, 50.0, 30.0) // 1100
        val firstCost = PurchaseMath.allocatedUnitCostBase(first, 1.0, 1_000.0, total)
        val secondCost = PurchaseMath.allocatedUnitCostBase(second, 1.0, 1_000.0, total)
        assertEquals(66.0, firstCost, 0.0001)
        assertEquals(44.0, secondCost, 0.0001)
        assertEquals(total, firstCost * 10.0 + secondCost * 10.0, 0.0001)
    }

    @Test
    fun returnUsesAllocatedInventoryCost() {
        assertEquals(220.0, PurchaseMath.returnTotalOriginal(baseQuantity = 5.0, unitCostBase = 44.0, exchangeRate = 1.0), 0.0001)
    }

}
