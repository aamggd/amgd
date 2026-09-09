package com.fush.erp.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Configurable additional-charge master. The principal/agent decision is deliberately data-driven;
 * v179 does not hard-code IFRS 15 treatment by charge label.
 */
@Entity(
    tableName = "sales_additional_charge_types",
    indices = [
        Index(value = ["code"], unique = true),
        Index("isActive"),
        Index("recoverableAccountId"),
        Index("expenseAccountId"),
        Index("revenueAccountId"),
        Index("payableAccountId")
    ],
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["recoverableAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["expenseAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["revenueAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["payableAccountId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class AdditionalChargeTypeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val nameAr: String,
    val nameEn: String = "",
    /** CUSTOMER or COMPANY. */
    val defaultBearer: String = "CUSTOMER",
    /** RECOVERABLE, COMPANY_EXPENSE or SERVICE_REVENUE. */
    val defaultAccountingTreatment: String = "RECOVERABLE",
    /** REVIEW, PRINCIPAL or AGENT. This is an accounting-policy setting, not an inference. */
    val principalAgentMode: String = "REVIEW",
    val recoverableAccountId: Long,
    val expenseAccountId: Long,
    val revenueAccountId: Long,
    val payableAccountId: Long,
    val isActive: Boolean = true,
    val createdAt: Long = com.fush.erp.domain.TrustedTimeService.now()
)

@Entity(
    tableName = "sales_additional_charges",
    indices = [
        Index(value = ["chargeNo"], unique = true),
        Index("customerId"),
        Index("chargeTypeId"),
        Index("chargeDate"),
        Index("status"),
        Index("currencyCode"),
        Index("recoverableAccountId"),
        Index("expenseAccountId"),
        Index("revenueAccountId"),
        Index("payableAccountId")
    ],
    foreignKeys = [
        ForeignKey(entity = CustomerEntity::class, parentColumns = ["id"], childColumns = ["customerId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AdditionalChargeTypeEntity::class, parentColumns = ["id"], childColumns = ["chargeTypeId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CurrencyEntity::class, parentColumns = ["code"], childColumns = ["currencyCode"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["recoverableAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["expenseAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["revenueAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["payableAccountId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class SalesAdditionalChargeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chargeNo: String,
    val customerId: Long,
    val chargeTypeId: Long,
    val chargeDate: Long,
    val description: String = "",
    val amountOriginal: Double,
    val currencyCode: String,
    val exchangeRate: Double,
    val amountBase: Double,
    /** CUSTOMER or COMPANY. */
    val bearer: String,
    /** UNPAID, PARTIAL or PAID. Maintained from active payment rows. */
    val paymentStatus: String = "UNPAID",
    /** COMPANY or CUSTOMER_DIRECT. */
    val paidBy: String,
    /** RECOVERABLE, COMPANY_EXPENSE or SERVICE_REVENUE. */
    val accountingTreatment: String,
    /** Snapshot of the policy used for this historical transaction. */
    val principalAgentModeSnapshot: String,
    val recoverableAccountId: Long,
    val expenseAccountId: Long,
    val revenueAccountId: Long,
    val payableAccountId: Long,
    val status: String = "POSTED",
    val notes: String = "",
    val createdBy: Long,
    val createdAt: Long = com.fush.erp.domain.TrustedTimeService.now(),
    val cancelledBy: Long? = null,
    val cancelledAt: Long? = null,
    val cancellationReason: String = ""
)

@Entity(
    tableName = "sales_additional_charge_payments",
    indices = [
        Index(value = ["paymentNo"], unique = true),
        Index("chargeId"),
        Index("paymentDate"),
        Index("treasuryAccountId"),
        Index(value = ["reversalOfPaymentId"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(entity = SalesAdditionalChargeEntity::class, parentColumns = ["id"], childColumns = ["chargeId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = TreasuryAccountEntity::class, parentColumns = ["id"], childColumns = ["treasuryAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = SalesAdditionalChargePaymentEntity::class, parentColumns = ["id"], childColumns = ["reversalOfPaymentId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class SalesAdditionalChargePaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val paymentNo: String,
    val chargeId: Long,
    val paymentDate: Long,
    val amountOriginal: Double,
    val currencyCode: String,
    val exchangeRate: Double,
    val amountBase: Double,
    /** COMPANY or CUSTOMER_DIRECT. */
    val paidBy: String,
    val treasuryAccountId: Long? = null,
    val paymentReference: String = "",
    val notes: String = "",
    val reversalOfPaymentId: Long? = null,
    val createdBy: Long,
    val createdAt: Long = com.fush.erp.domain.TrustedTimeService.now()
)

@Entity(
    tableName = "sales_additional_charge_settlements",
    indices = [
        Index("chargeId"),
        Index("invoiceId"),
        Index(value = ["chargeId", "invoiceId"], unique = true),
        Index("status"),
        Index("settlementDate")
    ],
    foreignKeys = [
        ForeignKey(entity = SalesAdditionalChargeEntity::class, parentColumns = ["id"], childColumns = ["chargeId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = SalesInvoiceEntity::class, parentColumns = ["id"], childColumns = ["invoiceId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class SalesAdditionalChargeSettlementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chargeId: Long,
    val invoiceId: Long,
    /** Allocation in the charge's original currency. */
    val amountChargeOriginal: Double,
    /** Allocation translated to the invoice currency at the invoice historical rate. */
    val amountInvoiceOriginal: Double,
    val amountBase: Double,
    val settlementDate: Long,
    val status: String = "ACTIVE",
    val createdBy: Long,
    val createdAt: Long = com.fush.erp.domain.TrustedTimeService.now(),
    val reversedBy: Long? = null,
    val reversedAt: Long? = null,
    val reversalReason: String = ""
)

data class AdditionalChargeAvailableRow(
    val id: Long,
    val chargeNo: String,
    val chargeDate: Long,
    val customerId: Long,
    val chargeTypeId: Long,
    val chargeTypeName: String,
    val amountOriginal: Double,
    val currencyCode: String,
    val exchangeRate: Double,
    val amountBase: Double,
    val bearer: String,
    val paymentStatus: String,
    val paidBy: String,
    val accountingTreatment: String,
    val settledBase: Double,
    val remainingBase: Double
)

data class AdditionalChargeInvoiceDetailRow(
    val settlementId: Long,
    val chargeId: Long,
    val chargeNo: String,
    val chargeTypeName: String,
    val bearer: String,
    val accountingTreatment: String,
    val paidBy: String,
    val paymentStatus: String,
    val currencyCode: String,
    val chargeAmountOriginal: Double,
    val allocatedChargeOriginal: Double,
    val allocatedInvoiceOriginal: Double,
    val allocatedBase: Double,
    val paymentAccountName: String?,
    val lastPaymentDate: Long?,
    val lastPaymentReference: String?
)
