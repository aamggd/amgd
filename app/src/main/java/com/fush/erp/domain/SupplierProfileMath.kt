package com.fush.erp.domain

import com.fush.erp.data.entity.SupplierAgingRow
import com.fush.erp.data.entity.SupplierLedgerEventRow
import kotlin.math.abs

data class SupplierProfileSnapshot(
    val currentBase: Double,
    val days1To30Base: Double,
    val days31To60Base: Double,
    val days61To90Base: Double,
    val over90Base: Double,
    val invoiceOutstandingBase: Double,
    val nonInvoiceAdjustmentBase: Double,
    val statementBalanceBase: Double,
    val reconciliationDifferenceBase: Double
) {
    val totalLiabilityBase: Double
        get() = invoiceOutstandingBase + nonInvoiceAdjustmentBase

    val isReconciled: Boolean
        get() = abs(reconciliationDifferenceBase) <= 0.000001
}

object SupplierProfileMath {
    fun build(
        aging: SupplierAgingRow?,
        nonInvoiceAdjustmentBase: Double,
        events: List<SupplierLedgerEventRow>
    ): SupplierProfileSnapshot {
        require(nonInvoiceAdjustmentBase.isFinite()) { "تسوية المورد غير صالحة" }
        events.forEach {
            require(it.debitBase.isFinite() && it.creditBase.isFinite()) {
                "حركة كشف المورد تحتوي مبلغاً غير صالح"
            }
        }

        val invoiceOutstanding = aging?.totalOutstandingBase ?: 0.0
        val statementBalance = events.sumOf { it.creditBase - it.debitBase }
        val expectedLiability = invoiceOutstanding + nonInvoiceAdjustmentBase

        return SupplierProfileSnapshot(
            currentBase = aging?.currentBase ?: 0.0,
            days1To30Base = aging?.days1To30Base ?: 0.0,
            days31To60Base = aging?.days31To60Base ?: 0.0,
            days61To90Base = aging?.days61To90Base ?: 0.0,
            over90Base = aging?.over90Base ?: 0.0,
            invoiceOutstandingBase = invoiceOutstanding,
            nonInvoiceAdjustmentBase = nonInvoiceAdjustmentBase,
            statementBalanceBase = statementBalance,
            reconciliationDifferenceBase = statementBalance - expectedLiability
        )
    }
}
