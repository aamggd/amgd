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

    /**
     * Customer master codes are synchronized between phones. A cloud hydration can therefore
     * advance the real CUS-xxxxxx range without advancing this phone's local number_sequences
     * row. Reconcile against the actual customer table every time before allocating a code.
     */
    suspend fun nextCustomerCode(): String {
        val key = "MASTER:CUS"
        val sequenceValue = db.numberSequenceDao().byKey(key)?.lastValue ?: 0L
        val persistedCustomerValue = db.customerDao().maxAutomaticCodeValue()
        var next = maxOf(sequenceValue, persistedCustomerValue) + 1L
        while (true) {
            val candidate = AutoNumberFormat.master("CUS", next, 6)
            if (db.customerDao().byCode(candidate) == null) {
                db.numberSequenceDao().upsert(
                    NumberSequenceEntity(
                        sequenceKey = key,
                        lastValue = next,
                        updatedAt = com.fush.erp.domain.TrustedTimeService.now()
                    )
                )
                return candidate
            }
            next++
        }
    }

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

    suspend fun nextDocumentNo(prefix: String, at: Long = com.fush.erp.domain.TrustedTimeService.now()): String {
        val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(at))
        val key = "DOC:$prefix:$date"
        return AutoNumberFormat.document(prefix, date, nextValue(key))
    }

    /**
     * Shipment numbers are synchronized between phones. Cloud hydration can insert SHP-yyyyMMdd-NNNN
     * rows without advancing this phone's local number_sequences counter. Reconcile against the
     * persisted shipment table before allocating so a synced shipment can never collide locally.
     */
    suspend fun nextShipmentNo(at: Long = com.fush.erp.domain.TrustedTimeService.now()): String {
        val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(at))
        val sequenceKey = "DOC:SHP:$date"
        val numberPrefix = "SHP-$date-"
        val sequenceValue = db.numberSequenceDao().byKey(sequenceKey)?.lastValue ?: 0L
        val persistedShipmentValue = db.shipmentDao().maxAutomaticShipmentSequence(numberPrefix)
        var next = maxOf(sequenceValue, persistedShipmentValue) + 1L
        while (true) {
            val candidate = AutoNumberFormat.document("SHP", date, next)
            if (db.shipmentDao().shipmentByNo(candidate) == null) {
                db.numberSequenceDao().upsert(
                    NumberSequenceEntity(
                        sequenceKey = sequenceKey,
                        lastValue = next,
                        updatedAt = com.fush.erp.domain.TrustedTimeService.now()
                    )
                )
                return candidate
            }
            next++
        }
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
                updatedAt = com.fush.erp.domain.TrustedTimeService.now()
            )
        )
        return next
    }
}
