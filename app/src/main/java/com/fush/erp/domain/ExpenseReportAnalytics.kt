package com.fush.erp.domain

import com.fush.erp.data.entity.ExpenseReportRow

data class ExpenseBreakdownRow(
    val label: String,
    val amountBase: Double,
    val voucherCount: Int,
    val sharePercent: Double
)

data class ExpenseReportAnalytics(
    val totalAmountBase: Double,
    val voucherCount: Int,
    val averageVoucherBase: Double,
    val attachmentCount: Int,
    val byAccount: List<ExpenseBreakdownRow>,
    val byCostCenter: List<ExpenseBreakdownRow>,
    val byOrganizationUnit: List<ExpenseBreakdownRow>,
    val byEmployeeOrRep: List<ExpenseBreakdownRow>,
    val byPaymentMethod: List<ExpenseBreakdownRow>
)

object ExpenseReportAnalyticsMath {
    fun build(rows: List<ExpenseReportRow>): ExpenseReportAnalytics {
        val total = rows.sumOf { it.amountBase }
        return ExpenseReportAnalytics(
            totalAmountBase = total,
            voucherCount = rows.size,
            averageVoucherBase = if (rows.isEmpty()) 0.0 else total / rows.size,
            attachmentCount = rows.sumOf { it.attachmentCount },
            byAccount = breakdown(rows, total) { row ->
                listOf(row.expenseAccountCode, row.expenseAccountName).filter { it.isNotBlank() }.joinToString(" — ").ifBlank { "غير محدد" }
            },
            byCostCenter = breakdown(rows, total) { row ->
                listOf(row.costCenterCode, row.costCenterName).filter { it.isNotBlank() }.joinToString(" — ").ifBlank { "غير محدد" }
            },
            byOrganizationUnit = breakdown(rows, total) { it.organizationUnit.ifBlank { "غير محدد" } },
            byEmployeeOrRep = breakdown(rows, total) { row ->
                when {
                    row.salesRepName.isNotBlank() -> "مندوب — ${row.salesRepName}"
                    row.employeeName.isNotBlank() -> "موظف — ${row.employeeName}"
                    else -> "غير محدد"
                }
            },
            byPaymentMethod = breakdown(rows, total) { it.paymentMethod.ifBlank { "غير محدد" } }
        )
    }

    private fun breakdown(
        rows: List<ExpenseReportRow>,
        total: Double,
        key: (ExpenseReportRow) -> String
    ): List<ExpenseBreakdownRow> = rows
        .groupBy(key)
        .map { (label, group) ->
            val amount = group.sumOf { it.amountBase }
            ExpenseBreakdownRow(
                label = label,
                amountBase = amount,
                voucherCount = group.size,
                sharePercent = if (kotlin.math.abs(total) < 0.000001) 0.0 else amount / total * 100.0
            )
        }
        .sortedWith(compareByDescending<ExpenseBreakdownRow> { it.amountBase }.thenBy { it.label })
}
