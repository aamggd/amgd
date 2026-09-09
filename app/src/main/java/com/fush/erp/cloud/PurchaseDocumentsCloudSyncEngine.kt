package com.fush.erp.cloud

import android.content.Context
import androidx.room.withTransaction
import com.fush.erp.BuildConfig
import com.fush.erp.R
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.AuditEventEntity
import com.fush.erp.data.entity.PurchaseInvoiceEntity
import com.fush.erp.data.entity.PurchaseLineEntity
import com.fush.erp.data.entity.PurchaseReturnEntity
import com.fush.erp.data.entity.PurchaseReturnLineEntity
import com.fush.erp.data.entity.UserEntity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * v161 mirrors posted purchase invoices and purchase returns from the OWNER/ADMIN phone.
 * It intentionally does not replay stock movements, supplier payments, treasury movements,
 * or accounting journals; those side-effect domains remain local until their dedicated waves.
 */
internal class PurchaseDocumentsCloudSyncEngine(
    private val context: Context,
    private val db: FushDatabase,
) {
    private val store = PurchaseDocumentsSyncStore(context.applicationContext)

    fun lastSuccessAt(localUserId: Long): Long = store.lastSuccessAt(localUserId)
    fun lastConflicts(localUserId: Long): List<PurchaseDocumentsConflict> = store.conflicts(localUserId)

    suspend fun sync(
        localUser: UserEntity,
        session: CloudSession,
        cloudRole: String,
    ): CloudOperationResult<PurchaseDocumentsSyncResult> {
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
                        context.getString(R.string.cloud_purchase_owner_bootstrap_required)
                    )
                }
                publishAll(session)
                remote = fetchAll(session)
                bootstrapped = true
                publishedDocuments = localKeysBeforePublish.size
            } else if (canPublish) {
                publishAll(session)
                remote = fetchAll(session)
                publishedDocuments = (localKeysBeforePublish - remoteKeysBeforePublish).size
            }

            val counters = Counters()
            val conflictDetails = mutableListOf<PurchaseDocumentsConflict>()
            applyInvoices(localUser, remote, counters, conflictDetails)
            applyReturns(localUser, remote, counters, conflictDetails)

            if (!canPublish) {
                counters.skippedLocal += (localDocumentKeys() - remote.documentKeys()).size
            }

            val completedAt = System.currentTimeMillis()
            store.saveConflicts(localUser.id, conflictDetails)
            store.markSuccess(localUser.id, completedAt)
            val result = PurchaseDocumentsSyncResult(
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
                        action = "CLOUD_PURCHASE_DOCUMENTS_SYNC",
                        entityType = "SYSTEM",
                        entityId = session.requireOrganizationId(),
                        newValue = "downloaded=${result.downloadedDocuments};conflicts=${result.conflicts};skippedLocal=${result.skippedLocalDocuments}",
                        reason = if (result.bootstrappedCloud) "Purchase documents cloud mirror bootstrap" else "Purchase documents cloud mirror sync",
                        deviceInfo = "ANDROID_CLOUD_SYNC",
                    )
                )
            }
            CloudOperationResult.Success(result)
        } catch (error: Throwable) {
            CloudOperationResult.Failure(
                error.message?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.cloud_purchase_sync_failed)
            )
        }
    }

    private suspend fun publishAll(session: CloudSession) {
        val org = session.requireOrganizationId()
        val userId = session.userId
        val dao = db.purchaseDao()
        val supplierById = db.supplierDao().allSuppliers().associateBy { it.id }
        val warehouseById = db.warehouseDao().allRows().associateBy { it.id }
        val itemById = db.itemDao().allRows().associateBy { it.id }
        val unitById = db.unitDao().allRows().associateBy { it.id }
        val invoices = dao.allInvoicesForCloudSync()
        val invoiceNoById = invoices.associate { it.id to it.invoiceNo }
        val sourceLineNoById = mutableMapOf<Long, Int>()

        val headers = mutableListOf<JSONObject>()
        val lines = mutableListOf<JSONObject>()
        for (invoice in invoices) {
            val supplierCode = supplierById[invoice.supplierId]?.code
                ?: error("Missing supplier for purchase ${invoice.invoiceNo}")
            val warehouseCode = warehouseById[invoice.warehouseId]?.code
                ?: error("Missing warehouse for purchase ${invoice.invoiceNo}")
            headers += invoiceJson(invoice, supplierCode, warehouseCode, org, userId)
            dao.linesForInvoice(invoice.id).forEachIndexed { index, line ->
                val lineNo = index + 1
                sourceLineNoById[line.id] = lineNo
                val itemCode = itemById[line.itemId]?.code ?: error("Missing item for purchase ${invoice.invoiceNo}")
                val unitCode = unitById[line.unitId]?.code ?: error("Missing unit for purchase ${invoice.invoiceNo}")
                lines += lineJson(line, invoice.invoiceNo, lineNo, itemCode, unitCode, org, userId)
            }
        }

        val returnHeaders = mutableListOf<JSONObject>()
        val returnLines = mutableListOf<JSONObject>()
        for (purchaseReturn in dao.allReturnsForCloudSync()) {
            val invoiceNo = invoiceNoById[purchaseReturn.purchaseInvoiceId]
                ?: error("Missing source purchase for return ${purchaseReturn.returnNo}")
            val supplierCode = supplierById[purchaseReturn.supplierId]?.code
                ?: error("Missing supplier for return ${purchaseReturn.returnNo}")
            val warehouseCode = warehouseById[purchaseReturn.warehouseId]?.code
                ?: error("Missing warehouse for return ${purchaseReturn.returnNo}")
            returnHeaders += returnJson(purchaseReturn, invoiceNo, supplierCode, warehouseCode, org, userId)
            dao.returnLinesForReturn(purchaseReturn.id).forEachIndexed { index, line ->
                val sourceLineNo = sourceLineNoById[line.purchaseLineId]
                    ?: error("Missing source purchase line for return ${purchaseReturn.returnNo}")
                val itemCode = itemById[line.itemId]?.code ?: error("Missing item for return ${purchaseReturn.returnNo}")
                val unitCode = unitById[line.unitId]?.code ?: error("Missing unit for return ${purchaseReturn.returnNo}")
                returnLines += returnLineJson(
                    line, purchaseReturn.returnNo, index + 1, sourceLineNo,
                    itemCode, unitCode, org, userId
                )
            }
        }

        upsertBatch("fush_tx_purchase_invoices", "organization_id,invoice_no", headers, session)
        upsertBatch("fush_tx_purchase_lines", "organization_id,invoice_no,line_no", lines, session)
        upsertBatch("fush_tx_purchase_returns", "organization_id,return_no", returnHeaders, session)
        upsertBatch("fush_tx_purchase_return_lines", "organization_id,return_no,line_no", returnLines, session)
    }

    private suspend fun applyInvoices(localUser: UserEntity, remote: RemoteData, counters: Counters, conflictDetails: MutableList<PurchaseDocumentsConflict>) {
        val dao = db.purchaseDao()
        remote.invoices.sortedWith(compareBy({ it.optLong("invoice_date_ms") }, { it.optString("invoice_no") }))
            .forEach { header ->
                val invoiceNo = header.getString("invoice_no")
                val existing = dao.invoiceByNo(invoiceNo)
                if (existing != null) {
                    val differences = invoiceDifferences(existing, header, remote)
                    if (differences.isEmpty()) {
                        counters.unchanged++
                    } else {
                        counters.conflicts++
                        conflictDetails += PurchaseDocumentsConflict(
                            documentType = "INVOICE",
                            documentNo = invoiceNo,
                            differences = differences,
                        )
                    }
                    return@forEach
                }

                db.withTransaction {
                    val supplier = db.supplierDao().byCode(header.getString("supplier_code"))
                        ?: error("Missing master supplier ${header.getString("supplier_code")}")
                    val warehouse = db.warehouseDao().byCode(header.getString("warehouse_code"))
                        ?: error("Missing master warehouse ${header.getString("warehouse_code")}")
                    val invoiceId = dao.insertInvoice(
                        PurchaseInvoiceEntity(
                            invoiceNo = invoiceNo,
                            supplierInvoiceNo = header.optString("supplier_invoice_no"),
                            supplierId = supplier.id,
                            invoiceDate = header.optLong("invoice_date_ms"),
                            dueDate = header.optNullableLong("due_date_ms"),
                            warehouseId = warehouse.id,
                            currencyCode = header.optString("currency_code"),
                            exchangeRate = header.optDouble("exchange_rate", 1.0),
                            paymentType = header.optString("payment_type"),
                            subtotalOriginal = header.optDouble("subtotal_original", 0.0),
                            discountOriginal = header.optDouble("discount_original", 0.0),
                            freightOriginal = header.optDouble("freight_original", 0.0),
                            customsOriginal = header.optDouble("customs_original", 0.0),
                            otherChargesOriginal = header.optDouble("other_charges_original", 0.0),
                            totalOriginal = header.optDouble("total_original", 0.0),
                            totalBase = header.optDouble("total_base", 0.0),
                            treasuryAccountId = null,
                            status = header.optString("status", "POSTED"),
                            notes = header.optString("notes"),
                            createdBy = localUser.id,
                            createdAt = header.optLong("created_at_ms", System.currentTimeMillis()),
                        )
                    )
                    remote.lines.filter { it.optString("invoice_no").equals(invoiceNo, ignoreCase = true) }
                        .sortedBy { it.optInt("line_no") }
                        .forEach { row ->
                            val item = db.itemDao().byCode(row.getString("item_code"))
                                ?: error("Missing master item ${row.getString("item_code")}")
                            val unit = db.unitDao().byCode(row.getString("unit_code"))
                                ?: error("Missing master unit ${row.getString("unit_code")}")
                            dao.insertLine(
                                PurchaseLineEntity(
                                    invoiceId = invoiceId,
                                    itemId = item.id,
                                    unitId = unit.id,
                                    quantity = row.optDouble("quantity", 0.0),
                                    factorToBase = row.optDouble("factor_to_base", 1.0),
                                    baseQuantity = row.optDouble("base_quantity", 0.0),
                                    unitPriceOriginal = row.optDouble("unit_price_original", 0.0),
                                    lineTotalOriginal = row.optDouble("line_total_original", 0.0),
                                    unitCostBase = row.optDouble("unit_cost_base", 0.0),
                                    lotNo = row.optNullableString("lot_no"),
                                    expiryDate = row.optNullableLong("expiry_date_ms"),
                                )
                            )
                        }
                }
                counters.downloaded++
            }
    }

    private suspend fun applyReturns(localUser: UserEntity, remote: RemoteData, counters: Counters, conflictDetails: MutableList<PurchaseDocumentsConflict>) {
        val dao = db.purchaseDao()
        remote.returns.sortedWith(compareBy({ it.optLong("return_date_ms") }, { it.optString("return_no") }))
            .forEach { header ->
                val returnNo = header.getString("return_no")
                val existing = dao.returnByNo(returnNo)
                if (existing != null) {
                    val differences = returnDifferences(existing, header, remote)
                    if (differences.isEmpty()) {
                        counters.unchanged++
                    } else {
                        counters.conflicts++
                        conflictDetails += PurchaseDocumentsConflict(
                            documentType = "RETURN",
                            documentNo = returnNo,
                            differences = differences,
                        )
                    }
                    return@forEach
                }

                db.withTransaction {
                    val invoice = dao.invoiceByNo(header.getString("purchase_invoice_no"))
                        ?: error("Missing source purchase ${header.getString("purchase_invoice_no")}")
                    val supplier = db.supplierDao().byCode(header.getString("supplier_code"))
                        ?: error("Missing master supplier ${header.getString("supplier_code")}")
                    val warehouse = db.warehouseDao().byCode(header.getString("warehouse_code"))
                        ?: error("Missing master warehouse ${header.getString("warehouse_code")}")
                    val returnId = dao.insertReturn(
                        PurchaseReturnEntity(
                            returnNo = returnNo,
                            purchaseInvoiceId = invoice.id,
                            supplierId = supplier.id,
                            returnDate = header.optLong("return_date_ms"),
                            warehouseId = warehouse.id,
                            currencyCode = header.optString("currency_code"),
                            exchangeRate = header.optDouble("exchange_rate", 1.0),
                            settlementType = header.optString("settlement_type"),
                            totalOriginal = header.optDouble("total_original", 0.0),
                            totalBase = header.optDouble("total_base", 0.0),
                            treasuryAccountId = null,
                            status = header.optString("status", "POSTED"),
                            reason = header.optString("reason"),
                            createdBy = localUser.id,
                            createdAt = header.optLong("created_at_ms", System.currentTimeMillis()),
                        )
                    )
                    val sourceLines = dao.linesForInvoice(invoice.id)
                    remote.returnLines.filter { it.optString("return_no").equals(returnNo, ignoreCase = true) }
                        .sortedBy { it.optInt("line_no") }
                        .forEach { row ->
                            val sourceLineNo = row.optInt("purchase_invoice_line_no")
                            val sourceLine = sourceLines.getOrNull(sourceLineNo - 1)
                                ?: error("Missing source purchase line $sourceLineNo for return $returnNo")
                            val item = db.itemDao().byCode(row.getString("item_code"))
                                ?: error("Missing master item ${row.getString("item_code")}")
                            val unit = db.unitDao().byCode(row.getString("unit_code"))
                                ?: error("Missing master unit ${row.getString("unit_code")}")
                            dao.insertReturnLine(
                                PurchaseReturnLineEntity(
                                    returnId = returnId,
                                    purchaseLineId = sourceLine.id,
                                    itemId = item.id,
                                    unitId = unit.id,
                                    quantity = row.optDouble("quantity", 0.0),
                                    factorToBase = row.optDouble("factor_to_base", 1.0),
                                    baseQuantity = row.optDouble("base_quantity", 0.0),
                                    unitPriceOriginal = row.optDouble("unit_price_original", 0.0),
                                    lineTotalOriginal = row.optDouble("line_total_original", 0.0),
                                    unitCostBase = row.optDouble("unit_cost_base", 0.0),
                                )
                            )
                        }
                }
                counters.downloaded++
            }
    }

    private suspend fun localDocumentKeys(): Set<String> = buildSet {
        db.purchaseDao().allInvoicesForCloudSync().forEach { add("I:${it.invoiceNo.uppercase()}") }
        db.purchaseDao().allReturnsForCloudSync().forEach { add("R:${it.returnNo.uppercase()}") }
    }

    private fun fetchAll(session: CloudSession): RemoteData = RemoteData(
        invoices = fetchRows("fush_tx_purchase_invoices", session),
        lines = fetchRows("fush_tx_purchase_lines", session),
        returns = fetchRows("fush_tx_purchase_returns", session),
        returnLines = fetchRows("fush_tx_purchase_return_lines", session),
    )

    private fun fetchRows(table: String, session: CloudSession): List<JSONObject> {
        val org = encode(session.requireOrganizationId())
        val path = "/rest/v1/$table?select=*&organization_id=eq.$org&limit=10000"
        return when (val response = request("GET", path, null, session.accessToken, null)) {
            is HttpResult.Error -> {
                if (response.code == 404) error(context.getString(R.string.cloud_purchase_schema_missing))
                error(apiErrorMessage(response))
            }
            is HttpResult.Ok -> {
                val array = runCatching { JSONArray(response.body) }.getOrElse {
                    error(context.getString(R.string.cloud_purchase_invalid_response))
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
            when (val response = request(
                "POST", path, payload, session.accessToken,
                "resolution=merge-duplicates,return=minimal"
            )) {
                is HttpResult.Ok -> Unit
                is HttpResult.Error -> error(apiErrorMessage(response))
            }
        }
    }

    private fun invoiceJson(row: PurchaseInvoiceEntity, supplierCode: String, warehouseCode: String, org: String, userId: String) = JSONObject()
        .put("organization_id", org).put("invoice_no", row.invoiceNo).put("supplier_invoice_no", row.supplierInvoiceNo)
        .put("supplier_code", supplierCode).put("invoice_date_ms", row.invoiceDate).put("due_date_ms", row.dueDate ?: JSONObject.NULL)
        .put("warehouse_code", warehouseCode).put("currency_code", row.currencyCode).put("exchange_rate", row.exchangeRate)
        .put("payment_type", row.paymentType).put("subtotal_original", row.subtotalOriginal).put("discount_original", row.discountOriginal)
        .put("freight_original", row.freightOriginal).put("customs_original", row.customsOriginal).put("other_charges_original", row.otherChargesOriginal)
        .put("total_original", row.totalOriginal).put("total_base", row.totalBase).put("status", row.status).put("notes", row.notes)
        .put("created_at_ms", row.createdAt).put("updated_by", userId)

    private fun lineJson(row: PurchaseLineEntity, invoiceNo: String, lineNo: Int, itemCode: String, unitCode: String, org: String, userId: String) = JSONObject()
        .put("organization_id", org).put("invoice_no", invoiceNo).put("line_no", lineNo).put("item_code", itemCode).put("unit_code", unitCode)
        .put("quantity", row.quantity).put("factor_to_base", row.factorToBase).put("base_quantity", row.baseQuantity)
        .put("unit_price_original", row.unitPriceOriginal).put("line_total_original", row.lineTotalOriginal).put("unit_cost_base", row.unitCostBase)
        .put("lot_no", row.lotNo ?: JSONObject.NULL).put("expiry_date_ms", row.expiryDate ?: JSONObject.NULL).put("updated_by", userId)

    private fun returnJson(row: PurchaseReturnEntity, invoiceNo: String, supplierCode: String, warehouseCode: String, org: String, userId: String) = JSONObject()
        .put("organization_id", org).put("return_no", row.returnNo).put("purchase_invoice_no", invoiceNo).put("supplier_code", supplierCode)
        .put("return_date_ms", row.returnDate).put("warehouse_code", warehouseCode).put("currency_code", row.currencyCode).put("exchange_rate", row.exchangeRate)
        .put("settlement_type", row.settlementType).put("total_original", row.totalOriginal).put("total_base", row.totalBase)
        .put("status", row.status).put("reason", row.reason).put("created_at_ms", row.createdAt).put("updated_by", userId)

    private fun returnLineJson(row: PurchaseReturnLineEntity, returnNo: String, lineNo: Int, sourceLineNo: Int, itemCode: String, unitCode: String, org: String, userId: String) = JSONObject()
        .put("organization_id", org).put("return_no", returnNo).put("line_no", lineNo).put("purchase_invoice_line_no", sourceLineNo)
        .put("item_code", itemCode).put("unit_code", unitCode).put("quantity", row.quantity).put("factor_to_base", row.factorToBase)
        .put("base_quantity", row.baseQuantity).put("unit_price_original", row.unitPriceOriginal).put("line_total_original", row.lineTotalOriginal)
        .put("unit_cost_base", row.unitCostBase).put("updated_by", userId)

    private suspend fun invoiceCanonical(row: PurchaseInvoiceEntity): String = listOf(
        db.supplierDao().byId(row.supplierId)?.code.orEmpty(),
        db.warehouseDao().byId(row.warehouseId)?.code.orEmpty(), row.supplierInvoiceNo,
        row.invoiceDate.toString(), row.dueDate?.toString().orEmpty(), row.currencyCode, formatNumber(row.exchangeRate), row.paymentType,
        formatNumber(row.subtotalOriginal), formatNumber(row.discountOriginal), formatNumber(row.freightOriginal), formatNumber(row.customsOriginal),
        formatNumber(row.otherChargesOriginal), formatNumber(row.totalOriginal), formatNumber(row.totalBase), row.status, row.notes
    ).joinToString("\u001f")

    private fun invoiceCanonical(row: JSONObject): String = listOf(
        row.optString("supplier_code"), row.optString("warehouse_code"), row.optString("supplier_invoice_no"),
        row.optLong("invoice_date_ms").toString(), row.optNullableLong("due_date_ms")?.toString().orEmpty(), row.optString("currency_code"),
        formatNumber(row.optDouble("exchange_rate", 1.0)), row.optString("payment_type"), formatNumber(row.optDouble("subtotal_original", 0.0)),
        formatNumber(row.optDouble("discount_original", 0.0)), formatNumber(row.optDouble("freight_original", 0.0)), formatNumber(row.optDouble("customs_original", 0.0)),
        formatNumber(row.optDouble("other_charges_original", 0.0)), formatNumber(row.optDouble("total_original", 0.0)), formatNumber(row.optDouble("total_base", 0.0)),
        row.optString("status", "POSTED"), row.optString("notes")
    ).joinToString("\u001f")

    private suspend fun localInvoiceLinesCanonical(invoiceId: Long): String = db.purchaseDao().linesForInvoice(invoiceId).map { row ->
        listOf(
            db.itemDao().byId(row.itemId)?.code.orEmpty(), db.unitDao().byId(row.unitId)?.code.orEmpty(),
            formatNumber(row.quantity), formatNumber(row.factorToBase), formatNumber(row.baseQuantity), formatNumber(row.unitPriceOriginal),
            formatNumber(row.lineTotalOriginal), formatNumber(row.unitCostBase), row.lotNo.orEmpty(), row.expiryDate?.toString().orEmpty()
        ).joinToString("\u001e")
    }.joinToString("\u001d")

    private fun remoteInvoiceLinesCanonical(invoiceNo: String, remote: RemoteData): String = remote.lines
        .filter { it.optString("invoice_no").equals(invoiceNo, ignoreCase = true) }.sortedBy { it.optInt("line_no") }.map { row ->
            listOf(
                row.optString("item_code"), row.optString("unit_code"), formatNumber(row.optDouble("quantity", 0.0)),
                formatNumber(row.optDouble("factor_to_base", 1.0)), formatNumber(row.optDouble("base_quantity", 0.0)),
                formatNumber(row.optDouble("unit_price_original", 0.0)), formatNumber(row.optDouble("line_total_original", 0.0)),
                formatNumber(row.optDouble("unit_cost_base", 0.0)), row.optNullableString("lot_no").orEmpty(), row.optNullableLong("expiry_date_ms")?.toString().orEmpty()
            ).joinToString("\u001e")
        }.joinToString("\u001d")

    private suspend fun returnCanonical(row: PurchaseReturnEntity): String = listOf(
        db.purchaseDao().invoiceById(row.purchaseInvoiceId)?.invoiceNo.orEmpty(), db.supplierDao().byId(row.supplierId)?.code.orEmpty(),
        db.warehouseDao().byId(row.warehouseId)?.code.orEmpty(), row.returnDate.toString(), row.currencyCode, formatNumber(row.exchangeRate),
        row.settlementType, formatNumber(row.totalOriginal), formatNumber(row.totalBase), row.status, row.reason
    ).joinToString("\u001f")

    private fun returnCanonical(row: JSONObject): String = listOf(
        row.optString("purchase_invoice_no"), row.optString("supplier_code"), row.optString("warehouse_code"), row.optLong("return_date_ms").toString(),
        row.optString("currency_code"), formatNumber(row.optDouble("exchange_rate", 1.0)), row.optString("settlement_type"),
        formatNumber(row.optDouble("total_original", 0.0)), formatNumber(row.optDouble("total_base", 0.0)), row.optString("status", "POSTED"), row.optString("reason")
    ).joinToString("\u001f")

    private suspend fun localReturnLinesCanonical(returnId: Long): String = db.purchaseDao().returnLinesForReturn(returnId).map { row ->
        listOf(
            db.purchaseDao().lineById(row.purchaseLineId)?.let { source ->
                val lines = db.purchaseDao().linesForInvoice(source.invoiceId)
                (lines.indexOfFirst { it.id == source.id } + 1).toString()
            }.orEmpty(),
            db.itemDao().byId(row.itemId)?.code.orEmpty(), db.unitDao().byId(row.unitId)?.code.orEmpty(),
            formatNumber(row.quantity), formatNumber(row.factorToBase), formatNumber(row.baseQuantity), formatNumber(row.unitPriceOriginal),
            formatNumber(row.lineTotalOriginal), formatNumber(row.unitCostBase)
        ).joinToString("\u001e")
    }.joinToString("\u001d")

    private fun remoteReturnLinesCanonical(returnNo: String, remote: RemoteData): String = remote.returnLines
        .filter { it.optString("return_no").equals(returnNo, ignoreCase = true) }.sortedBy { it.optInt("line_no") }.map { row ->
            listOf(
                row.optInt("purchase_invoice_line_no").toString(), row.optString("item_code"), row.optString("unit_code"),
                formatNumber(row.optDouble("quantity", 0.0)), formatNumber(row.optDouble("factor_to_base", 1.0)), formatNumber(row.optDouble("base_quantity", 0.0)),
                formatNumber(row.optDouble("unit_price_original", 0.0)), formatNumber(row.optDouble("line_total_original", 0.0)), formatNumber(row.optDouble("unit_cost_base", 0.0))
            ).joinToString("\u001e")
        }.joinToString("\u001d")

    private suspend fun invoiceDifferences(
        local: PurchaseInvoiceEntity,
        cloud: JSONObject,
        remote: RemoteData,
    ): List<PurchaseConflictDifference> {
        val diffs = mutableListOf<PurchaseConflictDifference>()
        addDiff(diffs, "supplier", db.supplierDao().byId(local.supplierId)?.code.orEmpty(), cloud.optString("supplier_code"))
        addDiff(diffs, "supplier_invoice_no", local.supplierInvoiceNo, cloud.optString("supplier_invoice_no"))
        addDiff(diffs, "warehouse", db.warehouseDao().byId(local.warehouseId)?.code.orEmpty(), cloud.optString("warehouse_code"))
        addDiff(diffs, "invoice_date", local.invoiceDate, cloud.optLong("invoice_date_ms"))
        addDiff(diffs, "due_date", local.dueDate, cloud.optNullableLong("due_date_ms"))
        addDiff(diffs, "currency", local.currencyCode, cloud.optString("currency_code"))
        addDiff(diffs, "exchange_rate", local.exchangeRate, cloud.optDouble("exchange_rate", 1.0))
        addDiff(diffs, "payment_type", local.paymentType, cloud.optString("payment_type"))
        addDiff(diffs, "subtotal", local.subtotalOriginal, cloud.optDouble("subtotal_original", 0.0))
        addDiff(diffs, "discount", local.discountOriginal, cloud.optDouble("discount_original", 0.0))
        addDiff(diffs, "freight", local.freightOriginal, cloud.optDouble("freight_original", 0.0))
        addDiff(diffs, "customs", local.customsOriginal, cloud.optDouble("customs_original", 0.0))
        addDiff(diffs, "other_charges", local.otherChargesOriginal, cloud.optDouble("other_charges_original", 0.0))
        addDiff(diffs, "total", local.totalOriginal, cloud.optDouble("total_original", 0.0))
        addDiff(diffs, "total_base", local.totalBase, cloud.optDouble("total_base", 0.0))
        addDiff(diffs, "status", local.status, cloud.optString("status", "POSTED"))
        addDiff(diffs, "notes", local.notes, cloud.optString("notes"))

        val localLines = db.purchaseDao().linesForInvoice(local.id)
        val cloudLines = remote.lines.filter { it.optString("invoice_no").equals(local.invoiceNo, ignoreCase = true) }
            .sortedBy { it.optInt("line_no") }
        addDiff(diffs, "line_count", localLines.size.toLong(), cloudLines.size.toLong())
        localLines.zip(cloudLines).forEachIndexed { index, (localLine, cloudLine) ->
            val prefix = "line_${index + 1}"
            addDiff(diffs, "$prefix.item", db.itemDao().byId(localLine.itemId)?.code.orEmpty(), cloudLine.optString("item_code"))
            addDiff(diffs, "$prefix.unit", db.unitDao().byId(localLine.unitId)?.code.orEmpty(), cloudLine.optString("unit_code"))
            addDiff(diffs, "$prefix.quantity", localLine.quantity, cloudLine.optDouble("quantity", 0.0))
            addDiff(diffs, "$prefix.factor", localLine.factorToBase, cloudLine.optDouble("factor_to_base", 1.0))
            addDiff(diffs, "$prefix.base_quantity", localLine.baseQuantity, cloudLine.optDouble("base_quantity", 0.0))
            addDiff(diffs, "$prefix.unit_price", localLine.unitPriceOriginal, cloudLine.optDouble("unit_price_original", 0.0))
            addDiff(diffs, "$prefix.line_total", localLine.lineTotalOriginal, cloudLine.optDouble("line_total_original", 0.0))
            addDiff(diffs, "$prefix.unit_cost", localLine.unitCostBase, cloudLine.optDouble("unit_cost_base", 0.0))
            addDiff(diffs, "$prefix.lot", localLine.lotNo, cloudLine.optNullableString("lot_no"))
            addDiff(diffs, "$prefix.expiry", localLine.expiryDate, cloudLine.optNullableLong("expiry_date_ms"))
        }
        return diffs
    }

    private suspend fun returnDifferences(
        local: PurchaseReturnEntity,
        cloud: JSONObject,
        remote: RemoteData,
    ): List<PurchaseConflictDifference> {
        val diffs = mutableListOf<PurchaseConflictDifference>()
        addDiff(diffs, "purchase_invoice", db.purchaseDao().invoiceById(local.purchaseInvoiceId)?.invoiceNo.orEmpty(), cloud.optString("purchase_invoice_no"))
        addDiff(diffs, "supplier", db.supplierDao().byId(local.supplierId)?.code.orEmpty(), cloud.optString("supplier_code"))
        addDiff(diffs, "warehouse", db.warehouseDao().byId(local.warehouseId)?.code.orEmpty(), cloud.optString("warehouse_code"))
        addDiff(diffs, "return_date", local.returnDate, cloud.optLong("return_date_ms"))
        addDiff(diffs, "currency", local.currencyCode, cloud.optString("currency_code"))
        addDiff(diffs, "exchange_rate", local.exchangeRate, cloud.optDouble("exchange_rate", 1.0))
        addDiff(diffs, "settlement_type", local.settlementType, cloud.optString("settlement_type"))
        addDiff(diffs, "total", local.totalOriginal, cloud.optDouble("total_original", 0.0))
        addDiff(diffs, "total_base", local.totalBase, cloud.optDouble("total_base", 0.0))
        addDiff(diffs, "status", local.status, cloud.optString("status", "POSTED"))
        addDiff(diffs, "reason", local.reason, cloud.optString("reason"))

        val localLines = db.purchaseDao().returnLinesForReturn(local.id)
        val cloudLines = remote.returnLines.filter { it.optString("return_no").equals(local.returnNo, ignoreCase = true) }
            .sortedBy { it.optInt("line_no") }
        addDiff(diffs, "line_count", localLines.size.toLong(), cloudLines.size.toLong())
        localLines.zip(cloudLines).forEachIndexed { index, (localLine, cloudLine) ->
            val prefix = "line_${index + 1}"
            val sourceLineNo = db.purchaseDao().lineById(localLine.purchaseLineId)?.let { source ->
                val sourceLines = db.purchaseDao().linesForInvoice(source.invoiceId)
                sourceLines.indexOfFirst { it.id == source.id } + 1
            } ?: 0
            addDiff(diffs, "$prefix.source_line", sourceLineNo.toLong(), cloudLine.optInt("purchase_invoice_line_no").toLong())
            addDiff(diffs, "$prefix.item", db.itemDao().byId(localLine.itemId)?.code.orEmpty(), cloudLine.optString("item_code"))
            addDiff(diffs, "$prefix.unit", db.unitDao().byId(localLine.unitId)?.code.orEmpty(), cloudLine.optString("unit_code"))
            addDiff(diffs, "$prefix.quantity", localLine.quantity, cloudLine.optDouble("quantity", 0.0))
            addDiff(diffs, "$prefix.factor", localLine.factorToBase, cloudLine.optDouble("factor_to_base", 1.0))
            addDiff(diffs, "$prefix.base_quantity", localLine.baseQuantity, cloudLine.optDouble("base_quantity", 0.0))
            addDiff(diffs, "$prefix.unit_price", localLine.unitPriceOriginal, cloudLine.optDouble("unit_price_original", 0.0))
            addDiff(diffs, "$prefix.line_total", localLine.lineTotalOriginal, cloudLine.optDouble("line_total_original", 0.0))
            addDiff(diffs, "$prefix.unit_cost", localLine.unitCostBase, cloudLine.optDouble("unit_cost_base", 0.0))
        }
        return diffs
    }

    private fun addDiff(target: MutableList<PurchaseConflictDifference>, field: String, local: String?, cloud: String?) {
        val left = local.orEmpty().trim()
        val right = cloud.orEmpty().trim()
        if (left != right) target += PurchaseConflictDifference(field, printable(left), printable(right))
    }

    private fun addDiff(target: MutableList<PurchaseConflictDifference>, field: String, local: Long?, cloud: Long?) {
        if (local != cloud) target += PurchaseConflictDifference(field, printable(local), printable(cloud))
    }

    private fun addDiff(target: MutableList<PurchaseConflictDifference>, field: String, local: Double, cloud: Double) {
        if (!nearlyEqual(local, cloud)) target += PurchaseConflictDifference(field, formatNumber(local), formatNumber(cloud))
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

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun encodeConflictColumns(columns: String): String = columns.split(',').joinToString(",") { encode(it.trim()) }

    private fun JSONObject.optNullableString(name: String): String? =
        if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

    private fun JSONObject.optNullableLong(name: String): Long? =
        if (!has(name) || isNull(name)) null else optLong(name)

    private sealed interface HttpResult {
        data class Ok(val code: Int, val body: String) : HttpResult
        data class Error(val code: Int, val body: String) : HttpResult
    }

    private data class RemoteData(
        val invoices: List<JSONObject>,
        val lines: List<JSONObject>,
        val returns: List<JSONObject>,
        val returnLines: List<JSONObject>,
    ) {
        fun documentCount(): Int = invoices.size + returns.size
        fun documentKeys(): Set<String> = buildSet {
            invoices.forEach { add("I:${it.optString("invoice_no").uppercase()}") }
            returns.forEach { add("R:${it.optString("return_no").uppercase()}") }
        }
    }

    private data class Counters(
        var downloaded: Int = 0,
        var unchanged: Int = 0,
        var conflicts: Int = 0,
        var skippedLocal: Int = 0,
    )
}
