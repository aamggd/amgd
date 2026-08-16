package com.fush.erp.domain

import androidx.room.withTransaction
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.NumberSequenceEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AutoNumberFormat {
    fun master(prefix: String, value: Long, width: Int): String =
        "$prefix-${value.toString().padStart(width, '0')}"

    fun document(prefix: String, date: String, value: Long): String =
        "$prefix-$date-${value.toString().padStart(4, '0')}"
}

/**
 * Pure sequence rules used by the database-backed allocator.
 * Values only move forward; a corrupted negative value or Long overflow is rejected
 * instead of resetting or wrapping the sequence.
 */
object AutoNumberSequencePolicy {
    fun next(lastValue: Long): Long {
        require(lastValue >= 0L) { "Sequence value cannot be negative" }
        check(lastValue < Long.MAX_VALUE) { "Sequence exhausted" }
        return lastValue + 1L
    }
}

/**
 * Central, database-backed numbering for master-data codes and operational documents.
 * The allocator enters a Room transaction itself, and therefore joins an existing
 * caller transaction when one is already active. This serializes read/increment/write
 * and prevents two concurrent callers from receiving the same committed value.
 *
 * The sequence row is never deleted or reset by this service. If an outer business
 * transaction fails, both the business row and the sequence increment roll back
 * together, so no committed/used code is recycled.
 */
class AutoNumberService(private val db: FushDatabase) {

    suspend fun nextSupplierCode(): String = nextMasterCode("SUP", "SUP", 6)

    suspend fun nextCustomerCode(): String = nextMasterCode("CUS", "CUS", 6)

    suspend fun nextUnitCode(): String = nextMasterCode("UNT", "UNT", 3)

    suspend fun nextWarehouseCode(): String = nextMasterCode("WH", "WH", 3)

    suspend fun nextItemCode(category: String): String {
        val prefix = when (category) {
            "RAW_MATERIAL" -> "RM"
            "PACKAGING" -> "PK"
            "FINISHED_GOOD" -> "FG"
            else -> "ITM"
        }
        return nextMasterCode("ITEM:$prefix", prefix, 6)
    }

    suspend fun nextDocumentNo(prefix: String, at: Long = System.currentTimeMillis()): String {
        val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(at))
        val key = "DOC:$prefix:$date"
        return AutoNumberFormat.document(prefix, date, nextValue(key))
    }

    private suspend fun nextMasterCode(sequenceKey: String, prefix: String, width: Int): String =
        AutoNumberFormat.master(prefix, nextValue("MASTER:$sequenceKey"), width)

    private suspend fun nextValue(key: String): Long = db.withTransaction {
        require(key.isNotBlank()) { "Sequence key is required" }
        val current = db.numberSequenceDao().byKey(key)?.lastValue ?: 0L
        val next = AutoNumberSequencePolicy.next(current)
        db.numberSequenceDao().upsert(
            NumberSequenceEntity(
                sequenceKey = key,
                lastValue = next,
                updatedAt = System.currentTimeMillis()
            )
        )
        val persisted = db.numberSequenceDao().byKey(key)?.lastValue
        check(persisted == next) { "Sequence reservation was not persisted" }
        next
    }
}
