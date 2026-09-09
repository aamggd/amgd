package com.fush.erp.domain

import kotlin.math.max
import kotlin.math.min

object SettlementAllocationMath {
    const val TOLERANCE = 1e-8

    data class InvoiceBalance(
        val invoiceId: Long,
        val outstandingBase: Double,
        val historicalRate: Double
    )

    data class Allocation(
        val invoiceId: Long,
        val amountOriginal: Double,
        val allocatedBase: Double
    )

    data class Plan(
        val allocations: List<Allocation>,
        val totalOriginal: Double,
        val totalAllocatedBase: Double
    )

    fun allocateOldest(totalOriginal: Double, balances: List<InvoiceBalance>): Plan {
        require(totalOriginal.isFinite() && totalOriginal > 0.0) { "مبلغ التسوية يجب أن يكون أكبر من صفر" }
        require(balances.isNotEmpty()) { "لا توجد فواتير مفتوحة للتسوية" }
        balances.forEach {
            require(it.outstandingBase.isFinite() && it.outstandingBase >= -TOLERANCE) { "رصيد فاتورة غير صالح" }
            require(it.historicalRate.isFinite() && it.historicalRate > 0.0) { "سعر الفاتورة التاريخي غير صالح" }
        }

        var remainingOriginal = totalOriginal
        val allocations = mutableListOf<Allocation>()
        for (balance in balances) {
            if (remainingOriginal <= TOLERANCE) break
            val outstandingBase = max(0.0, balance.outstandingBase)
            if (outstandingBase <= TOLERANCE) continue
            val outstandingOriginal = outstandingBase / balance.historicalRate
            val takeOriginal = min(remainingOriginal, outstandingOriginal)
            if (takeOriginal <= TOLERANCE) continue
            val allocatedBase = min(outstandingBase, takeOriginal * balance.historicalRate)
            allocations += Allocation(balance.invoiceId, takeOriginal, allocatedBase)
            remainingOriginal -= takeOriginal
        }

        require(remainingOriginal <= TOLERANCE) { "مبلغ التسوية يتجاوز إجمالي الفواتير المفتوحة بالعملة المحددة" }
        return Plan(
            allocations = allocations,
            totalOriginal = allocations.sumOf { it.amountOriginal },
            totalAllocatedBase = allocations.sumOf { it.allocatedBase }
        )
    }

    fun cashBase(totalOriginal: Double, currentRate: Double): Double {
        require(totalOriginal.isFinite() && totalOriginal > 0.0) { "مبلغ التسوية غير صالح" }
        require(currentRate.isFinite() && currentRate > 0.0) { "سعر الصرف الحالي غير صالح" }
        return totalOriginal * currentRate
    }

    fun fxDifference(cashBase: Double, allocatedBase: Double): Double = cashBase - allocatedBase
}
