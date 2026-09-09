package com.fush.erp.cloud

import android.content.Context
import androidx.room.withTransaction
import com.fush.erp.BuildConfig
import com.fush.erp.R
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.AuditEventEntity
import com.fush.erp.data.entity.SupplierPaymentAllocationEntity
import com.fush.erp.data.entity.SupplierPaymentEntity
import com.fush.erp.data.entity.TreasuryAccountEntity
import com.fush.erp.data.entity.UserEntity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * v163 mirrors the treasury-account directory and supplier-payment documents from OWNER/ADMIN.
 * It intentionally does not replay journal entries, generic party vouchers, bank reconciliation,
 * cash counts, FX revaluation or other treasury/accounting side effects.
 */
internal class SupplierPaymentsTreasuryCloudSyncEngine(
    private val context: Context,
    private val db: FushDatabase,
) {
    private val store = SupplierPaymentsTreasurySyncStore(context.applicationContext)

    fun lastSuccessAt(localUserId: Long): Long = store.lastSuccessAt(localUserId)
    fun lastConflicts(localUserId: Long): List<SupplierPaymentsTreasuryConflict> = store.conflicts(localUserId)

    suspend fun sync(
        localUser: UserEntity,
        session: CloudSession,
        cloudRole: String,
    ): CloudOperationResult<SupplierPaymentsTreasurySyncResult> {
        return try {
            val canPublish = true // v185: active organization membership authorizes transport
            var remote = fetchAll(session)
            val remotePaymentKeysBefore = remote.paymentKeys()
            val localPaymentKeysBefore = localPaymentKeys()
            var bootstrapped = false
            var uploaded = 0

            // Treasury rows act as the baseline marker, including companies with zero supplier payments.
            if (remote.treasuries.isEmpty()) {
                if (!canPublish) {
                    return CloudOperationResult.Failure(context.getString(R.string.cloud_supplier_payment_owner_bootstrap_required))
                }
                publishAll(session)
                remote = fetchAll(session)
                bootstrapped = true
                uploaded = localPaymentKeysBefore.size
            } else if (canPublish) {
                publishAll(session)
                remote = fetchAll(session)
                uploaded = (localPaymentKeysBefore - remotePaymentKeysBefore).size
            }

            val counters = Counters()
            val conflicts = mutableListOf<SupplierPaymentsTreasuryConflict>()
            applyTreasuries(localUser, remote, counters, conflicts)
            applyPayments(localUser, remote, counters, conflicts)

            if (!canPublish) {
                counters.skippedLocal += (localPaymentKeys() - remote.paymentKeys()).size
            }

            val completedAt = System.currentTimeMillis()
            store.saveConflicts(localUser.id, conflicts)
            store.markSuccess(localUser.id, completedAt)
            val result = SupplierPaymentsTreasurySyncResult(
                uploadedPayments = if (canPublish) uploaded else 0,
                downloadedPayments = counters.downloaded,
                unchangedPayments = counters.unchanged,
                conflicts = counters.conflicts,
                skippedLocalPayments = counters.skippedLocal,
                treasuryAccountsReady = counters.treasuryReady,
                bootstrappedCloud = bootstrapped,
                completedAtEpochMillis = completedAt,
                conflictDetails = conflicts,
            )

            if (result.downloadedPayments > 0 || result.conflicts > 0 || result.bootstrappedCloud) {
                db.governanceDao().insertAudit(
                    AuditEventEntity(
                        userId = localUser.id,
                        action = "CLOUD_SUPPLIER_PAYMENTS_TREASURY_SYNC",
                        entityType = "SYSTEM",
                        entityId = session.requireOrganizationId(),
                        newValue = "downloaded=${result.downloadedPayments};conflicts=${result.conflicts};treasuryReady=${result.treasuryAccountsReady};skippedLocal=${result.skippedLocalPayments}",
                        reason = if (result.bootstrappedCloud) "Supplier payments and treasury directory bootstrap" else "Supplier payments and treasury directory sync",
                        deviceInfo = "ANDROID_CLOUD_SYNC",
                    )
                )
            }
            CloudOperationResult.Success(result)
        } catch (error: Throwable) {
            CloudOperationResult.Failure(
                error.message?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.cloud_supplier_payment_sync_failed)
            )
        }
    }

    private suspend fun publishAll(session: CloudSession) {
        val org = session.requireOrganizationId()
        val userId = session.userId
        val purchaseDao = db.purchaseDao()
        val supplierById = db.supplierDao().allSuppliers().associateBy { it.id }
        val treasuryById = db.accountingDao().allTreasury().associateBy { it.id }
        val accountsById = db.accountDao().allActive().associateBy { it.id }
        val invoicesById = purchaseDao.allInvoicesForCloudSync().associateBy { it.id }
        val payments = purchaseDao.allSupplierPaymentsForCloudSync()
        val paymentNoById = payments.associate { it.id to it.paymentNo }

        val treasuryRows = mutableListOf<JSONObject>()
        db.accountingDao().allTreasury().forEach { treasury ->
            val ledgerCode = accountsById[treasury.accountId]?.code
                ?: db.accountDao().byId(treasury.accountId)?.code
                ?: error("Missing ledger account for treasury ${treasury.code}")
            treasuryRows += treasuryJson(treasury, ledgerCode, org, userId)
        }

        val paymentRows = mutableListOf<JSONObject>()
        val allocationRows = mutableListOf<JSONObject>()
        payments.forEach { payment ->
            val supplierCode = supplierById[payment.supplierId]?.code
                ?: error("Missing supplier for payment ${payment.paymentNo}")
            val treasuryCode = treasuryById[payment.treasuryAccountId]?.code
                ?: error("Missing treasury for payment ${payment.paymentNo}")
            val reversalNo = payment.reversalOfPaymentId?.let { paymentNoById[it] }
                ?: if (payment.reversalOfPaymentId == null) null else error("Missing source payment for reversal ${payment.paymentNo}")
            paymentRows += paymentJson(payment, supplierCode, treasuryCode, reversalNo, org, userId)
            purchaseDao.supplierPaymentAllocations(payment.id).forEachIndexed { index, allocation ->
                val invoiceNo = invoicesById[allocation.invoiceId]?.invoiceNo
                    ?: purchaseDao.invoiceById(allocation.invoiceId)?.invoiceNo
                    ?: error("Missing purchase invoice for payment allocation ${payment.paymentNo}")
                allocationRows += allocationJson(allocation, payment.paymentNo, index + 1, invoiceNo, org, userId)
            }
        }

        upsertBatch("fush_md_treasury_accounts", "organization_id,code", treasuryRows, session)
        upsertBatch("fush_tx_supplier_payments", "organization_id,payment_no", paymentRows, session)
        upsertBatch("fush_tx_supplier_payment_allocations", "organization_id,payment_no,allocation_no", allocationRows, session)
    }

    private suspend fun applyTreasuries(
        localUser: UserEntity,
        remote: RemoteData,
        counters: Counters,
        conflicts: MutableList<SupplierPaymentsTreasuryConflict>,
    ) {
        val dao = db.accountingDao()
        remote.treasuries.sortedBy { it.optString("code") }.forEach { row ->
            val code = row.getString("code")
            val existing = dao.treasuryByCode(code)
            if (existing == null) {
                val ledgerCode = row.getString("ledger_account_code")
                val ledger = db.accountDao().byCode(ledgerCode)
                if (ledger == null) {
                    counters.conflicts++
                    conflicts += SupplierPaymentsTreasuryConflict(
                        documentType = "TREASURY",
                        documentNo = code,
                        differences = listOf(SupplierPaymentsTreasuryConflictDifference("ledger_account_code", "missing locally", ledgerCode)),
                    )
                    return@forEach
                }
                dao.insertTreasury(
                    TreasuryAccountEntity(
                        code = code,
                        groupCode = row.optString("group_code", code).ifBlank { code },
                        nameAr = row.optString("name_ar"),
                        kind = row.optString("kind"),
                        accountId = ledger.id,
                        currencyCode = row.optString("currency_code", "YER_NEW"),
                        bankName = row.optString("bank_name"),
                        accountNumber = row.optString("account_number"),
                        isActive = row.optBoolean("is_active", true),
                        createdBy = localUser.id,
                        createdAt = row.optLong("created_at_ms", System.currentTimeMillis()),
                    )
                )
                counters.treasuryReady++
                return@forEach
            }

            val differences = treasuryDifferences(existing, row)
            if (differences.isEmpty()) {
                counters.treasuryReady++
            } else {
                counters.conflicts++
                conflicts += SupplierPaymentsTreasuryConflict("TREASURY", code, differences)
            }
        }
    }

    private suspend fun applyPayments(
        localUser: UserEntity,
        remote: RemoteData,
        counters: Counters,
        conflicts: MutableList<SupplierPaymentsTreasuryConflict>,
    ) {
        val dao = db.purchaseDao()
        val sorted = remote.payments.sortedWith(
            compareBy<JSONObject>({ !it.optNullableString("reversal_of_payment_no").isNullOrBlank() }, { it.optLong("payment_date_ms") }, { it.optString("payment_no") })
        )
        sorted.forEach { header ->
            val paymentNo = header.getString("payment_no")
            val existing = dao.supplierPaymentByNo(paymentNo)
            if (existing != null) {
                val differences = paymentDifferences(existing, header, remote)
                if (differences.isEmpty()) {
                    counters.unchanged++
                } else {
                    counters.conflicts++
                    conflicts += SupplierPaymentsTreasuryConflict("PAYMENT", paymentNo, differences)
                }
                return@forEach
            }

            db.withTransaction {
                val supplier = db.supplierDao().byCode(header.getString("supplier_code"))
                    ?: error("Missing master supplier ${header.getString("supplier_code")}")
                val treasury = db.accountingDao().treasuryByCode(header.getString("treasury_code"))
                    ?: error("Missing treasury ${header.getString("treasury_code")}")
                val reversalNo = header.optNullableString("reversal_of_payment_no")
                val reversalId = reversalNo?.let { sourceNo ->
                    dao.supplierPaymentByNo(sourceNo)?.id ?: error("Missing source supplier payment $sourceNo")
                }
                val paymentId = dao.insertSupplierPayment(
                    SupplierPaymentEntity(
                        paymentNo = paymentNo,
                        supplierId = supplier.id,
                        treasuryAccountId = treasury.id,
                        paymentDate = header.optLong("payment_date_ms"),
                        currencyCode = header.optString("currency_code"),
                        exchangeRate = header.optDouble("exchange_rate", 1.0),
                        amountOriginal = header.optDouble("amount_original", 0.0),
                        cashAmountBase = header.optDouble("cash_amount_base", 0.0),
                        notes = header.optString("notes"),
                        reversalOfPaymentId = reversalId,
                        createdBy = localUser.id,
                        createdAt = header.optLong("created_at_ms", System.currentTimeMillis()),
                    )
                )
                remote.allocations
                    .filter { it.optString("payment_no").equals(paymentNo, ignoreCase = true) }
                    .sortedBy { it.optInt("allocation_no") }
                    .forEach { allocation ->
                        val invoice = dao.invoiceByNo(allocation.getString("invoice_no"))
                            ?: error("Missing purchase invoice ${allocation.getString("invoice_no")}")
                        dao.insertSupplierPaymentAllocation(
                            SupplierPaymentAllocationEntity(
                                paymentId = paymentId,
                                invoiceId = invoice.id,
                                amountOriginal = allocation.optDouble("amount_original", 0.0),
                                allocatedBase = allocation.optDouble("allocated_base", 0.0),
                            )
                        )
                    }
            }
            counters.downloaded++
        }
    }

    private suspend fun treasuryDifferences(local: TreasuryAccountEntity, cloud: JSONObject): List<SupplierPaymentsTreasuryConflictDifference> {
        val localLedgerCode = db.accountDao().byId(local.accountId)?.code.orEmpty()
        return buildList {
            if (cloud.has("group_code")) stringDiff("group_code", local.groupCode.ifBlank { local.code }, cloud.optString("group_code"))?.let(::add)
            stringDiff("name_ar", local.nameAr, cloud.optString("name_ar"))?.let(::add)
            stringDiff("kind", local.kind, cloud.optString("kind"))?.let(::add)
            stringDiff("ledger_account_code", localLedgerCode, cloud.optString("ledger_account_code"))?.let(::add)
            stringDiff("currency_code", local.currencyCode, cloud.optString("currency_code"))?.let(::add)
            stringDiff("bank_name", local.bankName, cloud.optString("bank_name"))?.let(::add)
            stringDiff("account_number", local.accountNumber, cloud.optString("account_number"))?.let(::add)
            if (local.isActive != cloud.optBoolean("is_active", true)) {
                add(SupplierPaymentsTreasuryConflictDifference("is_active", local.isActive.toString(), cloud.optBoolean("is_active", true).toString()))
            }
        }
    }

    private suspend fun paymentDifferences(local: SupplierPaymentEntity, cloud: JSONObject, remote: RemoteData): List<SupplierPaymentsTreasuryConflictDifference> {
        val localSupplierCode = db.supplierDao().byId(local.supplierId)?.code.orEmpty()
        val localTreasuryCode = db.accountingDao().treasuryById(local.treasuryAccountId)?.code.orEmpty()
        val localReversalNo = local.reversalOfPaymentId?.let { db.purchaseDao().supplierPaymentById(it)?.paymentNo }.orEmpty()
        val remoteReversalNo = cloud.optNullableString("reversal_of_payment_no").orEmpty()
        return buildList {
            stringDiff("supplier_code", localSupplierCode, cloud.optString("supplier_code"))?.let(::add)
            stringDiff("treasury_code", localTreasuryCode, cloud.optString("treasury_code"))?.let(::add)
            longDiff("payment_date_ms", local.paymentDate, cloud.optLong("payment_date_ms"))?.let(::add)
            stringDiff("currency_code", local.currencyCode, cloud.optString("currency_code"))?.let(::add)
            doubleDiff("exchange_rate", local.exchangeRate, cloud.optDouble("exchange_rate", 1.0))?.let(::add)
            doubleDiff("amount_original", local.amountOriginal, cloud.optDouble("amount_original", 0.0))?.let(::add)
            doubleDiff("cash_amount_base", local.cashAmountBase, cloud.optDouble("cash_amount_base", 0.0))?.let(::add)
            stringDiff("notes", local.notes, cloud.optString("notes"))?.let(::add)
            stringDiff("reversal_of_payment_no", localReversalNo, remoteReversalNo)?.let(::add)
            val localAlloc = localAllocationsCanonical(local.id)
            val cloudAlloc = remoteAllocationsCanonical(local.paymentNo, remote)
            if (localAlloc != cloudAlloc) add(SupplierPaymentsTreasuryConflictDifference("allocations", localAlloc.ifBlank { "∅" }, cloudAlloc.ifBlank { "∅" }))
        }
    }

    private suspend fun localAllocationsCanonical(paymentId: Long): String = db.purchaseDao().supplierPaymentAllocations(paymentId)
        .map { allocation ->
            val invoiceNo = db.purchaseDao().invoiceById(allocation.invoiceId)?.invoiceNo.orEmpty()
            listOf(invoiceNo, formatNumber(allocation.amountOriginal), formatNumber(allocation.allocatedBase)).joinToString("|")
        }.sorted().joinToString(";")

    private fun remoteAllocationsCanonical(paymentNo: String, remote: RemoteData): String = remote.allocations
        .filter { it.optString("payment_no").equals(paymentNo, ignoreCase = true) }
        .map { row ->
            listOf(row.optString("invoice_no"), formatNumber(row.optDouble("amount_original", 0.0)), formatNumber(row.optDouble("allocated_base", 0.0))).joinToString("|")
        }.sorted().joinToString(";")

    private suspend fun localPaymentKeys(): Set<String> = db.purchaseDao().allSupplierPaymentsForCloudSync()
        .mapTo(linkedSetOf()) { it.paymentNo.uppercase() }

    private fun fetchAll(session: CloudSession): RemoteData = RemoteData(
        treasuries = fetchRows("fush_md_treasury_accounts", session),
        payments = fetchRows("fush_tx_supplier_payments", session),
        allocations = fetchRows("fush_tx_supplier_payment_allocations", session),
    )

    private fun fetchRows(table: String, session: CloudSession): List<JSONObject> {
        val org = encode(session.requireOrganizationId())
        val path = "/rest/v1/$table?select=*&organization_id=eq.$org&limit=10000"
        return when (val response = request("GET", path, null, session.accessToken, null)) {
            is HttpResult.Error -> {
                if (response.code == 404) error(context.getString(R.string.cloud_supplier_payment_schema_missing))
                error(apiErrorMessage(response))
            }
            is HttpResult.Ok -> {
                val array = runCatching { JSONArray(response.body) }.getOrElse {
                    error(context.getString(R.string.cloud_supplier_payment_invalid_response))
                }
                List(array.length()) { array.getJSONObject(it) }
            }
        }
    }

    private fun upsertBatch(table: String, conflictColumns: String, rows: List<JSONObject>, session: CloudSession) {
        if (rows.isEmpty()) return
        rows.chunked(150).forEach { chunk ->
            val payload = JSONArray().apply { chunk.forEach { put(it) } }.toString()
            val path = "/rest/v1/$table?on_conflict=${encodeConflictColumns(conflictColumns)}"
            when (val response = request("POST", path, payload, session.accessToken, "resolution=merge-duplicates,return=minimal")) {
                is HttpResult.Ok -> Unit
                is HttpResult.Error -> error(apiErrorMessage(response))
            }
        }
    }

    private fun treasuryJson(row: TreasuryAccountEntity, ledgerCode: String, org: String, userId: String) = JSONObject()
        .put("organization_id", org).put("code", row.code).put("group_code", row.groupCode.ifBlank { row.code }).put("name_ar", row.nameAr).put("kind", row.kind)
        .put("ledger_account_code", ledgerCode).put("currency_code", row.currencyCode).put("bank_name", row.bankName)
        .put("account_number", row.accountNumber).put("is_active", row.isActive).put("created_at_ms", row.createdAt).put("updated_by", userId)

    private fun paymentJson(row: SupplierPaymentEntity, supplierCode: String, treasuryCode: String, reversalNo: String?, org: String, userId: String) = JSONObject()
        .put("organization_id", org).put("payment_no", row.paymentNo).put("supplier_code", supplierCode).put("treasury_code", treasuryCode)
        .put("payment_date_ms", row.paymentDate).put("currency_code", row.currencyCode).put("exchange_rate", row.exchangeRate)
        .put("amount_original", row.amountOriginal).put("cash_amount_base", row.cashAmountBase).put("notes", row.notes)
        .put("reversal_of_payment_no", reversalNo ?: JSONObject.NULL).put("created_at_ms", row.createdAt).put("updated_by", userId)

    private fun allocationJson(row: SupplierPaymentAllocationEntity, paymentNo: String, allocationNo: Int, invoiceNo: String, org: String, userId: String) = JSONObject()
        .put("organization_id", org).put("payment_no", paymentNo).put("allocation_no", allocationNo).put("invoice_no", invoiceNo)
        .put("amount_original", row.amountOriginal).put("allocated_base", row.allocatedBase).put("updated_by", userId)

    private fun stringDiff(field: String, local: String, cloud: String): SupplierPaymentsTreasuryConflictDifference? =
        if (local == cloud) null else SupplierPaymentsTreasuryConflictDifference(field, local.ifBlank { "∅" }, cloud.ifBlank { "∅" })

    private fun longDiff(field: String, local: Long, cloud: Long): SupplierPaymentsTreasuryConflictDifference? =
        if (local == cloud) null else SupplierPaymentsTreasuryConflictDifference(field, local.toString(), cloud.toString())

    private fun doubleDiff(field: String, local: Double, cloud: Double): SupplierPaymentsTreasuryConflictDifference? =
        if (nearlyEqual(local, cloud)) null else SupplierPaymentsTreasuryConflictDifference(field, formatNumber(local), formatNumber(cloud))

    private fun nearlyEqual(left: Double, right: Double): Boolean {
        val scale = maxOf(1.0, kotlin.math.abs(left), kotlin.math.abs(right))
        return kotlin.math.abs(left - right) <= 0.000001 * scale
    }

    private fun formatNumber(value: Double): String = java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    private fun request(method: String, path: String, body: String?, bearerToken: String?, prefer: String?): HttpResult {
        val url = URL(BuildConfig.SUPABASE_URL.trimEnd('/') + path)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Accept", "application/json")
            if (!bearerToken.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $bearerToken")
            if (!prefer.isNullOrBlank()) setRequestProperty("Prefer", prefer)
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            }
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code in 200..299) HttpResult.Ok(code, responseBody) else HttpResult.Error(code, responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun apiErrorMessage(error: HttpResult.Error): String {
        val json = runCatching { JSONObject(error.body) }.getOrNull()
        return json?.optString("message")?.takeIf { it.isNotBlank() }
            ?: json?.optString("error_description")?.takeIf { it.isNotBlank() }
            ?: "Cloud API error (${error.code})"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun encodeConflictColumns(columns: String): String = columns.split(',').joinToString(",") { encode(it.trim()) }

    private fun JSONObject.optNullableString(name: String): String? =
        if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

    private sealed interface HttpResult {
        data class Ok(val code: Int, val body: String) : HttpResult
        data class Error(val code: Int, val body: String) : HttpResult
    }

    private data class RemoteData(
        val treasuries: List<JSONObject>,
        val payments: List<JSONObject>,
        val allocations: List<JSONObject>,
    ) {
        fun paymentKeys(): Set<String> = payments.mapTo(linkedSetOf()) { it.optString("payment_no").uppercase() }
    }

    private data class Counters(
        var downloaded: Int = 0,
        var unchanged: Int = 0,
        var conflicts: Int = 0,
        var skippedLocal: Int = 0,
        var treasuryReady: Int = 0,
    )
}
