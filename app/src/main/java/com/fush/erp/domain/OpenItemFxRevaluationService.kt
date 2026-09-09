package com.fush.erp.domain

import androidx.room.withTransaction
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.JournalEntryEntity
import com.fush.erp.data.entity.JournalLineEntity
import kotlin.math.abs

data class OpenItemFxExposure(
    val domain: String,
    val currencyCode: String,
    val outstandingOriginal: Double,
    val carryingBase: Double
)

data class OpenItemFxResult(
    val domain: String,
    val currencyCode: String,
    val outstandingOriginal: Double,
    val carryingBase: Double,
    val closingRate: Double,
    val targetBase: Double,
    val deltaBase: Double,
    val journalEntryId: Long?
)

object OpenItemFxMath {
    fun revalue(exposure: OpenItemFxExposure, closingRate: Double): OpenItemFxResult {
        require(exposure.domain in setOf("AR", "AP")) { "OPEN_ITEM_DOMAIN_INVALID" }
        require(exposure.currencyCode.isNotBlank()) { "OPEN_ITEM_CURRENCY_REQUIRED" }
        require(exposure.outstandingOriginal >= -0.000001) { "OPEN_ITEM_ORIGINAL_NEGATIVE" }
        require(closingRate.isFinite() && closingRate > 0.0) { "OPEN_ITEM_CLOSING_RATE_INVALID" }
        val target = exposure.outstandingOriginal.coerceAtLeast(0.0) * closingRate
        return OpenItemFxResult(
            exposure.domain, exposure.currencyCode, exposure.outstandingOriginal.coerceAtLeast(0.0),
            exposure.carryingBase, closingRate, target, target - exposure.carryingBase, null
        )
    }
}

