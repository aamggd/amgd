package com.fush.erp.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fush.erp.domain.AccountingPrecision

@Entity(
    tableName = "journal_line_semantics",
    foreignKeys = [ForeignKey(
        entity = JournalLineEntity::class,
        parentColumns = ["id"],
        childColumns = ["journalLineId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("transactionCurrencyCode"), Index("branchCode"), Index("costCenterCode"), Index("projectCode"), Index("partyType", "partyId")]
)
data class JournalLineSemanticEntity(
    @PrimaryKey val journalLineId: Long,
    val lineNo: Int,
    val functionalDebitScaled: Long,
    val functionalCreditScaled: Long,
    val transactionCurrencyCode: String,
    val transactionDebitScaled: Long,
    val transactionCreditScaled: Long,
    val transactionExchangeRateScaled: Long = AccountingPrecision.RATE_SCALE,
    val branchCode: String = "",
    val costCenterCode: String = "",
    val projectCode: String = "",
    val partyType: String = "NONE",
    val partyId: Long? = null,
    val sourceReferenceType: String = "",
    val sourceReferenceId: String = ""
)
