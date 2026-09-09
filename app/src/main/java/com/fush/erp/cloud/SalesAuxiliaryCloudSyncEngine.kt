package com.fush.erp.cloud

import android.content.Context
import androidx.room.withTransaction
import com.fush.erp.BuildConfig
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.YemenGeographyHierarchy
import com.fush.erp.data.entity.*
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * v179 conflict-safe bidirectional sync for AdditionalCharges + Shipment Tracking.
 *
 * This engine hydrates Room entities directly. It NEVER calls AdditionalChargesService,
 * ShipmentService, AccountingService, or treasury posting services while downloading.
 * Therefore a downloaded shipment expense/charge does not replay cash/GL effects; v177
 * AccountingCloudSyncEngine remains the sole cloud source for journals and treasury vouchers.
 */
internal class SalesAuxiliaryCloudSyncEngine(
    private val context: Context,
    private val db: FushDatabase,
) {
    private val store = SalesAuxiliarySyncStore(context.applicationContext)

    fun lastSuccessAt(localUserId: Long): Long = store.lastSuccessAt(localUserId)
    fun lastConflicts(localUserId: Long): List<SalesAuxiliaryConflict> = store.conflicts(localUserId)

    suspend fun sync(
        localUser: UserEntity,
        session: CloudSession,
        cloudRole: String,
    ): CloudOperationResult<SalesAuxiliarySyncResult> {
        return try {
            val role = cloudRole.trim().uppercase() // retained only for diagnostics
            val canPublish = true // v185: active organization membership authorizes sync transport
            val initialRemote = fetchDocuments(session)
            val tombstoneDeletes = applyDeletionTombstones(initialRemote, localUser)
            val localBefore = localDocuments()
            val remoteBefore = initialRemote.filterNot { it.content.isDeletionTombstone() }
            val publishableLocal = alignChargeTypeMetadataForPublish(localBefore, remoteBefore)
            var uploaded = 0
            var skippedLocal = 0

            if (canPublish) {
                uploaded = publishBatch(publishableLocal, session).optInt("inserted", 0)
            } else {
                val remoteKeys = remoteBefore.map { it.key }.toSet()
                skippedLocal = localBefore.count { it.key !in remoteKeys }
            }

            val remote = fetchDocuments(session).filterNot { it.content.isDeletionTombstone() }
            val local = localDocuments().associateBy { it.key }
            var downloaded = tombstoneDeletes
            var unchanged = 0
            val conflicts = linkedMapOf<DocumentKey, SalesAuxiliaryConflict>()

            for (doc in remote.sortedWith(compareBy<RemoteDocument> { precedence(it.entityType) }.thenBy { it.entityKey })) {
                val key = doc.key
                val localDoc = local[key]
                if (localDoc == null) {
                    applyRemoteDocument(doc, localUser, forceReplace = true)
                    downloaded++
                } else {
                    val diffs = businessDifferences(doc.entityType, localDoc.content, doc.content)
                    if (diffs.isEmpty()) unchanged++
                    else conflicts[key] = SalesAuxiliaryConflict(doc.entityType, doc.entityKey, diffs.take(MAX_CONFLICT_DIFFERENCES))
                }
            }

            // Include conflicts raised by another device even when this device currently equals canonical cloud.
            fetchOpenConflicts(session).forEach { row ->
                val type = row.optString("entity_type")
                val keyText = row.optString("entity_key")
                val key = DocumentKey(type, keyText)
                val cloud = row.optJSONObject("cloud_content") ?: JSONObject()
                val incoming = row.optJSONObject("incoming_content") ?: JSONObject()
                if (cloud.isDeletionTombstone() || incoming.isDeletionTombstone()) {
                    return@forEach
                }
                val diffs = businessDifferences(type, incoming, cloud)
                if (type == TYPE_CHARGE_TYPE && diffs.isEmpty()) {
                    if (role in AUTHORITY_ROLES) resolveMetadataOnlyChargeTypeConflict(keyText, session)
                    return@forEach
                }
                if (key !in conflicts) {
                    conflicts[key] = SalesAuxiliaryConflict(type, keyText, diffs.take(MAX_CONFLICT_DIFFERENCES))
                }
            }

            val completedAt = System.currentTimeMillis()
            val conflictList = conflicts.values.toList()
            store.saveConflicts(localUser.id, conflictList)
            store.markSuccess(localUser.id, completedAt)
            val result = SalesAuxiliarySyncResult(
                uploadedDocuments = uploaded,
                downloadedDocuments = downloaded,
                unchangedDocuments = unchanged,
                conflicts = conflictList.size,
                skippedLocalDocuments = skippedLocal,
                completedAtEpochMillis = completedAt,
                conflictDetails = conflictList,
            )
            if (downloaded > 0 || conflictList.isNotEmpty() || uploaded > 0) {
                db.governanceDao().insertAudit(
                    AuditEventEntity(
                        userId = localUser.id,
                        action = "CLOUD_SALES_AUX_SYNC",
                        entityType = "SYSTEM",
                        entityId = session.requireOrganizationId(),
                        newValue = "uploaded=$uploaded;downloaded=$downloaded;unchanged=$unchanged;conflicts=${conflictList.size};skippedLocal=$skippedLocal",
                        reason = "AdditionalCharges + Shipment Tracking bidirectional cloud sync",
                        deviceInfo = "ANDROID_CLOUD_SYNC",
                    )
                )
            }
            CloudOperationResult.Success(result)
        } catch (error: Throwable) {
            CloudOperationResult.Failure(error.message?.takeIf { it.isNotBlank() } ?: "تعذر مزامنة الرسوم والشحنات")
        }
    }

    /**
     * v200 durable support-test deletion. The existing v179 cloud document row is retained at the
     * same natural key but its canonical content becomes a tombstone. This works with the already
     * deployed Supabase schema and prevents a stale phone from resurrecting the test expense.
     */
    internal suspend fun publishSupportDeletionForShipmentExpense(
        localUser: UserEntity,
        session: CloudSession,
        cloudRole: String,
        expenseId: Long,
        reason: String,
    ) {
        require(cloudRole.trim().uppercase() in AUTHORITY_ROLES) {
            "حذف بيانات الاختبار السحابي يتطلب OWNER أو ADMIN أو ACCOUNTANT"
        }
        val expense = requireNotNull(db.shipmentDao().expenseById(expenseId)) { "مصروف الشحنة غير موجود" }
        val shipment = requireNotNull(db.shipmentDao().shipmentById(expense.shipmentId)) { "الشحنة المرتبطة بالمصروف غير موجودة" }
        val key = DocumentKey(TYPE_SHIPMENT_EXPENSE, naturalKey(shipment.shipmentNo, expense.paymentVoucherNo))
        val tombstone = shipmentExpenseContent(expense, shipment.shipmentNo)
            .put(TOMBSTONE_DELETED_FIELD, true)
            .put(TOMBSTONE_SCOPE_FIELD, TOMBSTONE_SCOPE_TEST_SUPPORT)
            .put(TOMBSTONE_VERSION_FIELD, TOMBSTONE_VERSION)
            .put(TOMBSTONE_DELETED_AT_FIELD, System.currentTimeMillis())
            .put(TOMBSTONE_REASON_HASH_FIELD, sha256Hex(reason.trim()))

        publishBatch(listOf(LocalDocument(key.entityType, key.entityKey, tombstone)), session)
        var remote = fetchDocument(key, session) ?: error("تعذر العثور على مصروف الشحنة في السحابة بعد نشر الحذف")
        if (!remote.content.isSupportTestDeletionTombstone()) {
            val payload = JSONObject()
                .put("target_organization_id", session.requireOrganizationId())
                .put("target_entity_type", key.entityType)
                .put("target_entity_key", key.entityKey)
                .put("target_resolution", "KEEP_LOCAL")
                .put("replacement_content", tombstone)
            callRpc("fush_resolve_sales_aux_conflict", payload, session)
            remote = fetchDocument(key, session) ?: error("تعذر التحقق من Tombstone مصروف الشحنة")
        }
        require(remote.content.isSupportTestDeletionTombstone()) {
            "فشل تثبيت Tombstone مصروف الشحنة في السحابة"
        }
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = localUser.id,
                action = "CLOUD_SUPPORT_TEST_DELETE_PUBLISHED",
                entityType = TYPE_SHIPMENT_EXPENSE,
                entityId = key.entityKey,
                newValue = "tombstone=v$TOMBSTONE_VERSION",
                reason = "Support test cleanup tombstone",
                deviceInfo = "ANDROID_CLOUD_SYNC_V200",
            )
        )
    }

    /**
     * v201 ADMIN delete for an unused shipment.  Hard deletion is allowed only when the shipment
     * has never acquired an expense or invoice allocation locally or in canonical cloud state.
     * We publish tombstones for the shipment items and parent shipment so another synced device
     * cannot resurrect the deleted tracking row.
     */
    internal suspend fun publishAdminDeletionForUnusedShipment(
        localUser: UserEntity,
        session: CloudSession,
        cloudRole: String,
        shipmentId: Long,
        reason: String,
    ) {
        require(cloudRole.trim().uppercase() in setOf("OWNER", "ADMIN")) {
            "حذف الشحنة السحابي يتطلب OWNER أو ADMIN"
        }
        val dao = db.shipmentDao()
        val shipment = requireNotNull(dao.shipmentById(shipmentId)) { "الشحنة غير موجودة" }
        require(dao.shipmentExpenseCountAll(shipmentId) == 0) { "لا يمكن حذف شحنة لديها مصاريف" }
        require(dao.shipmentItemAllocationCountAll(shipmentId) == 0) { "لا يمكن حذف شحنة مرتبطة بفواتير" }
        require(dao.shipmentExpenseAllocationCountAll(shipmentId) == 0) { "لا يمكن حذف شحنة لديها تخصيصات تكلفة" }

        val remote = fetchDocuments(session).filterNot { it.content.isDeletionTombstone() }
        val remoteUse = remote.filter { doc ->
            val remoteShipmentNo = doc.content.optString("shipment_no")
            remoteShipmentNo.equals(shipment.shipmentNo, ignoreCase = true) &&
                doc.entityType in setOf(TYPE_SHIPMENT_EXPENSE, TYPE_SHIPMENT_ITEM_ALLOCATION, TYPE_SHIPMENT_EXPENSE_ALLOCATION)
        }
        require(remoteUse.isEmpty()) {
            val types = remoteUse.map { it.entityType }.distinct().joinToString()
            "لا يمكن حذف الشحنة: توجد استخدامات سحابية مرتبطة بها ($types). نفّذ مزامنة الكل أولاً."
        }

        val deletedAt = System.currentTimeMillis()
        val reasonHash = sha256Hex(reason.trim())
        val tombstones = mutableListOf<LocalDocument>()
        dao.itemsForShipment(shipmentId).forEach { row ->
            val item = requireNotNull(db.itemDao().byId(row.itemId)) { "صنف الشحنة غير موجود" }
            val content = shipmentItemContent(row, shipment.shipmentNo, item.code)
                .markDeletionTombstone(TOMBSTONE_SCOPE_ADMIN_UNUSED_SHIPMENT, deletedAt, reasonHash)
            tombstones += LocalDocument(
                TYPE_SHIPMENT_ITEM,
                naturalKey(shipment.shipmentNo, item.code, row.lotNo),
                content,
            )
        }
        // A second device may have uploaded an item line that this phone has not hydrated yet.
        // Tombstone every canonical cloud child as well, otherwise the orphan child could survive
        // the parent deletion and fail/reappear on the next bidirectional sync.
        val existingItemKeys = tombstones.filter { it.entityType == TYPE_SHIPMENT_ITEM }.map { it.key }.toMutableSet()
        remote.filter { doc ->
            doc.entityType == TYPE_SHIPMENT_ITEM &&
                doc.content.optString("shipment_no").equals(shipment.shipmentNo, ignoreCase = true)
        }.forEach { doc ->
            if (doc.key !in existingItemKeys) {
                val content = JSONObject(doc.content.toString())
                    .markDeletionTombstone(TOMBSTONE_SCOPE_ADMIN_UNUSED_SHIPMENT, deletedAt, reasonHash)
                tombstones += LocalDocument(doc.entityType, doc.entityKey, content)
                existingItemKeys += doc.key
            }
        }
        val shipmentTombstone = shipmentContent(shipment)
            .markDeletionTombstone(TOMBSTONE_SCOPE_ADMIN_UNUSED_SHIPMENT, deletedAt, reasonHash)
        tombstones += LocalDocument(TYPE_SHIPMENT, shipment.shipmentNo.uppercase(), shipmentTombstone)

        publishBatch(tombstones, session)
        tombstones.forEach { local ->
            val key = local.key
            var canonical = fetchDocument(key, session) ?: error("تعذر العثور على ${key.entityType} في السحابة بعد نشر الحذف")
            if (!canonical.content.isAdminUnusedShipmentDeletionTombstone()) {
                val payload = JSONObject()
                    .put("target_organization_id", session.requireOrganizationId())
                    .put("target_entity_type", key.entityType)
                    .put("target_entity_key", key.entityKey)
                    .put("target_resolution", "KEEP_LOCAL")
                    .put("replacement_content", local.content)
                callRpc("fush_resolve_sales_aux_conflict", payload, session)
                canonical = fetchDocument(key, session) ?: error("تعذر التحقق من Tombstone الشحنة")
            }
            require(canonical.content.isAdminUnusedShipmentDeletionTombstone()) {
                "فشل تثبيت حذف الشحنة في السحابة: ${key.entityType}/${key.entityKey}"
            }
        }
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = localUser.id,
                action = "CLOUD_UNUSED_SHIPMENT_DELETE_PUBLISHED",
                entityType = TYPE_SHIPMENT,
                entityId = shipment.shipmentNo,
                newValue = "items=${tombstones.count { it.entityType == TYPE_SHIPMENT_ITEM }};tombstone=v$TOMBSTONE_VERSION",
                reason = "Admin deleted unused shipment",
                deviceInfo = "ANDROID_CLOUD_SYNC_V201",
            )
        )
    }

    /** Apply only deletion tombstones; used as an early pre-pass by unified sync. */
    internal suspend fun applyDeletionTombstones(
        localUser: UserEntity,
        session: CloudSession,
        cloudRole: String,
    ): Int {
        cloudRole.trim() // retained for diagnostic symmetry with normal sync
        return applyDeletionTombstones(fetchDocuments(session), localUser)
    }

    private suspend fun applyDeletionTombstones(remote: List<RemoteDocument>, localUser: UserEntity): Int {
        var deleted = 0
        remote.asSequence()
            .filter { it.content.isDeletionTombstone() }
            .sortedWith(compareBy<RemoteDocument> { deletionPrecedence(it.entityType) }.thenBy { it.entityKey })
            .forEach { doc ->
                when (doc.entityType) {
                    TYPE_SHIPMENT_EXPENSE -> {
                        if (!doc.content.isSupportTestDeletionTombstone()) return@forEach
                        val shipmentNo = doc.content.optString("shipment_no").ifBlank { doc.entityKey.substringBefore('|') }
                        val voucherNo = doc.content.optString("payment_voucher_no").ifBlank { doc.entityKey.substringAfter('|', "") }
                        val shipment = db.shipmentDao().shipmentByNo(shipmentNo) ?: return@forEach
                        val expense = db.shipmentDao().expenseByNaturalKey(shipment.id, voucherNo) ?: return@forEach
                        db.withTransaction {
                            val sqlite = db.openHelper.writableDatabase
                            sqlite.execSQL(
                                "DELETE FROM sales_shipment_expense_invoice_allocations WHERE shipmentExpenseId=?",
                                arrayOf(expense.id),
                            )
                            sqlite.execSQL("DELETE FROM sales_shipment_expenses WHERE id=?", arrayOf(expense.id))
                            db.governanceDao().insertAudit(
                                AuditEventEntity(
                                    userId = localUser.id,
                                    action = "CLOUD_SUPPORT_TEST_DELETE_APPLIED",
                                    entityType = TYPE_SHIPMENT_EXPENSE,
                                    entityId = doc.entityKey,
                                    oldValue = "expenseId=${expense.id};voucher=$voucherNo",
                                    newValue = "DELETED_BY_TOMBSTONE",
                                    reason = "Support test cleanup replicated from cloud",
                                    deviceInfo = "ANDROID_CLOUD_SYNC_V200",
                                )
                            )
                        }
                        deleted++
                    }
                    TYPE_SHIPMENT_ITEM -> {
                        if (!doc.content.isAdminUnusedShipmentDeletionTombstone()) return@forEach
                        val shipmentNo = doc.content.optString("shipment_no").ifBlank { doc.entityKey.substringBefore('|') }
                        val shipment = db.shipmentDao().shipmentByNo(shipmentNo) ?: return@forEach
                        require(db.shipmentDao().shipmentItemAllocationCountAll(shipment.id) == 0) {
                            "CLOUD_UNUSED_SHIPMENT_DELETE_BLOCKED_BY_LOCAL_INVOICE:${shipment.shipmentNo}"
                        }
                        val itemCode = doc.content.optString("item_code")
                        val item = db.itemDao().byCode(itemCode) ?: return@forEach
                        val lotNo = doc.content.optString("lot_no")
                        val shipmentItem = db.shipmentDao().itemByNaturalKey(shipment.id, item.id, lotNo) ?: return@forEach
                        db.withTransaction {
                            db.openHelper.writableDatabase.execSQL("DELETE FROM sales_shipment_items WHERE id=?", arrayOf(shipmentItem.id))
                            db.governanceDao().insertAudit(
                                AuditEventEntity(
                                    userId = localUser.id,
                                    action = "CLOUD_UNUSED_SHIPMENT_ITEM_DELETE_APPLIED",
                                    entityType = TYPE_SHIPMENT_ITEM,
                                    entityId = doc.entityKey,
                                    oldValue = "shipmentItemId=${shipmentItem.id}",
                                    newValue = "DELETED_BY_TOMBSTONE",
                                    reason = "Unused shipment deletion replicated from cloud",
                                    deviceInfo = "ANDROID_CLOUD_SYNC_V201",
                                )
                            )
                        }
                        deleted++
                    }
                    TYPE_SHIPMENT -> {
                        if (!doc.content.isAdminUnusedShipmentDeletionTombstone()) return@forEach
                        val shipmentNo = doc.content.optString("shipment_no").ifBlank { doc.entityKey }
                        val shipment = db.shipmentDao().shipmentByNo(shipmentNo) ?: return@forEach
                        val dao = db.shipmentDao()
                        require(dao.shipmentExpenseCountAll(shipment.id) == 0) {
                            "CLOUD_UNUSED_SHIPMENT_DELETE_BLOCKED_BY_LOCAL_EXPENSE:${shipment.shipmentNo}"
                        }
                        require(dao.shipmentItemAllocationCountAll(shipment.id) == 0) {
                            "CLOUD_UNUSED_SHIPMENT_DELETE_BLOCKED_BY_LOCAL_INVOICE:${shipment.shipmentNo}"
                        }
                        require(dao.shipmentExpenseAllocationCountAll(shipment.id) == 0) {
                            "CLOUD_UNUSED_SHIPMENT_DELETE_BLOCKED_BY_LOCAL_COST_ALLOCATION:${shipment.shipmentNo}"
                        }
                        db.withTransaction {
                            val removed = dao.deleteShipmentById(shipment.id)
                            if (removed > 0) {
                                db.governanceDao().insertAudit(
                                    AuditEventEntity(
                                        userId = localUser.id,
                                        action = "CLOUD_UNUSED_SHIPMENT_DELETE_APPLIED",
                                        entityType = TYPE_SHIPMENT,
                                        entityId = shipment.shipmentNo,
                                        oldValue = "shipmentId=${shipment.id}",
                                        newValue = "DELETED_BY_TOMBSTONE",
                                        reason = "Unused shipment deletion replicated from cloud",
                                        deviceInfo = "ANDROID_CLOUD_SYNC_V201",
                                    )
                                )
                            }
                        }
                        deleted++
                    }
                }
            }
        return deleted
    }

    suspend fun resolveConflict(
        localUser: UserEntity,
        session: CloudSession,
        cloudRole: String,
        conflict: SalesAuxiliaryConflict,
        resolution: SalesAuxiliaryConflictResolution,
    ): CloudOperationResult<SalesAuxiliarySyncResult> {
        return try {
            require(cloudRole.trim().uppercase() in AUTHORITY_ROLES) { "حل تعارضات الرسوم والشحنات يتطلب OWNER أو ADMIN أو ACCOUNTANT" }
            val key = DocumentKey(conflict.entityType, conflict.entityKey)
            val local = localDocuments().associateBy { it.key }[key]
            val payload = JSONObject()
                .put("target_organization_id", session.requireOrganizationId())
                .put("target_entity_type", conflict.entityType)
                .put("target_entity_key", conflict.entityKey)
            when (resolution) {
                SalesAuxiliaryConflictResolution.KEEP_LOCAL -> {
                    requireNotNull(local) { "المستند المحلي غير موجود" }
                    payload.put("target_resolution", "KEEP_LOCAL").put("replacement_content", local.content)
                    callRpc("fush_resolve_sales_aux_conflict", payload, session)
                }
                SalesAuxiliaryConflictResolution.USE_CLOUD -> {
                    payload.put("target_resolution", "KEEP_CLOUD").put("replacement_content", JSONObject.NULL)
                    callRpc("fush_resolve_sales_aux_conflict", payload, session)
                    val remote = fetchDocument(key, session) ?: error("المستند السحابي غير موجود")
                    applyRemoteDocument(remote, localUser, forceReplace = true)
                }
            }
            sync(localUser, session, cloudRole)
        } catch (error: Throwable) {
            CloudOperationResult.Failure(error.message?.takeIf { it.isNotBlank() } ?: "تعذر حل تعارض الرسوم والشحنات")
        }
    }

    private suspend fun localDocuments(): List<LocalDocument> {
        val out = mutableListOf<LocalDocument>()
        val chargeDao = db.additionalChargesDao()
        val shipmentDao = db.shipmentDao()

        for (row in chargeDao.allTypes()) {
            out += LocalDocument(TYPE_CHARGE_TYPE, row.code.uppercase(), chargeTypeContent(row))
        }
        for (row in chargeDao.allChargesForCloudSync()) {
            out += LocalDocument(TYPE_CHARGE, row.chargeNo.uppercase(), chargeContent(row))
        }
        for (row in chargeDao.allPaymentsForCloudSync()) {
            out += LocalDocument(TYPE_CHARGE_PAYMENT, row.paymentNo.uppercase(), chargePaymentContent(row))
        }
        for (row in chargeDao.allSettlementsForCloudSync()) {
            val chargeNo = requireNotNull(chargeDao.chargeById(row.chargeId)) { "رسم التسوية غير موجود" }.chargeNo
            val invoiceNo = requireNotNull(db.salesDao().invoiceById(row.invoiceId)) { "فاتورة تسوية الرسم غير موجودة" }.invoiceNo
            out += LocalDocument(TYPE_CHARGE_SETTLEMENT, naturalKey(chargeNo, invoiceNo), chargeSettlementContent(row, chargeNo, invoiceNo))
        }

        for (row in shipmentDao.allShipmentsForCloudSync()) {
            out += LocalDocument(TYPE_SHIPMENT, row.shipmentNo.uppercase(), shipmentContent(row))
        }
        for (row in shipmentDao.allItemsForCloudSync()) {
            val shipment = requireNotNull(shipmentDao.shipmentById(row.shipmentId)) { "الشحنة غير موجودة" }
            val item = requireNotNull(db.itemDao().byId(row.itemId)) { "صنف الشحنة غير موجود" }
            out += LocalDocument(TYPE_SHIPMENT_ITEM, naturalKey(shipment.shipmentNo, item.code, row.lotNo), shipmentItemContent(row, shipment.shipmentNo, item.code))
        }
        for (row in shipmentDao.allExpensesForCloudSync()) {
            val shipment = requireNotNull(shipmentDao.shipmentById(row.shipmentId)) { "الشحنة غير موجودة" }
            out += LocalDocument(TYPE_SHIPMENT_EXPENSE, naturalKey(shipment.shipmentNo, row.paymentVoucherNo), shipmentExpenseContent(row, shipment.shipmentNo))
        }
        for (row in shipmentDao.allItemAllocationsForCloudSync()) {
            val shipmentItem = requireNotNull(shipmentDao.itemById(row.shipmentItemId)) { "سطر الشحنة غير موجود" }
            val shipment = requireNotNull(shipmentDao.shipmentById(shipmentItem.shipmentId)) { "الشحنة غير موجودة" }
            val item = requireNotNull(db.itemDao().byId(shipmentItem.itemId)) { "صنف الشحنة غير موجود" }
            val invoice = requireNotNull(db.salesDao().invoiceById(row.invoiceId)) { "فاتورة تخصيص الشحنة غير موجودة" }
            val invoiceLineNo = row.salesLineId?.let { lineId ->
                db.salesDao().linesForInvoice(invoice.id).sortedBy { it.id }.indexOfFirst { it.id == lineId }.takeIf { it >= 0 }?.plus(1)
                    ?: error("سطر فاتورة تخصيص الشحنة غير موجود")
            }
            val key = if (invoiceLineNo == null) naturalKey(shipment.shipmentNo, item.code, shipmentItem.lotNo, invoice.invoiceNo)
                else naturalKey(shipment.shipmentNo, item.code, shipmentItem.lotNo, invoice.invoiceNo, invoiceLineNo.toString())
            out += LocalDocument(TYPE_SHIPMENT_ITEM_ALLOCATION, key, shipmentItemAllocationContent(row, shipment.shipmentNo, item.code, shipmentItem.lotNo, invoice.invoiceNo, invoiceLineNo))
        }
        for (row in shipmentDao.allExpenseAllocationsForCloudSync()) {
            val expense = requireNotNull(shipmentDao.expenseById(row.shipmentExpenseId)) { "مصروف الشحنة غير موجود" }
            val shipment = requireNotNull(shipmentDao.shipmentById(expense.shipmentId)) { "الشحنة غير موجودة" }
            val invoice = requireNotNull(db.salesDao().invoiceById(row.invoiceId)) { "فاتورة تخصيص مصروف الشحنة غير موجودة" }
            out += LocalDocument(TYPE_SHIPMENT_EXPENSE_ALLOCATION, naturalKey(shipment.shipmentNo, expense.paymentVoucherNo, invoice.invoiceNo), shipmentExpenseAllocationContent(row, shipment.shipmentNo, expense.paymentVoucherNo, invoice.invoiceNo))
        }
        return out
    }

    private suspend fun chargeTypeContent(row: AdditionalChargeTypeEntity) = JSONObject()
        .put("code", row.code).put("name_ar", row.nameAr).put("name_en", row.nameEn)
        .put("default_bearer", row.defaultBearer).put("default_accounting_treatment", row.defaultAccountingTreatment)
        .put("principal_agent_mode", row.principalAgentMode)
        .put("recoverable_account_code", accountCode(row.recoverableAccountId))
        .put("expense_account_code", accountCode(row.expenseAccountId))
        .put("revenue_account_code", accountCode(row.revenueAccountId))
        .put("payable_account_code", accountCode(row.payableAccountId))
        .put("is_active", row.isActive).put("created_at_ms", row.createdAt)

    private suspend fun chargeContent(row: SalesAdditionalChargeEntity): JSONObject {
        val customer = requireNotNull(db.customerDao().byId(row.customerId)) { "عميل الرسم غير موجود" }
        val type = requireNotNull(db.additionalChargesDao().typeById(row.chargeTypeId)) { "نوع الرسم غير موجود" }
        return JSONObject()
            .put("charge_no", row.chargeNo).put("customer_code", customer.code).put("charge_type_code", type.code)
            .put("charge_date_ms", row.chargeDate).put("description", row.description).put("amount_original", row.amountOriginal)
            .put("currency_code", row.currencyCode).put("exchange_rate", row.exchangeRate).put("amount_base", row.amountBase)
            .put("bearer", row.bearer).put("payment_status", row.paymentStatus).put("paid_by", row.paidBy)
            .put("accounting_treatment", row.accountingTreatment).put("principal_agent_mode_snapshot", row.principalAgentModeSnapshot)
            .put("recoverable_account_code", accountCode(row.recoverableAccountId)).put("expense_account_code", accountCode(row.expenseAccountId))
            .put("revenue_account_code", accountCode(row.revenueAccountId)).put("payable_account_code", accountCode(row.payableAccountId))
            .put("status", row.status).put("notes", row.notes).put("created_at_ms", row.createdAt)
            .putNullable("cancelled_at_ms", row.cancelledAt).put("cancellation_reason", row.cancellationReason)
    }

    private suspend fun chargePaymentContent(row: SalesAdditionalChargePaymentEntity): JSONObject {
        val charge = requireNotNull(db.additionalChargesDao().chargeById(row.chargeId)) { "رسم الدفعة غير موجود" }
        val treasuryCode = row.treasuryAccountId?.let { db.accountingDao().treasuryById(it)?.code ?: error("خزينة دفعة الرسم غير موجودة") }
        val reversalNo = row.reversalOfPaymentId?.let { db.additionalChargesDao().paymentById(it)?.paymentNo ?: error("دفعة الرسم الأصلية غير موجودة") }
        return JSONObject().put("payment_no", row.paymentNo).put("charge_no", charge.chargeNo)
            .put("payment_date_ms", row.paymentDate).put("amount_original", row.amountOriginal).put("currency_code", row.currencyCode)
            .put("exchange_rate", row.exchangeRate).put("amount_base", row.amountBase).put("paid_by", row.paidBy)
            .putNullable("treasury_code", treasuryCode).put("payment_reference", row.paymentReference).put("notes", row.notes)
            .putNullable("reversal_of_payment_no", reversalNo).put("created_at_ms", row.createdAt)
    }

    private fun chargeSettlementContent(row: SalesAdditionalChargeSettlementEntity, chargeNo: String, invoiceNo: String) = JSONObject()
        .put("charge_no", chargeNo).put("invoice_no", invoiceNo).put("amount_charge_original", row.amountChargeOriginal)
        .put("amount_invoice_original", row.amountInvoiceOriginal).put("amount_base", row.amountBase).put("settlement_date_ms", row.settlementDate)
        .put("status", row.status).put("created_at_ms", row.createdAt).putNullable("reversed_at_ms", row.reversedAt).put("reversal_reason", row.reversalReason)

    private suspend fun shipmentContent(row: SalesShipmentEntity): JSONObject {
        val warehouse = requireNotNull(db.warehouseDao().byId(row.fromWarehouseId)) { "مخزن الشحنة غير موجود" }
        return JSONObject().put("shipment_no", row.shipmentNo).put("shipment_date_ms", row.shipmentDate).put("from_warehouse_code", warehouse.code)
            .put("destination_province", row.destinationProvince)
            .put("destination_governorate_id", row.destinationGovernorateId ?: JSONObject.NULL)
            .put("destination_district_id", row.destinationDistrictId ?: JSONObject.NULL)
            .put("destination_area_id", row.destinationAreaId ?: JSONObject.NULL)
            .put("status", row.status).put("transport_reference", row.transportReference)
            .put("notes", row.notes).put("created_at_ms", row.createdAt).putNullable("closed_at_ms", row.closedAt)
            .putNullable("cancelled_at_ms", row.cancelledAt).put("cancellation_reason", row.cancellationReason)
    }

    private fun shipmentItemContent(row: SalesShipmentItemEntity, shipmentNo: String, itemCode: String) = JSONObject()
        .put("shipment_no", shipmentNo).put("item_code", itemCode).put("lot_no", row.lotNo).put("quantity_base", row.quantityBase).put("created_at_ms", row.createdAt)

    private fun shipmentExpenseContent(row: SalesShipmentExpenseEntity, shipmentNo: String) = JSONObject()
        .put("shipment_no", shipmentNo).put("expense_type", row.expenseType).put("description", row.description).put("expense_date_ms", row.expenseDate)
        .put("amount_original", row.amountOriginal).put("currency_code", row.currencyCode).put("exchange_rate", row.exchangeRate).put("amount_base", row.amountBase)
        .put("bearer", row.bearer).put("payment_method", row.paymentMethod).put("payment_voucher_no", row.paymentVoucherNo).put("payment_reference", row.paymentReference)
        .put("status", row.status).put("created_at_ms", row.createdAt)

    private fun shipmentItemAllocationContent(row: SalesShipmentInvoiceItemAllocationEntity, shipmentNo: String, itemCode: String, lotNo: String, invoiceNo: String, invoiceLineNo: Int?) = JSONObject()
        .put("shipment_no", shipmentNo).put("item_code", itemCode).put("lot_no", lotNo).put("invoice_no", invoiceNo)
        .putNullable("invoice_line_no", invoiceLineNo)
        .put("quantity_base", row.quantityBase).put("status", row.status).put("created_at_ms", row.createdAt)
        .putNullable("reversed_at_ms", row.reversedAt).put("reversal_reason", row.reversalReason)

    private fun shipmentExpenseAllocationContent(row: SalesShipmentExpenseInvoiceAllocationEntity, shipmentNo: String, voucherNo: String, invoiceNo: String) = JSONObject()
        .put("shipment_no", shipmentNo).put("payment_voucher_no", voucherNo).put("invoice_no", invoiceNo).put("amount_base", row.amountBase)
        .put("customer_charge_base", row.customerChargeBase).put("allocation_method", row.allocationMethod).put("basis_quantity_base", row.basisQuantityBase).put("status", row.status)
        .put("created_at_ms", row.createdAt).putNullable("reversed_at_ms", row.reversedAt).put("reversal_reason", row.reversalReason)

    private suspend fun applyRemoteDocument(doc: RemoteDocument, localUser: UserEntity, forceReplace: Boolean) {
        db.withTransaction {
            when (doc.entityType) {
                TYPE_CHARGE_TYPE -> applyChargeType(doc.content, forceReplace)
                TYPE_CHARGE -> applyCharge(doc.content, localUser, forceReplace)
                TYPE_CHARGE_PAYMENT -> applyChargePayment(doc.content, localUser, forceReplace)
                TYPE_CHARGE_SETTLEMENT -> applyChargeSettlement(doc.content, localUser, forceReplace)
                TYPE_SHIPMENT -> applyShipment(doc.content, localUser, forceReplace)
                TYPE_SHIPMENT_ITEM -> applyShipmentItem(doc.content, localUser, forceReplace)
                TYPE_SHIPMENT_EXPENSE -> applyShipmentExpense(doc.content, localUser, forceReplace)
                TYPE_SHIPMENT_ITEM_ALLOCATION -> applyShipmentItemAllocation(doc.content, localUser, forceReplace)
                TYPE_SHIPMENT_EXPENSE_ALLOCATION -> applyShipmentExpenseAllocation(doc.content, localUser, forceReplace)
                else -> error("نوع مستند مزامنة غير مدعوم: ${doc.entityType}")
            }
        }
    }

    private suspend fun applyChargeType(c: JSONObject, force: Boolean) {
        val dao = db.additionalChargesDao()
        val existing = dao.typeByCode(c.getString("code"))
        val row = AdditionalChargeTypeEntity(
            id = existing?.id ?: 0,
            code = c.getString("code"), nameAr = c.optString("name_ar"), nameEn = c.optString("name_en"),
            defaultBearer = c.optString("default_bearer"), defaultAccountingTreatment = c.optString("default_accounting_treatment"),
            principalAgentMode = c.optString("principal_agent_mode"),
            recoverableAccountId = accountId(c.getString("recoverable_account_code")), expenseAccountId = accountId(c.getString("expense_account_code")),
            revenueAccountId = accountId(c.getString("revenue_account_code")), payableAccountId = accountId(c.getString("payable_account_code")),
            isActive = c.optBoolean("is_active", true), createdAt = c.optLong("created_at_ms", System.currentTimeMillis())
        )
        if (existing == null) dao.insertTypesIgnore(listOf(row)) else if (force) dao.updateType(row)
    }

    private suspend fun applyCharge(c: JSONObject, user: UserEntity, force: Boolean) {
        val dao = db.additionalChargesDao()
        val existing = dao.chargeByNo(c.getString("charge_no"))
        val customer = db.customerDao().byCode(c.getString("customer_code")) ?: error("العميل السحابي غير موجود محلياً: ${c.getString("customer_code")}")
        val type = dao.typeByCode(c.getString("charge_type_code")) ?: error("نوع الرسم السحابي غير موجود محلياً")
        val row = SalesAdditionalChargeEntity(
            id=existing?.id?:0, chargeNo=c.getString("charge_no"), customerId=customer.id, chargeTypeId=type.id,
            chargeDate=c.optLong("charge_date_ms"), description=c.optString("description"), amountOriginal=c.optDouble("amount_original"),
            currencyCode=c.optString("currency_code"), exchangeRate=c.optDouble("exchange_rate",1.0), amountBase=c.optDouble("amount_base"),
            bearer=c.optString("bearer"), paymentStatus=c.optString("payment_status"), paidBy=c.optString("paid_by"), accountingTreatment=c.optString("accounting_treatment"),
            principalAgentModeSnapshot=c.optString("principal_agent_mode_snapshot"), recoverableAccountId=accountId(c.getString("recoverable_account_code")),
            expenseAccountId=accountId(c.getString("expense_account_code")), revenueAccountId=accountId(c.getString("revenue_account_code")), payableAccountId=accountId(c.getString("payable_account_code")),
            status=c.optString("status","POSTED"), notes=c.optString("notes"), createdBy=existing?.createdBy?:user.id, createdAt=c.optLong("created_at_ms",System.currentTimeMillis()),
            cancelledBy=if(c.optNullableLong("cancelled_at_ms")!=null) existing?.cancelledBy?:user.id else null, cancelledAt=c.optNullableLong("cancelled_at_ms"), cancellationReason=c.optString("cancellation_reason")
        )
        if(existing==null) dao.insertCharge(row) else if(force) dao.updateCharge(row)
    }

    private suspend fun applyChargePayment(c: JSONObject, user: UserEntity, force: Boolean) {
        val dao=db.additionalChargesDao(); val existing=dao.paymentByNo(c.getString("payment_no"))
        val charge=dao.chargeByNo(c.getString("charge_no")) ?: error("الرسم المرتبط بالدفعة غير موجود")
        val treasuryId=c.optNullableString("treasury_code")?.let { db.accountingDao().treasuryByCode(it)?.id ?: error("الخزينة $it غير موجودة") }
        val reversalId=c.optNullableString("reversal_of_payment_no")?.let { dao.paymentByNo(it)?.id ?: error("دفعة الرسم الأصلية $it غير موجودة") }
        val row=SalesAdditionalChargePaymentEntity(
            id=existing?.id?:0,paymentNo=c.getString("payment_no"),chargeId=charge.id,paymentDate=c.optLong("payment_date_ms"),amountOriginal=c.optDouble("amount_original"),
            currencyCode=c.optString("currency_code"),exchangeRate=c.optDouble("exchange_rate",1.0),amountBase=c.optDouble("amount_base"),paidBy=c.optString("paid_by"),
            treasuryAccountId=treasuryId,paymentReference=c.optString("payment_reference"),notes=c.optString("notes"),reversalOfPaymentId=reversalId,
            createdBy=existing?.createdBy?:user.id,createdAt=c.optLong("created_at_ms",System.currentTimeMillis()))
        if(existing==null) dao.insertPayment(row) else if(force) dao.updatePayment(row)
    }

    private suspend fun applyChargeSettlement(c: JSONObject, user: UserEntity, force: Boolean) {
        val dao=db.additionalChargesDao(); val charge=dao.chargeByNo(c.getString("charge_no"))?:error("رسم التسوية غير موجود")
        val invoice=db.salesDao().invoiceByNo(c.getString("invoice_no"))?:error("فاتورة تسوية الرسم غير موجودة")
        val existing=dao.settlementByLink(charge.id,invoice.id)
        val reversedAt=c.optNullableLong("reversed_at_ms")
        val row=SalesAdditionalChargeSettlementEntity(
            id=existing?.id?:0,chargeId=charge.id,invoiceId=invoice.id,amountChargeOriginal=c.optDouble("amount_charge_original"),amountInvoiceOriginal=c.optDouble("amount_invoice_original"),
            amountBase=c.optDouble("amount_base"),settlementDate=c.optLong("settlement_date_ms"),status=c.optString("status","ACTIVE"),createdBy=existing?.createdBy?:user.id,
            createdAt=c.optLong("created_at_ms",System.currentTimeMillis()),reversedBy=if(reversedAt!=null) existing?.reversedBy?:user.id else null,reversedAt=reversedAt,reversalReason=c.optString("reversal_reason"))
        if(existing==null) dao.insertSettlement(row) else if(force) dao.updateSettlement(row)
    }

    private suspend fun applyShipment(c: JSONObject,user:UserEntity,force:Boolean){
        val dao=db.shipmentDao(); val existing=dao.shipmentByNo(c.getString("shipment_no")); val warehouse=db.warehouseDao().byCode(c.getString("from_warehouse_code"))?:error("مخزن الشحنة غير موجود")
        val province = c.optString("destination_province")
        val governorateId = c.optNullableString("destination_governorate_id")
            ?: db.geographyDao().governorateIdForAlias(YemenGeographyHierarchy.normalizeAlias(province))
        val row=SalesShipmentEntity(id=existing?.id?:0,shipmentNo=c.getString("shipment_no"),shipmentDate=c.optLong("shipment_date_ms"),fromWarehouseId=warehouse.id,destinationProvince=province,destinationGovernorateId=governorateId,destinationDistrictId=c.optNullableString("destination_district_id"),destinationAreaId=c.optNullableString("destination_area_id"),status=c.optString("status","IN_TRANSIT"),transportReference=c.optString("transport_reference"),notes=c.optString("notes"),createdBy=existing?.createdBy?:user.id,createdAt=c.optLong("created_at_ms",System.currentTimeMillis()),closedAt=c.optNullableLong("closed_at_ms"),cancelledAt=c.optNullableLong("cancelled_at_ms"),cancellationReason=c.optString("cancellation_reason"))
        if(existing==null) dao.insertShipment(row) else if(force) dao.updateShipment(row)
    }

    private suspend fun applyShipmentItem(c:JSONObject,user:UserEntity,force:Boolean){
        val dao=db.shipmentDao(); val shipment=dao.shipmentByNo(c.getString("shipment_no"))?:error("الشحنة غير موجودة"); val item=db.itemDao().byCode(c.getString("item_code"))?:error("صنف الشحنة غير موجود")
        val lot=c.optString("lot_no"); val existing=dao.itemByNaturalKey(shipment.id,item.id,lot)
        val row=SalesShipmentItemEntity(id=existing?.id?:0,shipmentId=shipment.id,itemId=item.id,lotNo=lot,quantityBase=c.optDouble("quantity_base"),createdBy=existing?.createdBy?:user.id,createdAt=c.optLong("created_at_ms",System.currentTimeMillis()))
        if(existing==null) dao.insertItem(row) else if(force) dao.updateItem(row)
    }

    private suspend fun applyShipmentExpense(c:JSONObject,user:UserEntity,force:Boolean){
        val dao=db.shipmentDao(); val shipment=dao.shipmentByNo(c.getString("shipment_no"))?:error("الشحنة غير موجودة"); val voucherNo=c.getString("payment_voucher_no")
        // Accounting sync must hydrate this voucher first. Never recreate/post it here.
        val voucher=db.partyDao().voucherByNo(voucherNo)?:error("سند الصرف $voucherNo غير موجود محلياً. شغّل مزامنة الأستاذ والخزينة أولاً")
        val existing=dao.expenseByNaturalKey(shipment.id,voucherNo)
        val row=SalesShipmentExpenseEntity(id=existing?.id?:0,shipmentId=shipment.id,expenseType=c.optString("expense_type"),description=c.optString("description"),expenseDate=c.optLong("expense_date_ms"),amountOriginal=c.optDouble("amount_original"),currencyCode=c.optString("currency_code"),exchangeRate=c.optDouble("exchange_rate",1.0),amountBase=c.optDouble("amount_base"),bearer=c.optString("bearer","COMPANY"),paymentMethod=c.optString("payment_method"),partyVoucherId=voucher.id,paymentVoucherNo=voucherNo,paymentReference=c.optString("payment_reference"),status=c.optString("status","POSTED"),createdBy=existing?.createdBy?:user.id,createdAt=c.optLong("created_at_ms",System.currentTimeMillis()))
        if(existing==null) dao.insertExpense(row) else if(force) dao.updateExpense(row)
    }

    private suspend fun applyShipmentItemAllocation(c:JSONObject,user:UserEntity,force:Boolean){
        val dao=db.shipmentDao(); val shipment=dao.shipmentByNo(c.getString("shipment_no"))?:error("الشحنة غير موجودة"); val item=db.itemDao().byCode(c.getString("item_code"))?:error("صنف الشحنة غير موجود"); val shipmentItem=dao.itemByNaturalKey(shipment.id,item.id,c.optString("lot_no"))?:error("سطر الشحنة غير موجود"); val invoice=db.salesDao().invoiceByNo(c.getString("invoice_no"))?:error("فاتورة التخصيص غير موجودة")
        val shipmentGovernorateId = requireNotNull(shipment.destinationGovernorateId) { "الشحنة ${shipment.shipmentNo} غير مرتبطة بمعرف محافظة رسمي" }
        val invoiceGovernorateId = requireNotNull(invoice.governorateId) { "الفاتورة ${invoice.invoiceNo} غير مرتبطة بمعرف محافظة رسمي" }
        require(shipmentGovernorateId == invoiceGovernorateId) { "محافظة الشحنة لا تطابق محافظة الفاتورة حسب المعرّف الرسمي" }
        val lineNo=if(c.has("invoice_line_no")&&!c.isNull("invoice_line_no"))c.optInt("invoice_line_no") else 0
        val salesLineId=if(lineNo>0) db.salesDao().linesForInvoice(invoice.id).sortedBy{it.id}.getOrNull(lineNo-1)?.id ?: error("سطر الفاتورة السحابي غير موجود") else null
        val existing=dao.itemAllocationByNaturalKey(shipmentItem.id,invoice.id,salesLineId); val reversedAt=c.optNullableLong("reversed_at_ms")
        val row=SalesShipmentInvoiceItemAllocationEntity(id=existing?.id?:0,shipmentItemId=shipmentItem.id,invoiceId=invoice.id,salesLineId=salesLineId,quantityBase=c.optDouble("quantity_base"),status=c.optString("status","ACTIVE"),createdBy=existing?.createdBy?:user.id,createdAt=c.optLong("created_at_ms",System.currentTimeMillis()),reversedAt=reversedAt,reversalReason=c.optString("reversal_reason"))
        if(existing==null) dao.insertItemAllocation(row) else if(force) dao.updateItemAllocation(row)
    }

    private suspend fun applyShipmentExpenseAllocation(c:JSONObject,user:UserEntity,force:Boolean){
        val dao=db.shipmentDao(); val shipment=dao.shipmentByNo(c.getString("shipment_no"))?:error("الشحنة غير موجودة"); val expense=dao.expenseByNaturalKey(shipment.id,c.getString("payment_voucher_no"))?:error("مصروف الشحنة غير موجود"); val invoice=db.salesDao().invoiceByNo(c.getString("invoice_no"))?:error("فاتورة التخصيص غير موجودة"); val existing=dao.expenseAllocationByNaturalKey(expense.id,invoice.id); val reversedAt=c.optNullableLong("reversed_at_ms")
        val row=SalesShipmentExpenseInvoiceAllocationEntity(id=existing?.id?:0,shipmentExpenseId=expense.id,invoiceId=invoice.id,amountBase=c.optDouble("amount_base"),customerChargeBase=c.optDouble("customer_charge_base",0.0),allocationMethod=c.optString("allocation_method","MANUAL"),basisQuantityBase=c.optDouble("basis_quantity_base"),status=c.optString("status","ACTIVE"),createdBy=existing?.createdBy?:user.id,createdAt=c.optLong("created_at_ms",System.currentTimeMillis()),reversedAt=reversedAt,reversalReason=c.optString("reversal_reason"))
        if(existing==null) dao.insertExpenseAllocation(row) else if(force) dao.updateExpenseAllocation(row)
    }

    private suspend fun accountCode(id:Long):String = db.accountDao().byId(id)?.code ?: error("الحساب $id غير موجود")
    private suspend fun accountId(code:String):Long = db.accountDao().byCode(code)?.id ?: error("الحساب $code غير موجود محلياً")

    /**
     * v199: created_at_ms is provenance metadata for charge-type master rows, not a business
     * attribute. Two devices may bootstrap the same fixed type at different times. Preserve the
     * canonical cloud timestamp during publication when all business fields already match so the
     * server does not manufacture a false conflict.
     */
    private fun alignChargeTypeMetadataForPublish(
        local: List<LocalDocument>,
        remote: List<RemoteDocument>,
    ): List<LocalDocument> {
        val remoteByKey = remote.associateBy { it.key }
        return local.map { doc ->
            if (doc.entityType != TYPE_CHARGE_TYPE) return@map doc
            val cloud = remoteByKey[doc.key] ?: return@map doc
            if (businessDifferences(TYPE_CHARGE_TYPE, doc.content, cloud.content).isNotEmpty()) return@map doc
            val aligned = JSONObject(doc.content.toString())
            if (cloud.content.has("created_at_ms") && !cloud.content.isNull("created_at_ms")) {
                aligned.put("created_at_ms", cloud.content.get("created_at_ms"))
            } else {
                aligned.remove("created_at_ms")
            }
            LocalDocument(doc.entityType, doc.entityKey, aligned)
        }
    }

    private fun resolveMetadataOnlyChargeTypeConflict(entityKey: String, session: CloudSession) {
        val payload = JSONObject()
            .put("target_organization_id", session.requireOrganizationId())
            .put("target_entity_type", TYPE_CHARGE_TYPE)
            .put("target_entity_key", entityKey)
            .put("target_resolution", "KEEP_CLOUD")
            .put("replacement_content", JSONObject.NULL)
        callRpc("fush_resolve_sales_aux_conflict", payload, session)
    }

    private fun publishBatch(local: List<LocalDocument>, session: CloudSession): JSONObject {
        if(local.isEmpty()) return JSONObject().put("inserted",0)
        val docs=JSONArray(); local.forEach { d -> docs.put(JSONObject().put("entity_type",d.entityType).put("entity_key",d.entityKey).put("content",d.content)) }
        return callRpc("fush_publish_sales_aux_batch",JSONObject().put("target_organization_id",session.requireOrganizationId()).put("documents",docs),session)
    }

    private fun callRpc(name:String,payload:JSONObject,session:CloudSession):JSONObject = when(val r=request("POST","/rest/v1/rpc/$name",payload.toString(),session.accessToken,null)){
        is HttpResult.Error -> error(apiErrorMessage(r)); is HttpResult.Ok -> runCatching { JSONObject(r.body) }.getOrElse { error("استجابة RPC غير صالحة") }
    }

    private fun fetchDocuments(session:CloudSession):List<RemoteDocument>{
        val org=encode(session.requireOrganizationId()); val rows=fetchRows("fush_tx_sales_aux_documents?select=entity_type,entity_key,content&organization_id=eq.$org&limit=20000",session)
        return rows.map { RemoteDocument(it.getString("entity_type"),it.getString("entity_key"),it.getJSONObject("content")) }
    }
    private fun fetchDocument(key:DocumentKey,session:CloudSession):RemoteDocument?{
        val org=encode(session.requireOrganizationId()); val type=encode(key.entityType); val ek=encode(key.entityKey)
        return fetchRows("fush_tx_sales_aux_documents?select=entity_type,entity_key,content&organization_id=eq.$org&entity_type=eq.$type&entity_key=eq.$ek&limit=1",session).firstOrNull()?.let{RemoteDocument(it.getString("entity_type"),it.getString("entity_key"),it.getJSONObject("content"))}
    }
    private fun fetchOpenConflicts(session:CloudSession):List<JSONObject>{
        val org=encode(session.requireOrganizationId())
        return fetchRows("fush_sales_aux_sync_conflicts?select=entity_type,entity_key,cloud_content,incoming_content&organization_id=eq.$org&status=eq.OPEN&limit=1000",session)
    }
    private fun fetchRows(pathAndQuery:String,session:CloudSession):List<JSONObject> = when(val r=request("GET","/rest/v1/$pathAndQuery",null,session.accessToken,null)){
        is HttpResult.Error -> { if(r.code==404) error("مخطط المزامنة الموحدة في Supabase غير مكتمل. شغّل FUSH_ERP_Mobile_v213-CommercialMultiTenant-Supabase.sql مرة واحدة ثم أعد مزامنة الكل."); error(apiErrorMessage(r)) }
        is HttpResult.Ok -> { val a=runCatching{JSONArray(r.body)}.getOrElse{error("استجابة السحابة غير صالحة")}; List(a.length()){a.getJSONObject(it)} }
    }

    private fun differences(local:JSONObject,cloud:JSONObject):List<SalesAuxiliaryConflictDifference> =
        businessDifferences(entityType = "", local = local, cloud = cloud)

    private fun businessDifferences(entityType:String,local:JSONObject,cloud:JSONObject):List<SalesAuxiliaryConflictDifference>{
        val ignored = if (entityType == TYPE_CHARGE_TYPE) CHARGE_TYPE_METADATA_FIELDS else emptySet()
        val keys=linkedSetOf<String>(); local.keys().forEachRemaining{keys+=it}; cloud.keys().forEachRemaining{keys+=it}
        return keys.sorted().mapNotNull { k ->
            if (k in ignored) return@mapNotNull null
            val l=local.opt(k); val c=cloud.opt(k)
            if(valuesEqual(l,c)) null else SalesAuxiliaryConflictDifference(k,printable(l),printable(c))
        }
    }
    private fun valuesEqual(a:Any?,b:Any?):Boolean{
        if((a==null||a===JSONObject.NULL)&&(b==null||b===JSONObject.NULL)) return true
        if(a is Number && b is Number) return runCatching{BigDecimal(a.toString()).compareTo(BigDecimal(b.toString()))==0}.getOrDefault(a.toDouble()==b.toDouble())
        return a?.toString()==b?.toString()
    }
    private fun printable(v:Any?):String=if(v==null||v===JSONObject.NULL) "∅" else v.toString()
    private fun JSONObject.isDeletionTombstone(): Boolean =
        optBoolean(TOMBSTONE_DELETED_FIELD, false) &&
            optString(TOMBSTONE_SCOPE_FIELD) in setOf(TOMBSTONE_SCOPE_TEST_SUPPORT, TOMBSTONE_SCOPE_ADMIN_UNUSED_SHIPMENT) &&
            optInt(TOMBSTONE_VERSION_FIELD, 0) >= 1

    private fun JSONObject.isSupportTestDeletionTombstone(): Boolean =
        isDeletionTombstone() && optString(TOMBSTONE_SCOPE_FIELD).equals(TOMBSTONE_SCOPE_TEST_SUPPORT, ignoreCase = true)

    private fun JSONObject.isAdminUnusedShipmentDeletionTombstone(): Boolean =
        isDeletionTombstone() && optString(TOMBSTONE_SCOPE_FIELD).equals(TOMBSTONE_SCOPE_ADMIN_UNUSED_SHIPMENT, ignoreCase = true)

    private fun JSONObject.markDeletionTombstone(scope: String, deletedAt: Long, reasonHash: String): JSONObject =
        put(TOMBSTONE_DELETED_FIELD, true)
            .put(TOMBSTONE_SCOPE_FIELD, scope)
            .put(TOMBSTONE_VERSION_FIELD, TOMBSTONE_VERSION)
            .put(TOMBSTONE_DELETED_AT_FIELD, deletedAt)
            .put(TOMBSTONE_REASON_HASH_FIELD, reasonHash)

    private fun deletionPrecedence(type: String): Int = when (type) {
        TYPE_SHIPMENT_EXPENSE -> 0
        TYPE_SHIPMENT_ITEM -> 1
        TYPE_SHIPMENT -> 2
        else -> 99
    }

    private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun request(method:String,path:String,body:String?,token:String?,prefer:String?):HttpResult{
        val connection=(URL(BuildConfig.SUPABASE_URL.trimEnd('/')+path).openConnection() as HttpURLConnection).apply{
            requestMethod=method; connectTimeout=15_000; readTimeout=30_000; setRequestProperty("apikey",BuildConfig.SUPABASE_PUBLISHABLE_KEY); setRequestProperty("Accept","application/json")
            if(!token.isNullOrBlank()) setRequestProperty("Authorization","Bearer $token"); if(!prefer.isNullOrBlank()) setRequestProperty("Prefer",prefer)
            if(body!=null){doOutput=true;setRequestProperty("Content-Type","application/json");outputStream.bufferedWriter(Charsets.UTF_8).use{it.write(body)}}
        }
        return try{val code=connection.responseCode;val stream=if(code in 200..299)connection.inputStream else connection.errorStream;val text=stream?.bufferedReader(Charsets.UTF_8)?.use{it.readText()}.orEmpty();if(code in 200..299)HttpResult.Ok(code,text)else HttpResult.Error(code,text)}finally{connection.disconnect()}
    }
    private fun apiErrorMessage(e:HttpResult.Error):String=runCatching{JSONObject(e.body)}.getOrNull()?.optString("message")?.takeIf{it.isNotBlank()}?:"Cloud API error (${e.code})"
    private fun encode(v:String)=URLEncoder.encode(v,"UTF-8")
    private fun naturalKey(vararg parts:String)=parts.joinToString("|"){it.trim().uppercase()}
    private fun precedence(type:String)=ENTITY_ORDER.indexOf(type).let{if(it<0)999 else it}

    private data class DocumentKey(val entityType:String,val entityKey:String)
    private data class LocalDocument(val entityType:String,val entityKey:String,val content:JSONObject){val key get()=DocumentKey(entityType,entityKey)}
    private data class RemoteDocument(val entityType:String,val entityKey:String,val content:JSONObject){val key get()=DocumentKey(entityType,entityKey)}
    private sealed interface HttpResult{data class Ok(val code:Int,val body:String):HttpResult;data class Error(val code:Int,val body:String):HttpResult}

    private companion object{
        const val TYPE_CHARGE_TYPE="CHARGE_TYPE"; const val TYPE_CHARGE="ADDITIONAL_CHARGE"; const val TYPE_CHARGE_PAYMENT="ADDITIONAL_CHARGE_PAYMENT"; const val TYPE_CHARGE_SETTLEMENT="ADDITIONAL_CHARGE_SETTLEMENT"
        const val TYPE_SHIPMENT="SHIPMENT"; const val TYPE_SHIPMENT_ITEM="SHIPMENT_ITEM"; const val TYPE_SHIPMENT_EXPENSE="SHIPMENT_EXPENSE"; const val TYPE_SHIPMENT_ITEM_ALLOCATION="SHIPMENT_ITEM_ALLOCATION"; const val TYPE_SHIPMENT_EXPENSE_ALLOCATION="SHIPMENT_EXPENSE_ALLOCATION"
        val ENTITY_ORDER=listOf(TYPE_CHARGE_TYPE,TYPE_CHARGE,TYPE_CHARGE_PAYMENT,TYPE_CHARGE_SETTLEMENT,TYPE_SHIPMENT,TYPE_SHIPMENT_ITEM,TYPE_SHIPMENT_EXPENSE,TYPE_SHIPMENT_ITEM_ALLOCATION,TYPE_SHIPMENT_EXPENSE_ALLOCATION)
        val WRITER_ROLES=setOf("OWNER","ADMIN","ACCOUNTANT","SALES","INVENTORY","CASHIER")
        val AUTHORITY_ROLES=setOf("OWNER","ADMIN","ACCOUNTANT")
        const val MAX_CONFLICT_DIFFERENCES=24
        val CHARGE_TYPE_METADATA_FIELDS=setOf("created_at_ms")
        const val TOMBSTONE_DELETED_FIELD="_deleted"
        const val TOMBSTONE_SCOPE_FIELD="_delete_scope"
        const val TOMBSTONE_SCOPE_TEST_SUPPORT="TEST_DATA_SUPPORT"
        const val TOMBSTONE_SCOPE_ADMIN_UNUSED_SHIPMENT="ADMIN_UNUSED_SHIPMENT_DELETE"
        const val TOMBSTONE_VERSION_FIELD="_tombstone_version"
        const val TOMBSTONE_VERSION=1
        const val TOMBSTONE_DELETED_AT_FIELD="_deleted_at_ms"
        const val TOMBSTONE_REASON_HASH_FIELD="_reason_sha256"
    }
}

private fun JSONObject.putNullable(name:String,value:Any?):JSONObject=put(name,value?:JSONObject.NULL)
private fun JSONObject.optNullableLong(name:String):Long?=if(!has(name)||isNull(name))null else optLong(name)
private fun JSONObject.optNullableString(name:String):String?=if(!has(name)||isNull(name))null else optString(name).takeIf{it.isNotBlank()}
