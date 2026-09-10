package com.fush.erp.domain

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
 * Central, database-backed numbering for master-data codes and operational documents.
 * Callers invoke it from the same Room transaction that saves the business document,
 * so the sequence update rolls back when the save fails.
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

    private suspend fun nextValue(key: String): Long {
        val current = db.numberSequenceDao().byKey(key)?.lastValue ?: 0L
        val next = current + 1L
        db.numberSequenceDao().upsert(
            NumberSequenceEntity(
                sequenceKey = key,
                lastValue = next,
                updatedAt = System.currentTimeMillis()
            )
        )
        return next
    }
}
