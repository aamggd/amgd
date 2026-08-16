package com.fush.erp.domain

import androidx.room.withTransaction
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.JournalEntryEntity
import com.fush.erp.data.entity.JournalLineEntity
import com.fush.erp.data.entity.StockMovementEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class InventoryService(private val db: FushDatabase) {
    /**
     * Recomputes an inventory balance from the stock ledger for a historical cutoff.
     * No cached/stored quantity is consulted or mutated.
     */
    suspend fun balanceAt(warehouseId: Long, itemId: Long, asOf: Long): Double {
        require(asOf >= 0L) { "تاريخ رصيد المخزون غير صالح" }
        require(db.warehouseDao().allActive().any { it.id == warehouseId }) { "المخزن غير موجود" }
        require(db.itemDao().allActive().any { it.id == itemId }) { "الصنف غير موجود" }
        val result = db.stockDao().balanceAt(warehouseId, itemId, asOf)
        require(result.isFinite()) { "رصيد المخزون المحسوب غير صالح" }
        return if (kotlin.math.abs(result) <= StockLedgerInvariant.EPS) 0.0 else result
    }

    suspend fun postOpeningStock(
        warehouseId: Long,
        itemId: Long,
        quantityBase: Double,
        unitCostBase: Double,
        createdBy: Long,
        note: String = ""
    ): Long = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.INVENTORY_ADJUST)
        require(quantityBase > 0 && quantityBase.isFinite()) { "الكمية الافتتاحية يجب أن تكون أكبر من صفر" }
        require(unitCostBase > 0 && unitCostBase.isFinite()) { "تكلفة الوحدة الافتتاحية يجب أن تكون أكبر من صفر" }
        require(db.warehouseDao().allActive().any { it.id == warehouseId }) { "المخزن غير موجود" }
        require(db.itemDao().allActive().any { it.id == itemId }) { "الصنف غير موجود" }

        val inventory = requireNotNull(db.accountDao().byCode("1200")) { "حساب المخزون 1200 غير موجود" }
        val opening = requireNotNull(db.accountDao().byCode("3100")) { "حساب الرصيد الافتتاحي 3100 غير موجود" }
        val total = quantityBase * unitCostBase
        val draft = listOf(
            DraftJournalLine(inventory.id, total, 0.0),
            DraftJournalLine(opening.id, 0.0, total)
        )
        AccountingValidator.validate(draft)
        val now = System.currentTimeMillis()
        val docNo = documentNo("OPEN")
        val entryId = db.journalDao().insertEntry(
            JournalEntryEntity(
                entryNo = "JE-$docNo",
                entryDate = now,
                description = if (note.isBlank()) "رصيد افتتاحي للمخزون" else "رصيد افتتاحي: $note",
                currencyCode = "YER_NEW",
                exchangeRate = 1.0,
                sourceType = "OPENING_STOCK",
                sourceId = docNo,
                createdBy = createdBy
            )
        )
        db.journalDao().insertLines(
            draft.map { JournalLineEntity(entryId = entryId, accountId = it.accountId, debit = it.debit, credit = it.credit) }
        )
        db.stockDao().insertMovement(
            StockMovementEntity(
                movementDate = now,
                warehouseId = warehouseId,
                itemId = itemId,
                movementType = "OPENING",
                quantityBase = quantityBase,
                unitCostBase = unitCostBase,
                referenceType = "JOURNAL_ENTRY",
                referenceId = entryId
            )
        )
        entryId
    }

    private fun documentNo(prefix: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "$prefix-$stamp-${UUID.randomUUID().toString().take(6)}"
    }
}
