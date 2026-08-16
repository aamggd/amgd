package com.fush.erp.domain

import com.fush.erp.data.entity.ExpenseReportRow
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpenseReportAnalyticsTest {
    @Test
    fun `build aggregates expense dimensions and attachments`() {
        val rows = listOf(
            row(1, 120.0, "6101", "نقل", "OPS", "تشغيل", "المبيعات", employee="أحمد", payment="نقد", attachments=2),
            row(2, 80.0, "6101", "نقل", "OPS", "تشغيل", "المبيعات", rep="محمد", payment="نقد", attachments=1),
            row(3, 100.0, "6201", "صيانة", "MNT", "صيانة", "الإنتاج", employee="أحمد", payment="بنك", attachments=0)
        )

        val result = ExpenseReportAnalyticsMath.build(rows)

        assertEquals(300.0, result.totalAmountBase, 0.0001)
        assertEquals(3, result.voucherCount)
        assertEquals(100.0, result.averageVoucherBase, 0.0001)
        assertEquals(3, result.attachmentCount)
        assertEquals(200.0, result.byAccount.first().amountBase, 0.0001)
        assertEquals("6101 — نقل", result.byAccount.first().label)
        assertEquals(2, result.byPaymentMethod.first { it.label == "نقد" }.voucherCount)
        assertEquals(200.0 / 300.0 * 100.0, result.byCostCenter.first { it.label == "OPS — تشغيل" }.sharePercent, 0.0001)
    }

    @Test
    fun `blank dimensions remain visible as unspecified`() {
        val result = ExpenseReportAnalyticsMath.build(listOf(row(1, 50.0, "", "", "", "", "")))
        assertEquals("غير محدد", result.byAccount.single().label)
        assertEquals("غير محدد", result.byCostCenter.single().label)
        assertEquals("غير محدد", result.byOrganizationUnit.single().label)
        assertEquals("غير محدد", result.byEmployeeOrRep.single().label)
        assertEquals("غير محدد", result.byPaymentMethod.single().label)
    }

    private fun row(
        id: Long,
        amount: Double,
        accountCode: String,
        accountName: String,
        costCenterCode: String,
        costCenterName: String,
        org: String,
        employee: String = "",
        rep: String = "",
        payment: String = "",
        attachments: Int = 0
    ) = ExpenseReportRow(
        expenseId=id, voucherId=id, voucherNo="EXP-$id", voucherDate=id,
        expenseAccountId=id, expenseAccountCode=accountCode, expenseAccountName=accountName,
        amountBase=amount, description="", currencyCode="YER", amountOriginal=amount,
        paymentMethod=payment, employeeId=null, employeeName=employee, salesRepId=null, salesRepName=rep,
        costCenterCode=costCenterCode, costCenterName=costCenterName, organizationUnit=org,
        referenceType="NONE", referenceId=null, referenceNo="", referenceLabel="",
        customerId=null, customerName="", supplierId=null, supplierName="", itemId=null, itemName="",
        attachmentCount=attachments
    )
}
