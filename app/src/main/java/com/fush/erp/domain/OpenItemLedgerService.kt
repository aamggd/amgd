package com.fush.erp.domain

import com.fush.erp.data.FushDatabase
import kotlin.math.abs

data class AccountingOpenItem(
    val domain: String,
    val documentId: Long,
    val documentNo: String,
    val partyId: Long,
    val currencyCode: String,
    val openOriginal: Double,
    val openFunctionalBase: Double
)

data class OpenItemControlReconciliation(
    val domain: String,
    val openItemBase: Double,
    val controlGlBaseExcludingUnrealizedFx: Double,
    val varianceBase: Double
)

object OpenItemLedgerMath {
    private const val EPS = 0.01
    fun requireNoNegativeOpenItems(rows: List<AccountingOpenItem>) {
        rows.forEach {
            require(it.openOriginal >= -EPS && it.openFunctionalBase >= -EPS) {
                "NEGATIVE_OPEN_ITEM:${it.domain}:${it.documentId}"
            }
        }
    }
    fun requireControlReconciled(row: OpenItemControlReconciliation) {
        require(abs(row.varianceBase) <= EPS) { "OPEN_ITEM_CONTROL_MISMATCH:${row.domain}" }
    }
}

class OpenItemLedgerService(private val db: FushDatabase) {
    fun asOf(asOf: Long): List<AccountingOpenItem> {
        val sql = """
            SELECT 'AR',si.id,si.invoiceNo,si.customerId,si.currencyCode,
                   MAX(0.0, si.totalOriginal
                     - COALESCE((SELECT SUM((cra.amountBase + cra.discountBase) / NULLIF(si.exchangeRate,0))
                                 FROM customer_receipt_allocations cra JOIN customer_receipts cr ON cr.id=cra.receiptId
                                 WHERE cra.invoiceId=si.id AND cr.receiptDate <= $asOf),0)
                     - COALESCE((SELECT SUM(sr.totalOriginal) FROM sales_returns sr
                                 WHERE sr.salesInvoiceId=si.id AND sr.status='POSTED' AND sr.settlementType='CUSTOMER_CREDIT' AND sr.returnDate <= $asOf),0)) AS openOriginal,
                   MAX(0.0, si.totalBase
                     - COALESCE((SELECT SUM(cra.amountBase + cra.discountBase)
                                 FROM customer_receipt_allocations cra JOIN customer_receipts cr ON cr.id=cra.receiptId
                                 WHERE cra.invoiceId=si.id AND cr.receiptDate <= $asOf),0)
                     - COALESCE((SELECT SUM(sr.totalBase) FROM sales_returns sr
                                 WHERE sr.salesInvoiceId=si.id AND sr.status='POSTED' AND sr.settlementType='CUSTOMER_CREDIT' AND sr.returnDate <= $asOf),0)) AS openBase
            FROM sales_invoices si
            WHERE si.status='POSTED' AND si.invoiceDate <= $asOf
            UNION ALL
            SELECT 'AP',pi.id,pi.invoiceNo,pi.supplierId,pi.currencyCode,
                   MAX(0.0, pi.totalOriginal
                     - COALESCE((SELECT SUM(spa.amountOriginal)
                                 FROM supplier_payment_allocations spa JOIN supplier_payments sp ON sp.id=spa.paymentId
                                 WHERE spa.invoiceId=pi.id AND sp.paymentDate <= $asOf),0)
                     - COALESCE((SELECT SUM(pr.totalOriginal) FROM purchase_returns pr
                                 WHERE pr.purchaseInvoiceId=pi.id AND pr.status='POSTED' AND pr.settlementType='SUPPLIER_CREDIT' AND pr.returnDate <= $asOf),0)) AS openOriginal,
                   MAX(0.0, pi.totalBase
                     - COALESCE((SELECT SUM(spa.allocatedBase)
                                 FROM supplier_payment_allocations spa JOIN supplier_payments sp ON sp.id=spa.paymentId
                                 WHERE spa.invoiceId=pi.id AND sp.paymentDate <= $asOf),0)
                     - COALESCE((SELECT SUM(pr.totalBase) FROM purchase_returns pr
                                 WHERE pr.purchaseInvoiceId=pi.id AND pr.status='POSTED' AND pr.settlementType='SUPPLIER_CREDIT' AND pr.returnDate <= $asOf),0)) AS openBase
            FROM purchase_invoices pi
            WHERE pi.status='POSTED' AND pi.invoiceDate <= $asOf
        """.trimIndent()
        val result = db.openHelper.readableDatabase.query(sql).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val openBase = cursor.getDouble(6)
                    if (openBase > 0.000001) {
                        add(AccountingOpenItem(
                            cursor.getString(0), cursor.getLong(1), cursor.getString(2), cursor.getLong(3),
                            cursor.getString(4), cursor.getDouble(5), openBase
                        ))
                    }
                }
            }
        }
        OpenItemLedgerMath.requireNoNegativeOpenItems(result)
        return result
    }

    fun reconcileAsOf(asOf: Long): List<OpenItemControlReconciliation> {
        val items = asOf(asOf)
        val arOpen = items.filter { it.domain == "AR" }.sumOf { it.openFunctionalBase }
        val apOpen = items.filter { it.domain == "AP" }.sumOf { it.openFunctionalBase }
        fun gl(code: String, liability: Boolean): Double {
            val signed = if (liability) "jl.credit-jl.debit" else "jl.debit-jl.credit"
            val sql = """
                SELECT COALESCE(SUM($signed),0)
                FROM journal_lines jl
                JOIN journal_entries je ON je.id=jl.entryId
                JOIN accounts a ON a.id=jl.accountId
                WHERE a.code='$code' AND je.status='POSTED' AND je.entryDate <= $asOf
                  AND je.sourceType NOT IN ('FX_REVALUATION','FX_REVALUATION_REVERSAL')
            """.trimIndent()
            return db.openHelper.readableDatabase.query(sql).use { c -> check(c.moveToFirst()); c.getDouble(0) }
        }
        val arGl = gl("1300", false)
        val apGl = gl("2100", true)
        return listOf(
            OpenItemControlReconciliation("AR", arOpen, arGl, arOpen - arGl),
            OpenItemControlReconciliation("AP", apOpen, apGl, apOpen - apGl)
        )
    }
}
