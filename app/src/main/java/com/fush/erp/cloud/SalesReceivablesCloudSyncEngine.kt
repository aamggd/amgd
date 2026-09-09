package com.fush.erp.cloud

import android.content.Context
import androidx.room.withTransaction
import com.fush.erp.BuildConfig
import com.fush.erp.R
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.YemenGeographyHierarchy
import com.fush.erp.data.entity.AuditEventEntity
import com.fush.erp.data.entity.CustomerReceiptAllocationEntity
import com.fush.erp.data.entity.CustomerReceiptEntity
import com.fush.erp.data.entity.SalesAllocationEntity
import com.fush.erp.data.entity.SalesInvoiceEntity
import com.fush.erp.data.entity.SalesLineEntity
import com.fush.erp.data.entity.SalesReturnEntity
import com.fush.erp.data.entity.SalesReturnLineEntity
import com.fush.erp.data.entity.UserEntity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * v159 deliberately treats posted Sales & Receivables documents as an OWNER/ADMIN-authored
 * company mirror. Every organization member can download the mirror, while only OWNER/ADMIN
 * can publish it. This avoids silently replaying inventory/accounting side effects on another
 * phone before those transaction domains receive their own cloud-sync wave.
 */
internal class SalesReceivablesCloudSyncEngine(
    private val context: Context,
    private val db: FushDatabase,
) {
    private val store = SalesReceivablesSyncStore(context.applicationContext)

    fun lastSuccessAt(localUserId: Long): Long = store.lastSuccessAt(localUserId)
    fun lastConflicts(localUserId: Long): List<SalesReceivablesConflict> = store.conflicts(localUserId)

    suspend fun sync(
        localUser: UserEntity,
        session: CloudSession,
        cloudRole: String,
    ): CloudOperationResult<SalesReceivablesSyncResult> {
        return try {
            val canPublish = true // v185: active organization membership authorizes transport
            var remote = fetchAll(session)
            val remoteKeysBeforePublish = remote.documentKeys()
            val localKeysBeforePublish = localDocumentKeys()
            var bootstrapped = false
            var publishedDocuments = 0

            if (remote.documentCount() == 0) {
                if (!canPublish) {
                    return CloudOperationResult.Failure(
                        context.getString(R.string.cloud_sales_owner_bootstrap_required)
                    )
                }
                publishAll(session)
                remote = fetchAll(session)
                bootstrapped = true
                publishedDocuments = localKeysBeforePublish.size
            } else if (canPublish) {
                // Posted sales/receivables documents are immutable business records. Re-upserting
                // the authoritative owner snapshot is idempotent and publishes newly-created docs.
                publishAll(session)
                remote = fetchAll(session)
                publishedDocuments = (localKeysBeforePublish - remoteKeysBeforePublish).size
            }

            val counters = Counters()
            val conflictDetails = mutableListOf<SalesReceivablesConflict>()
            applyInvoices(localUser, remote, counters, conflictDetails)
            applyReceipts(localUser, remote, counters, conflictDetails)
            applyReturns(localUser, remote, counters, conflictDetails)

            if (!canPublish) {
                val localKeys = localDocumentKeys()
                val remoteKeys = remote.documentKeys()
                counters.skippedLocal += (localKeys - remoteKeys).size
            }

            store.markBaselineComplete(localUser.id)
            val completedAt = System.currentTimeMillis()
            store.saveConflicts(localUser.id, conflictDetails)
            store.markSuccess(localUser.id, completedAt)

            val result = SalesReceivablesSyncResult(
                uploadedDocuments = if (canPublish) publishedDocuments else 0,
                downloadedDocuments = counters.downloaded,
                unchangedDocuments = counters.unchanged,
                conflicts = counters.conflicts,
                skippedLocalDocuments = counters.skippedLocal,
                bootstrappedCloud = bootstrapped,
                completedAtEpochMillis = completedAt,
                conflictDetails = conflictDetails,
            )

            if (result.downloadedDocuments > 0 || result.conflicts > 0 || result.bootstrappedCloud) {
                db.governanceDao().insertAudit(
                    AuditEventEntity(
                        userId = localUser.id,
                        action = "CLOUD_SALES_RECEIVABLES_SYNC",
                        entityType = "SYSTEM",
                        entityId = session.requireOrganizationId(),
                        newValue = "downloaded=${result.downloadedDocuments};conflicts=${result.conflicts};skippedLocal=${result.skippedLocalDocuments}",
                        reason = if (result.bootstrappedCloud) "Sales/receivables cloud mirror bootstrap" else "Sales/receivables cloud mirror sync",
                        deviceInfo = "ANDROID_CLOUD_SYNC",
                    )
                )
            }
            CloudOperationResult.Success(result)
        } catch (error: Throwable) {
            CloudOperationResult.Failure(
                error.message?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.cloud_sales_sync_failed)
            )
        }
    }

    private suspend fun publishAll(session: CloudSession) {
        val org = session.requireOrganizationId()
        val userId = session.userId
        val salesDao = db.salesDao()
        val customerById = db.customerDao().allCustomers().associateBy { it.id }
        val warehouseById = db.warehouseDao().allRows().associateBy { it.id }
        val itemById = db.itemDao().allRows().associateBy { it.id }
        val unitById = db.unitDao().allRows().associateBy { it.id }
        val invoices = salesDao.allInvoicesForCloudSync()
        val invoiceNoById = invoices.associate { it.id to it.invoiceNo }
        val receipts = salesDao.allReceiptsForCloudSync()
        val receiptNoById = receipts.associate { it.id to it.receiptNo }

        val invoiceHeaders = mutableListOf<JSONObject>()
        val invoiceLines = mutableListOf<JSONObject>()
        val allocations = mutableListOf<JSONObject>()
        val invoiceLineNoByLocalId = mutableMapOf<Long, Int>()

        for (invoice in invoices) {
            val customerCode = customerById[invoice.customerId]?.code
                ?: error("Missing customer for invoice ${invoice.invoiceNo}")
            val warehouseCode = warehouseById[invoice.warehouseId]?.code
                ?: error("Missing warehouse for invoice ${invoice.invoiceNo}")
            invoiceHeaders += invoiceJson(invoice, customerCode, warehouseCode, org, userId)
            val lines = salesDao.linesForInvoice(invoice.id)
            lines.forEachIndexed { index, line ->
                val lineNo = index + 1
                invoiceLineNoByLocalId[line.id] = lineNo
                val itemCode = itemById[line.itemId]?.code ?: error("Missing item for invoice ${invoice.invoiceNo}")
                val unitCode = unitById[line.unitId]?.code ?: error("Missing unit for invoice ${invoice.invoiceNo}")
                invoiceLines += lineJson(line, invoice.invoiceNo, lineNo, itemCode, unitCode, org, userId)
                salesDao.allocationsForLine(line.id).forEachIndexed { allocationIndex, allocation ->
                    allocations += allocationJson(
                        allocation, invoice.invoiceNo, lineNo, allocationIndex + 1,
                        itemCode, org, userId
                    )
                }
            }
        }

        val receiptHeaders = mutableListOf<JSONObject>()
        val receiptAllocations = mutableListOf<JSONObject>()
        for (receipt in receipts) {
            val customerCode = customerById[receipt.customerId]?.code
                ?: error("Missing customer for receipt ${receipt.receiptNo}")
            val reversalNo = receipt.reversalOfReceiptId?.let(receiptNoById::get)
            receiptHeaders += receiptJson(receipt, customerCode, reversalNo, org, userId)
            salesDao.receiptAllocations(receipt.id).forEachIndexed { index, allocation ->
                val invoiceNo = invoiceNoById[allocation.invoiceId]
                    ?: error("Missing invoice for receipt ${receipt.receiptNo}")
                receiptAllocations += receiptAllocationJson(
                    allocation, receipt.receiptNo, index + 1, invoiceNo, org, userId
                )
            }
        }

        val returnHeaders = mutableListOf<JSONObject>()
        val returnLines = mutableListOf<JSONObject>()
        for (salesReturn in salesDao.allReturnsForCloudSync()) {
            val invoiceNo = invoiceNoById[salesReturn.salesInvoiceId]
                ?: error("Missing invoice for return ${salesReturn.returnNo}")
            val customerCode = customerById[salesReturn.customerId]?.code
                ?: error("Missing customer for return ${salesReturn.returnNo}")
            val warehouseCode = warehouseById[salesReturn.warehouseId]?.code
                ?: error("Missing warehouse for return ${salesReturn.returnNo}")
            returnHeaders += returnJson(salesReturn, invoiceNo, customerCode, warehouseCode, org, userId)
            salesDao.returnLinesForReturn(salesReturn.id).forEachIndexed { index, line ->
                val salesInvoiceLineNo = invoiceLineNoByLocalId[line.salesLineId]
                    ?: error("Missing source sales line for return ${salesReturn.returnNo}")
                val itemCode = itemById[line.itemId]?.code ?: error("Missing item for return ${salesReturn.returnNo}")
                val unitCode = unitById[line.unitId]?.code ?: error("Missing unit for return ${salesReturn.returnNo}")
                returnLines += returnLineJson(
                    line, salesReturn.returnNo, index + 1, salesInvoiceLineNo,
                    itemCode, unitCode, org, userId
                )
            }
        }

        upsertBatch("fush_tx_sales_invoices", "organization_id,invoice_no", invoiceHeaders, session)
        upsertBatch("fush_tx_sales_lines", "organization_id,invoice_no,line_no", invoiceLines, session)
        upsertBatch("fush_tx_sales_allocations", "organization_id,invoice_no,line_no,allocation_no", allocations, session)
        upsertBatch("fush_tx_customer_receipts", "organization_id,receipt_no", receiptHeaders, session)
        upsertBatch("fush_tx_customer_receipt_allocations", "organization_id,receipt_no,allocation_no", receiptAllocations, session)
        upsertBatch("fush_tx_sales_returns", "organization_id,return_no", returnHeaders, session)
        upsertBatch("fush_tx_sales_return_lines", "organization_id,return_no,line_no", returnLines, session)
    }

    private suspend fun applyInvoices(
        localUser: UserEntity,
        remote: RemoteData,
        counters: Counters,
        conflictDetails: MutableList<SalesReceivablesConflict>,
    ) {
        val salesDao = db.salesDao()
        for (header in remote.invoices.sortedBy { it.optLong("invoice_date_ms") }) {
            val invoiceNo = header.getString("invoice_no")
            val existing = salesDao.invoiceByNo(invoiceNo)
            if (existing != null) {
                val differences = invoiceDifferences(existing, header, remote)
                if (differences.isEmpty()) {
                    counters.unchanged++
                } else {
                    counters.conflicts++
                    conflictDetails += SalesReceivablesConflict(
                        documentType = "INVOICE",
                        documentNo = invoiceNo,
                        differences = differences.take(MAX_CONFLICT_DIFFERENCES),
                    )
                }
                continue
            }
            db.withTransaction {
                val customer = db.customerDao().byCode(header.getString("customer_code"))
                    ?: error("Missing master customer ${header.getString("customer_code")}")
                val warehouse = db.warehouseDao().byCode(header.getString("warehouse_code"))
                    ?: error("Missing master warehouse ${header.getString("warehouse_code")}")
                val invoiceId = salesDao.insertInvoice(
                    SalesInvoiceEntity(
                        invoiceNo = invoiceNo,
                        customerId = customer.id,
                        invoiceDate = header.optLong("invoice_date_ms"),
                        dueDate = header.optNullableLong("due_date_ms"),
                        warehouseId = warehouse.id,
                        currencyCode = header.optString("currency_code"),
                        exchangeRate = header.optDouble("exchange_rate", 1.0),
                        paymentType = header.optString("payment_type"),
                        channel = header.optString("channel"),
                        province = header.optString("province"),
                        governorateId = header.optNullableString("governorate_id")
                            ?: db.geographyDao().governorateIdForAlias(YemenGeographyHierarchy.normalizeAlias(header.optString("province"))),
                        districtId = header.optNullableString("district_id"),
                        areaId = header.optNullableString("area_id"),
                        salesRepId = null,
                        salesRepNameSnapshot = header.optString("sales_rep_name_snapshot"),
                        salesRepRatePct = header.optDouble("sales_rep_rate_pct", 0.0),
                        freeQtyLimitPctSnapshot = header.optDouble("free_qty_limit_pct_snapshot", 0.0),
                        freeQtyApprovedBy = null,
                        freeQtyApprovalReason = header.optString("free_qty_approval_reason"),
                        discountPct = header.optDouble("discount_pct", 0.0),
                        grossOriginal = header.optDouble("gross_original", 0.0),
                        discountOriginal = header.optDouble("discount_original", 0.0),
                        transportOriginal = header.optDouble("transport_original", 0.0),
                        feesOriginal = header.optDouble("fees_original", 0.0),
                        riskMarginOriginal = header.optDouble("risk_margin_original", 0.0),
                        totalOriginal = header.optDouble("total_original", 0.0),
                        totalBase = header.optDouble("total_base", 0.0),
                        treasuryAccountId = null,
                        status = header.optString("status", "POSTED"),
                        belowFloorApprovedBy = null,
                        belowFloorReason = header.optString("below_floor_reason"),
                        notes = header.optString("notes"),
                        createdBy = localUser.id,
                        createdAt = header.optLong("created_at_ms", System.currentTimeMillis()),
                    )
                )
                val lineIdByNo = mutableMapOf<Int, Long>()
                remote.lines.filter { it.optString("invoice_no") == invoiceNo }
                    .sortedBy { it.optInt("line_no") }
                    .forEach { row ->
                        val item = db.itemDao().byCode(row.getString("item_code"))
                            ?: error("Missing master item ${row.getString("item_code")}")
                        val unit = db.unitDao().byCode(row.getString("unit_code"))
                            ?: error("Missing master unit ${row.getString("unit_code")}")
                        val localLineId = salesDao.insertLine(
                            SalesLineEntity(
                                invoiceId = invoiceId,
                                itemId = item.id,
                                unitId = unit.id,
                                quantity = row.optDouble("quantity", 0.0),
                                freeQuantity = row.optDouble("free_quantity", 0.0),
                                factorToBase = row.optDouble("factor_to_base", 1.0),
                                baseQuantity = row.optDouble("base_quantity", 0.0),
                                freeBaseQuantity = row.optDouble("free_base_quantity", 0.0),
                                unitPriceOriginal = row.optDouble("unit_price_original", 0.0),
                                grossOriginal = row.optDouble("gross_original", 0.0),
                                discountOriginal = row.optDouble("discount_original", 0.0),
                                netOriginal = row.optDouble("net_original", 0.0),
                            )
                        )
                        lineIdByNo[row.optInt("line_no")] = localLineId
                    }
                remote.allocations.filter { it.optString("invoice_no") == invoiceNo }
                    .sortedWith(compareBy({ it.optInt("line_no") }, { it.optInt("allocation_no") }))
                    .forEach { row ->
                        val lineId = lineIdByNo[row.optInt("line_no")]
                            ?: error("Missing local line for invoice $invoiceNo")
                        val item = db.itemDao().byCode(row.getString("item_code"))
                            ?: error("Missing master item ${row.getString("item_code")}")
                        salesDao.insertAllocation(
                            SalesAllocationEntity(
                                salesLineId = lineId,
                                itemId = item.id,
                                lotNo = row.optNullableString("lot_no"),
                                expiryDate = row.optNullableLong("expiry_date_ms"),
                                quantityBase = row.optDouble("quantity_base", 0.0),
                                freeQuantityBase = row.optDouble("free_quantity_base", 0.0),
                                unitCostBase = row.optDouble("unit_cost_base", 0.0),
                                costBase = row.optDouble("cost_base", 0.0),
                            )
                        )
                    }
            }
            counters.downloaded++
        }
    }

    private suspend fun applyReceipts(
        localUser: UserEntity,
        remote: RemoteData,
        counters: Counters,
        conflictDetails: MutableList<SalesReceivablesConflict>,
    ) {
        val salesDao = db.salesDao()
        for (header in remote.receipts.sortedWith(compareBy({ it.optLong("receipt_date_ms") }, { it.optString("receipt_no") }))) {
            val receiptNo = header.getString("receipt_no")
            val existing = salesDao.receiptByNo(receiptNo)
            if (existing != null) {
                val differences = receiptDifferences(existing, header, remote)
                if (differences.isEmpty()) {
                    counters.unchanged++
                } else {
                    counters.conflicts++
                    conflictDetails += SalesReceivablesConflict(
                        documentType = "RECEIPT",
                        documentNo = receiptNo,
                        differences = differences.take(MAX_CONFLICT_DIFFERENCES),
                    )
                }
                continue
            }
            db.withTransaction {
                val customer = db.customerDao().byCode(header.getString("customer_code"))
                    ?: error("Missing master customer ${header.getString("customer_code")}")
                val reversalOf = header.optNullableString("reversal_of_receipt_no")?.let { originalNo ->
                    salesDao.receiptByNo(originalNo)?.id
                        ?: error("Missing original receipt $originalNo")
                }
                val receiptId = salesDao.insertReceipt(
                    CustomerReceiptEntity(
                        receiptNo = receiptNo,
                        customerId = customer.id,
                        receiptDate = header.optLong("receipt_date_ms"),
                        currencyCode = header.optString("currency_code"),
                        exchangeRate = header.optDouble("exchange_rate", 1.0),
                        amountOriginal = header.optDouble("amount_original", 0.0),
                        amountBase = header.optDouble("amount_base", 0.0),
                        discountOriginal = header.optDouble("discount_original", 0.0),
                        discountBase = header.optDouble("discount_base", 0.0),
                        discountReason = header.optString("discount_reason"),
                        notes = header.optString("notes"),
                        treasuryAccountId = null,
                        reversalOfReceiptId = reversalOf,
                        createdBy = localUser.id,
                        createdAt = header.optLong("created_at_ms", System.currentTimeMillis()),
                    )
                )
                remote.receiptAllocations.filter { it.optString("receipt_no") == receiptNo }
                    .sortedBy { it.optInt("allocation_no") }
                    .forEach { row ->
                        val invoice = salesDao.invoiceByNo(row.getString("invoice_no"))
                            ?: error("Missing invoice ${row.getString("invoice_no")} for receipt $receiptNo")
                        salesDao.insertReceiptAllocation(
                            CustomerReceiptAllocationEntity(
                                receiptId = receiptId,
                                invoiceId = invoice.id,
                                amountBase = row.optDouble("amount_base", 0.0),
                                discountOriginal = row.optDouble("discount_original", 0.0),
                                discountBase = row.optDouble("discount_base", 0.0),
                            )
                        )
                    }
            }
            counters.downloaded++
        }
    }

    private suspend fun applyReturns(
        localUser: UserEntity,
        remote: RemoteData,
        counters: Counters,
        conflictDetails: MutableList<SalesReceivablesConflict>,
    ) {
        val salesDao = db.salesDao()
        for (header in remote.returns.sortedWith(compareBy({ it.optLong("return_date_ms") }, { it.optString("return_no") }))) {
            val returnNo = header.getString("return_no")
            val existing = salesDao.returnByNo(returnNo)
            if (existing != null) {
                val differences = returnDifferences(existing, header, remote)
                if (differences.isEmpty()) {
                    counters.unchanged++
                } else {
                    counters.conflicts++
                    conflictDetails += SalesReceivablesConflict(
                        documentType = "RETURN",
                        documentNo = returnNo,
                        differences = differences.take(MAX_CONFLICT_DIFFERENCES),
                    )
                }
                continue
            }
            db.withTransaction {
                val invoice = salesDao.invoiceByNo(header.getString("sales_invoice_no"))
                    ?: error("Missing invoice ${header.getString("sales_invoice_no")} for return $returnNo")
                val customer = db.customerDao().byCode(header.getString("customer_code"))
                    ?: error("Missing master customer ${header.getString("customer_code")}")
                val warehouse = db.warehouseDao().byCode(header.getString("warehouse_code"))
                    ?: error("Missing master warehouse ${header.getString("warehouse_code")}")
                val returnId = salesDao.insertReturn(
                    SalesReturnEntity(
                        returnNo = returnNo,
                        salesInvoiceId = invoice.id,
                        customerId = customer.id,
                        returnDate = header.optLong("return_date_ms"),
                        warehouseId = warehouse.id,
                        currencyCode = header.optString("currency_code"),
                        exchangeRate = header.optDouble("exchange_rate", 1.0),
                        settlementType = header.optString("settlement_type"),
                        totalOriginal = header.optDouble("total_original", 0.0),
                        totalBase = header.optDouble("total_base", 0.0),
                        totalCostBase = header.optDouble("total_cost_base", 0.0),
                        treasuryAccountId = null,
                        reason = header.optString("reason"),
                        status = header.optString("status", "POSTED"),
                        createdBy = localUser.id,
                        createdAt = header.optLong("created_at_ms", System.currentTimeMillis()),
                    )
                )
                val invoiceLines = salesDao.linesForInvoice(invoice.id)
                remote.returnLines.filter { it.optString("return_no") == returnNo }
                    .sortedBy { it.optInt("line_no") }
                    .forEach { row ->
                        val sourceLineNo = row.optInt("sales_invoice_line_no")
                        val sourceLine = invoiceLines.getOrNull(sourceLineNo - 1)
                            ?: error("Missing source line $sourceLineNo for return $returnNo")
                        val item = db.itemDao().byCode(row.getString("item_code"))
                            ?: error("Missing master item ${row.getString("item_code")}")
                        val unit = db.unitDao().byCode(row.getString("unit_code"))
                            ?: error("Missing master unit ${row.getString("unit_code")}")
                        salesDao.insertReturnLine(
                            SalesReturnLineEntity(
                                returnId = returnId,
                                salesLineId = sourceLine.id,
                                itemId = item.id,
                                unitId = unit.id,
                                quantity = row.optDouble("quantity", 0.0),
                                freeQuantity = row.optDouble("free_quantity", 0.0),
                                factorToBase = row.optDouble("factor_to_base", 1.0),
                                baseQuantity = row.optDouble("base_quantity", 0.0),
                                freeBaseQuantity = row.optDouble("free_base_quantity", 0.0),
                                unitPriceOriginal = row.optDouble("unit_price_original", 0.0),
                                lineNetOriginal = row.optDouble("line_net_original", 0.0),
                                costBase = row.optDouble("cost_base", 0.0),
                            )
                        )
                    }
            }
            counters.downloaded++
        }
    }

    private suspend fun localDocumentKeys(): Set<String> = buildSet {
        db.salesDao().allInvoicesForCloudSync().forEach { add("I:${it.invoiceNo.uppercase()}") }
        db.salesDao().allReceiptsForCloudSync().forEach { add("C:${it.receiptNo.uppercase()}") }
        db.salesDao().allReturnsForCloudSync().forEach { add("R:${it.returnNo.uppercase()}") }
    }

    private fun fetchAll(session: CloudSession): RemoteData = RemoteData(
        invoices = fetchRows("fush_tx_sales_invoices", session),
        lines = fetchRows("fush_tx_sales_lines", session),
        allocations = fetchRows("fush_tx_sales_allocations", session),
        receipts = fetchRows("fush_tx_customer_receipts", session),
        receiptAllocations = fetchRows("fush_tx_customer_receipt_allocations", session),
        returns = fetchRows("fush_tx_sales_returns", session),
        returnLines = fetchRows("fush_tx_sales_return_lines", session),
    )

    private fun fetchRows(table: String, session: CloudSession): List<JSONObject> {
        val org = encode(session.requireOrganizationId())
        val path = "/rest/v1/$table?select=*&organization_id=eq.$org&limit=10000"
        return when (val response = request("GET", path, null, session.accessToken, null)) {
            is HttpResult.Error -> {
                if (response.code == 404) error(context.getString(R.string.cloud_sales_schema_missing))
                error(apiErrorMessage(response))
            }
            is HttpResult.Ok -> {
                val array = runCatching { JSONArray(response.body) }.getOrElse {
                    error(context.getString(R.string.cloud_sales_invalid_response))
                }
                List(array.length()) { array.getJSONObject(it) }
            }
        }
    }

    private fun upsertBatch(
        table: String,
        conflictColumns: String,
        rows: List<JSONObject>,
        session: CloudSession,
    ) {
        if (rows.isEmpty()) return
        rows.chunked(150).forEach { chunk ->
            val payload = JSONArray().apply { chunk.forEach { put(it) } }.toString()
            val path = "/rest/v1/$table?on_conflict=${encodeConflictColumns(conflictColumns)}"
            when (val response = request(
                "POST", path, payload, session.accessToken,
                "resolution=merge-duplicates,return=minimal"
            )) {
                is HttpResult.Ok -> Unit
                is HttpResult.Error -> error(apiErrorMessage(response))
            }
        }
    }

    private fun invoiceJson(row: SalesInvoiceEntity, customerCode: String, warehouseCode: String, org: String, userId: String) = JSONObject()
        .put("organization_id", org).put("invoice_no", row.invoiceNo).put("customer_code", customerCode)
        .put("invoice_date_ms", row.invoiceDate).put("due_date_ms", row.dueDate ?: JSONObject.NULL)
        .put("warehouse_code", warehouseCode).put("currency_code", row.currencyCode).put("exchange_rate", row.exchangeRate)
        .put("payment_type", row.paymentType).put("channel", row.channel).put("province", row.province)
        .put("governorate_id", row.governorateId ?: JSONObject.NULL)
        .put("district_id", row.districtId ?: JSONObject.NULL)
        .put("area_id", row.areaId ?: JSONObject.NULL)
        .put("sales_rep_name_snapshot", row.salesRepNameSnapshot).put("sales_rep_rate_pct", row.salesRepRatePct)
        .put("free_qty_limit_pct_snapshot", row.freeQtyLimitPctSnapshot).put("free_qty_approval_reason", row.freeQtyApprovalReason)
        .put("discount_pct", row.discountPct).put("gross_original", row.grossOriginal).put("discount_original", row.discountOriginal)
        .put("transport_original", row.transportOriginal).put("fees_original", row.feesOriginal).put("risk_margin_original", row.riskMarginOriginal)
        .put("total_original", row.totalOriginal).put("total_base", row.totalBase).put("status", row.status)
        .put("below_floor_reason", row.belowFloorReason).put("notes", row.notes).put("created_at_ms", row.createdAt).put("updated_by", userId)

    private fun lineJson(row: SalesLineEntity, invoiceNo: String, lineNo: Int, itemCode: String, unitCode: String, org: String, userId: String) = JSONObject()
        .put("organization_id", org).put("invoice_no", invoiceNo).put("line_no", lineNo).put("item_code", itemCode).put("unit_code", unitCode)
        .put("quantity", row.quantity).put("free_quantity", row.freeQuantity).put("factor_to_base", row.factorToBase)
        .put("base_quantity", row.baseQuantity).put("free_base_quantity", row.freeBaseQuantity).put("unit_price_original", row.unitPriceOriginal)
        .put("gross_original", row.grossOriginal).put("discount_original", row.discountOriginal).put("net_original", row.netOriginal).put("updated_by", userId)

    private fun allocationJson(row: SalesAllocationEntity, invoiceNo: String, lineNo: Int, allocationNo: Int, itemCode: String, org: String, userId: String) = JSONObject()
        .put("organization_id", org).put("invoice_no", invoiceNo).put("line_no", lineNo).put("allocation_no", allocationNo).put("item_code", itemCode)
        .put("lot_no", row.lotNo ?: JSONObject.NULL).put("expiry_date_ms", row.expiryDate ?: JSONObject.NULL)
        .put("quantity_base", row.quantityBase).put("free_quantity_base", row.freeQuantityBase).put("unit_cost_base", row.unitCostBase).put("cost_base", row.costBase)
        .put("updated_by", userId)

    private fun receiptJson(row: CustomerReceiptEntity, customerCode: String, reversalNo: String?, org: String, userId: String) = JSONObject()
        .put("organization_id", org).put("receipt_no", row.receiptNo).put("customer_code", customerCode).put("receipt_date_ms", row.receiptDate)
        .put("currency_code", row.currencyCode).put("exchange_rate", row.exchangeRate).put("amount_original", row.amountOriginal).put("amount_base", row.amountBase)
        .put("discount_original", row.discountOriginal).put("discount_base", row.discountBase).put("discount_reason", row.discountReason).put("notes", row.notes)
        .put("reversal_of_receipt_no", reversalNo ?: JSONObject.NULL).put("created_at_ms", row.createdAt).put("updated_by", userId)

    private fun receiptAllocationJson(row: CustomerReceiptAllocationEntity, receiptNo: String, allocationNo: Int, invoiceNo: String, org: String, userId: String) = JSONObject()
        .put("organization_id", org).put("receipt_no", receiptNo).put("allocation_no", allocationNo).put("invoice_no", invoiceNo)
        .put("amount_base", row.amountBase).put("discount_original", row.discountOriginal).put("discount_base", row.discountBase).put("updated_by", userId)

    private fun returnJson(row: SalesReturnEntity, invoiceNo: String, customerCode: String, warehouseCode: String, org: String, userId: String) = JSONObject()
        .put("organization_id", org).put("return_no", row.returnNo).put("sales_invoice_no", invoiceNo).put("customer_code", customerCode)
        .put("return_date_ms", row.returnDate).put("warehouse_code", warehouseCode).put("currency_code", row.currencyCode).put("exchange_rate", row.exchangeRate)
        .put("settlement_type", row.settlementType).put("total_original", row.totalOriginal).put("total_base", row.totalBase).put("total_cost_base", row.totalCostBase)
        .put("reason", row.reason).put("status", row.status).put("created_at_ms", row.createdAt).put("updated_by", userId)

    private fun returnLineJson(row: SalesReturnLineEntity, returnNo: String, lineNo: Int, sourceLineNo: Int, itemCode: String, unitCode: String, org: String, userId: String) = JSONObject()
        .put("organization_id", org).put("return_no", returnNo).put("line_no", lineNo).put("sales_invoice_line_no", sourceLineNo)
        .put("item_code", itemCode).put("unit_code", unitCode).put("quantity", row.quantity).put("free_quantity", row.freeQuantity)
        .put("factor_to_base", row.factorToBase).put("base_quantity", row.baseQuantity).put("free_base_quantity", row.freeBaseQuantity)
        .put("unit_price_original", row.unitPriceOriginal).put("line_net_original", row.lineNetOriginal).put("cost_base", row.costBase).put("updated_by", userId)

    private suspend fun invoiceDifferences(
        local: SalesInvoiceEntity,
        cloud: JSONObject,
        remote: RemoteData,
    ): List<SalesConflictDifference> {
        val diffs = mutableListOf<SalesConflictDifference>()
        val customerCode = db.customerDao().byId(local.customerId)?.code.orEmpty()
        val warehouseCode = db.warehouseDao().byId(local.warehouseId)?.code.orEmpty()
        addDiff(diffs, "customer", customerCode, cloud.optString("customer_code"))
        addDiff(diffs, "warehouse", warehouseCode, cloud.optString("warehouse_code"))
        addDiff(diffs, "invoice_date", local.invoiceDate, cloud.optLong("invoice_date_ms"))
        addDiff(diffs, "due_date", local.dueDate, cloud.optNullableLong("due_date_ms"))
        addDiff(diffs, "currency", local.currencyCode, cloud.optString("currency_code"))
        addDiff(diffs, "exchange_rate", local.exchangeRate, cloud.optDouble("exchange_rate", 1.0))
        addDiff(diffs, "payment_type", local.paymentType, cloud.optString("payment_type"))
        addDiff(diffs, "channel", local.channel, cloud.optString("channel"))
        addDiff(diffs, "province", local.province, cloud.optString("province"))
        addDiff(diffs, "governorate_id", local.governorateId, cloud.optNullableString("governorate_id"))
        addDiff(diffs, "district_id", local.districtId, cloud.optNullableString("district_id"))
        addDiff(diffs, "area_id", local.areaId, cloud.optNullableString("area_id"))
        addDiff(diffs, "sales_rep", local.salesRepNameSnapshot, cloud.optString("sales_rep_name_snapshot"))
        addDiff(diffs, "discount_pct", local.discountPct, cloud.optDouble("discount_pct", 0.0))
        addDiff(diffs, "gross", local.grossOriginal, cloud.optDouble("gross_original", 0.0))
        addDiff(diffs, "discount", local.discountOriginal, cloud.optDouble("discount_original", 0.0))
        addDiff(diffs, "transport", local.transportOriginal, cloud.optDouble("transport_original", 0.0))
        addDiff(diffs, "fees", local.feesOriginal, cloud.optDouble("fees_original", 0.0))
        addDiff(diffs, "risk_margin", local.riskMarginOriginal, cloud.optDouble("risk_margin_original", 0.0))
        addDiff(diffs, "total", local.totalOriginal, cloud.optDouble("total_original", 0.0))
        addDiff(diffs, "total_base", local.totalBase, cloud.optDouble("total_base", 0.0))
        addDiff(diffs, "status", local.status, cloud.optString("status", "POSTED"))
        addDiff(diffs, "notes", local.notes, cloud.optString("notes"))

        val localLines = db.salesDao().linesForInvoice(local.id)
        val cloudLines = remote.lines.filter { it.optString("invoice_no").equals(local.invoiceNo, ignoreCase = true) }
            .sortedBy { it.optInt("line_no") }
        addDiff(diffs, "line_count", localLines.size.toLong(), cloudLines.size.toLong())
        localLines.zip(cloudLines).forEachIndexed { index, (localLine, cloudLine) ->
            val prefix = "line_${index + 1}"
            addDiff(diffs, "$prefix.item", db.itemDao().byId(localLine.itemId)?.code.orEmpty(), cloudLine.optString("item_code"))
            addDiff(diffs, "$prefix.unit", db.unitDao().byId(localLine.unitId)?.code.orEmpty(), cloudLine.optString("unit_code"))
            addDiff(diffs, "$prefix.quantity", localLine.quantity, cloudLine.optDouble("quantity", 0.0))
            addDiff(diffs, "$prefix.free_quantity", localLine.freeQuantity, cloudLine.optDouble("free_quantity", 0.0))
            addDiff(diffs, "$prefix.unit_price", localLine.unitPriceOriginal, cloudLine.optDouble("unit_price_original", 0.0))
            addDiff(diffs, "$prefix.net", localLine.netOriginal, cloudLine.optDouble("net_original", 0.0))

            val localAllocations = db.salesDao().allocationsForLine(localLine.id)
            val cloudAllocations = remote.allocations.filter {
                it.optString("invoice_no").equals(local.invoiceNo, ignoreCase = true) && it.optInt("line_no") == index + 1
            }.sortedBy { it.optInt("allocation_no") }
            addDiff(diffs, "$prefix.allocation_count", localAllocations.size.toLong(), cloudAllocations.size.toLong())
            localAllocations.zip(cloudAllocations).forEachIndexed { allocationIndex, (localAllocation, cloudAllocation) ->
                val allocationPrefix = "$prefix.allocation_${allocationIndex + 1}"
                addDiff(diffs, "$allocationPrefix.lot", localAllocation.lotNo, cloudAllocation.optNullableString("lot_no"))
                addDiff(diffs, "$allocationPrefix.expiry", localAllocation.expiryDate, cloudAllocation.optNullableLong("expiry_date_ms"))
                addDiff(diffs, "$allocationPrefix.quantity", localAllocation.quantityBase, cloudAllocation.optDouble("quantity_base", 0.0))
                addDiff(diffs, "$allocationPrefix.cost", localAllocation.costBase, cloudAllocation.optDouble("cost_base", 0.0))
            }
        }
        return diffs
    }

    private suspend fun receiptDifferences(
        local: CustomerReceiptEntity,
        cloud: JSONObject,
        remote: RemoteData,
    ): List<SalesConflictDifference> {
        val diffs = mutableListOf<SalesConflictDifference>()
        addDiff(diffs, "customer", db.customerDao().byId(local.customerId)?.code.orEmpty(), cloud.optString("customer_code"))
        addDiff(diffs, "receipt_date", local.receiptDate, cloud.optLong("receipt_date_ms"))
        addDiff(diffs, "currency", local.currencyCode, cloud.optString("currency_code"))
        addDiff(diffs, "exchange_rate", local.exchangeRate, cloud.optDouble("exchange_rate", 1.0))
        addDiff(diffs, "amount", local.amountOriginal, cloud.optDouble("amount_original", 0.0))
        addDiff(diffs, "amount_base", local.amountBase, cloud.optDouble("amount_base", 0.0))
        addDiff(diffs, "discount", local.discountOriginal, cloud.optDouble("discount_original", 0.0))
        addDiff(diffs, "discount_base", local.discountBase, cloud.optDouble("discount_base", 0.0))
        addDiff(diffs, "discount_reason", local.discountReason, cloud.optString("discount_reason"))
        addDiff(diffs, "notes", local.notes, cloud.optString("notes"))
        val localReversalNo = local.reversalOfReceiptId?.let { db.salesDao().receiptById(it)?.receiptNo }
        addDiff(diffs, "reversal_of", localReversalNo, cloud.optNullableString("reversal_of_receipt_no"))

        val localAllocations = db.salesDao().receiptAllocations(local.id)
        val cloudAllocations = remote.receiptAllocations.filter { it.optString("receipt_no").equals(local.receiptNo, ignoreCase = true) }
            .sortedBy { it.optInt("allocation_no") }
        addDiff(diffs, "allocation_count", localAllocations.size.toLong(), cloudAllocations.size.toLong())
        localAllocations.zip(cloudAllocations).forEachIndexed { index, (localAllocation, cloudAllocation) ->
            val prefix = "allocation_${index + 1}"
            addDiff(diffs, "$prefix.invoice", db.salesDao().invoiceById(localAllocation.invoiceId)?.invoiceNo.orEmpty(), cloudAllocation.optString("invoice_no"))
            addDiff(diffs, "$prefix.amount", localAllocation.amountBase, cloudAllocation.optDouble("amount_base", 0.0))
            addDiff(diffs, "$prefix.discount", localAllocation.discountBase, cloudAllocation.optDouble("discount_base", 0.0))
        }
        return diffs
    }

    private suspend fun returnDifferences(
        local: SalesReturnEntity,
        cloud: JSONObject,
        remote: RemoteData,
    ): List<SalesConflictDifference> {
        val diffs = mutableListOf<SalesConflictDifference>()
        val sourceInvoice = db.salesDao().invoiceById(local.salesInvoiceId)
        addDiff(diffs, "sales_invoice", sourceInvoice?.invoiceNo.orEmpty(), cloud.optString("sales_invoice_no"))
        addDiff(diffs, "customer", db.customerDao().byId(local.customerId)?.code.orEmpty(), cloud.optString("customer_code"))
        addDiff(diffs, "warehouse", db.warehouseDao().byId(local.warehouseId)?.code.orEmpty(), cloud.optString("warehouse_code"))
        addDiff(diffs, "return_date", local.returnDate, cloud.optLong("return_date_ms"))
        addDiff(diffs, "currency", local.currencyCode, cloud.optString("currency_code"))
        addDiff(diffs, "exchange_rate", local.exchangeRate, cloud.optDouble("exchange_rate", 1.0))
        addDiff(diffs, "settlement_type", local.settlementType, cloud.optString("settlement_type"))
        addDiff(diffs, "total", local.totalOriginal, cloud.optDouble("total_original", 0.0))
        addDiff(diffs, "total_base", local.totalBase, cloud.optDouble("total_base", 0.0))
        addDiff(diffs, "cost_base", local.totalCostBase, cloud.optDouble("total_cost_base", 0.0))
        addDiff(diffs, "reason", local.reason, cloud.optString("reason"))
        addDiff(diffs, "status", local.status, cloud.optString("status", "POSTED"))

        val localLines = db.salesDao().returnLinesForReturn(local.id)
        val cloudLines = remote.returnLines.filter { it.optString("return_no").equals(local.returnNo, ignoreCase = true) }
            .sortedBy { it.optInt("line_no") }
        addDiff(diffs, "line_count", localLines.size.toLong(), cloudLines.size.toLong())
        localLines.zip(cloudLines).forEachIndexed { index, (localLine, cloudLine) ->
            val prefix = "line_${index + 1}"
            addDiff(diffs, "$prefix.item", db.itemDao().byId(localLine.itemId)?.code.orEmpty(), cloudLine.optString("item_code"))
            addDiff(diffs, "$prefix.unit", db.unitDao().byId(localLine.unitId)?.code.orEmpty(), cloudLine.optString("unit_code"))
            addDiff(diffs, "$prefix.quantity", localLine.quantity, cloudLine.optDouble("quantity", 0.0))
            addDiff(diffs, "$prefix.free_quantity", localLine.freeQuantity, cloudLine.optDouble("free_quantity", 0.0))
            addDiff(diffs, "$prefix.net", localLine.lineNetOriginal, cloudLine.optDouble("line_net_original", 0.0))
            addDiff(diffs, "$prefix.cost", localLine.costBase, cloudLine.optDouble("cost_base", 0.0))
        }
        return diffs
    }

    private fun addDiff(target: MutableList<SalesConflictDifference>, field: String, local: String?, cloud: String?) {
        val left = local.orEmpty().trim()
        val right = cloud.orEmpty().trim()
        if (!left.equals(right, ignoreCase = false)) target += SalesConflictDifference(field, printable(left), printable(right))
    }

    private fun addDiff(target: MutableList<SalesConflictDifference>, field: String, local: Long?, cloud: Long?) {
        if (local != cloud) target += SalesConflictDifference(field, printable(local), printable(cloud))
    }

    private fun addDiff(target: MutableList<SalesConflictDifference>, field: String, local: Double, cloud: Double) {
        if (!nearlyEqual(local, cloud)) target += SalesConflictDifference(field, formatNumber(local), formatNumber(cloud))
    }

    private fun nearlyEqual(left: Double, right: Double): Boolean {
        val scale = maxOf(1.0, kotlin.math.abs(left), kotlin.math.abs(right))
        return kotlin.math.abs(left - right) <= 0.000001 * scale
    }

    private fun printable(value: Any?): String = when (value) {
        null -> "∅"
        is String -> value.ifBlank { "∅" }
        else -> value.toString()
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

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    private fun encodeConflictColumns(value: String): String = value.split(',').joinToString(",") { encode(it.trim()) }

    private sealed interface HttpResult {
        data class Ok(val code: Int, val body: String) : HttpResult
        data class Error(val code: Int, val body: String) : HttpResult
    }

    private data class RemoteData(
        val invoices: List<JSONObject>,
        val lines: List<JSONObject>,
        val allocations: List<JSONObject>,
        val receipts: List<JSONObject>,
        val receiptAllocations: List<JSONObject>,
        val returns: List<JSONObject>,
        val returnLines: List<JSONObject>,
    ) {
        fun documentCount(): Int = invoices.size + receipts.size + returns.size
        fun documentKeys(): Set<String> = buildSet {
            invoices.forEach { add("I:${it.optString("invoice_no").uppercase()}") }
            receipts.forEach { add("C:${it.optString("receipt_no").uppercase()}") }
            returns.forEach { add("R:${it.optString("return_no").uppercase()}") }
        }
    }

    private data class Counters(
        var uploaded: Int = 0,
        var downloaded: Int = 0,
        var unchanged: Int = 0,
        var conflicts: Int = 0,
        var skippedLocal: Int = 0,
    )

    private companion object {
        const val MAX_CONFLICT_DIFFERENCES = 24
    }
}

private fun JSONObject.optNullableLong(name: String): Long? = if (isNull(name) || !has(name)) null else optLong(name)
private fun JSONObject.optNullableString(name: String): String? = if (isNull(name) || !has(name)) null else optString(name).takeIf { it.isNotBlank() }