class OpenItemFxRevaluationService(private val db: FushDatabase) {
    suspend fun exposures(asOf: Long): List<OpenItemFxExposure> {
        val sql = """
            WITH ar AS (
                SELECT si.currencyCode,
                       SUM(MAX(0.0,
                           si.totalOriginal
                           - COALESCE((SELECT SUM((cra.amountBase + cra.discountBase) / NULLIF(si.exchangeRate,0))
                                      FROM customer_receipt_allocations cra
                                      JOIN customer_receipts cr ON cr.id=cra.receiptId
                                      WHERE cra.invoiceId=si.id AND cr.receiptDate <= $asOf),0)
                           - COALESCE((SELECT SUM(sr.totalOriginal) FROM sales_returns sr
                                      WHERE sr.salesInvoiceId=si.id AND sr.status='POSTED'
                                        AND sr.settlementType='CUSTOMER_CREDIT' AND sr.returnDate <= $asOf),0)
                       )) AS outstandingOriginal,
                       SUM(MAX(0.0,
                           si.totalBase
                           - COALESCE((SELECT SUM(cra.amountBase + cra.discountBase) FROM customer_receipt_allocations cra
                                      JOIN customer_receipts cr ON cr.id=cra.receiptId
                                      WHERE cra.invoiceId=si.id AND cr.receiptDate <= $asOf),0)
                           - COALESCE((SELECT SUM(sr.totalBase) FROM sales_returns sr
                                      WHERE sr.salesInvoiceId=si.id AND sr.status='POSTED'
                                        AND sr.settlementType='CUSTOMER_CREDIT' AND sr.returnDate <= $asOf),0)
                       )) AS carryingBase
                FROM sales_invoices si
                WHERE si.status='POSTED' AND si.invoiceDate <= $asOf AND si.currencyCode <> 'YER_NEW'
                GROUP BY si.currencyCode
            ), ap AS (
                SELECT pi.currencyCode,
                       SUM(MAX(0.0,
                           pi.totalOriginal
                           - COALESCE((SELECT SUM(spa.amountOriginal) FROM supplier_payment_allocations spa
                                      JOIN supplier_payments sp ON sp.id=spa.paymentId
                                      WHERE spa.invoiceId=pi.id AND sp.paymentDate <= $asOf),0)
                           - COALESCE((SELECT SUM(pr.totalOriginal) FROM purchase_returns pr
                                      WHERE pr.purchaseInvoiceId=pi.id AND pr.status='POSTED'
                                        AND pr.settlementType='SUPPLIER_CREDIT' AND pr.returnDate <= $asOf),0)
                       )) AS outstandingOriginal,
                       SUM(MAX(0.0,
                           pi.totalBase
                           - COALESCE((SELECT SUM(spa.allocatedBase) FROM supplier_payment_allocations spa
                                      JOIN supplier_payments sp ON sp.id=spa.paymentId
                                      WHERE spa.invoiceId=pi.id AND sp.paymentDate <= $asOf),0)
                           - COALESCE((SELECT SUM(pr.totalBase) FROM purchase_returns pr
                                      WHERE pr.purchaseInvoiceId=pi.id AND pr.status='POSTED'
                                        AND pr.settlementType='SUPPLIER_CREDIT' AND pr.returnDate <= $asOf),0)
                       )) AS carryingBase
                FROM purchase_invoices pi
                WHERE pi.status='POSTED' AND pi.invoiceDate <= $asOf AND pi.currencyCode <> 'YER_NEW'
                GROUP BY pi.currencyCode
            )
            SELECT 'AR',currencyCode,outstandingOriginal,carryingBase FROM ar WHERE outstandingOriginal > 0.000001
            UNION ALL
            SELECT 'AP',currencyCode,outstandingOriginal,carryingBase FROM ap WHERE outstandingOriginal > 0.000001
        """.trimIndent()
        return db.openHelper.readableDatabase.query(sql).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(OpenItemFxExposure(cursor.getString(0), cursor.getString(1), cursor.getDouble(2), cursor.getDouble(3)))
                }
            }
        }
    }

    suspend fun revalue(asOf: Long, createdBy: Long): List<OpenItemFxResult> = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.ACCOUNTING_POST)
        val period = db.accountingDao().periodForDate(asOf)
        require(period.status == "OPEN") { "ACCOUNTING_PERIOD_NOT_OPEN" }
        val gain = requireNotNull(db.accountDao().byCode("4250")) { "FX gain account 4250 missing" }
        val loss = requireNotNull(db.accountDao().byCode("6750")) { "FX loss account 6750 missing" }
        val ar = requireNotNull(db.accountDao().byCode("1300")) { "AR control 1300 missing" }
        val ap = requireNotNull(db.accountDao().byCode("2100")) { "AP control 2100 missing" }

        exposures(asOf).map { exposure ->
            val rate = requireNotNull(db.currencyDao().latestRateAt(exposure.currencyCode, asOf)) {
                "FX rate missing: ${exposure.currencyCode}"
            }.rateToBase
            val calc = OpenItemFxMath.revalue(exposure, rate)
            if (abs(calc.deltaBase) <= 0.01) return@map calc
            val sourceId = "OPEN_ITEM:${exposure.domain}:${exposure.currencyCode}:$asOf"
            db.journalDao().bySource("FX_REVALUATION", sourceId)?.let { existing ->
                return@map calc.copy(journalEntryId = existing.id)
            }
            val amount = abs(calc.deltaBase)
            val control = if (exposure.domain == "AR") ar else ap
            val lines = when {
                exposure.domain == "AR" && calc.deltaBase > 0 -> listOf(DraftJournalLine(control.id, amount, 0.0), DraftJournalLine(gain.id, 0.0, amount))
                exposure.domain == "AR" -> listOf(DraftJournalLine(loss.id, amount, 0.0), DraftJournalLine(control.id, 0.0, amount))
                calc.deltaBase > 0 -> listOf(DraftJournalLine(loss.id, amount, 0.0), DraftJournalLine(control.id, 0.0, amount))
                else -> listOf(DraftJournalLine(control.id, amount, 0.0), DraftJournalLine(gain.id, 0.0, amount))
            }
            AccountingValidator.validate(lines)
            val entryId = db.journalDao().insertEntry(
                JournalEntryEntity(
                    entryNo = "JE-FXOI-${exposure.domain}-${exposure.currencyCode}-$asOf",
                    entryDate = asOf,
                    description = "Open-item FX revaluation ${exposure.domain} ${exposure.currencyCode}",
                    currencyCode = exposure.currencyCode,
                    exchangeRate = rate,
                    sourceType = "FX_REVALUATION",
                    sourceId = sourceId,
                    createdBy = createdBy
                )
            )
            db.journalDao().insertLines(lines.map {
                JournalLineEntity(entryId=entryId, accountId=it.accountId, debit=it.debit, credit=it.credit, memo="open-item FX")
            })
            calc.copy(journalEntryId = entryId)
        }
    }
}
