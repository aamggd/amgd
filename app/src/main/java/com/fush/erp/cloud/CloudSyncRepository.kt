package com.fush.erp.cloud

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import com.fush.erp.BuildConfig
import com.fush.erp.R
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.UserEntity
import com.fush.erp.domain.DocumentNumberGuard
import com.fush.erp.domain.DocumentNumberReservationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Instant

class CloudSyncRepository(
    private val context: Context,
    private val db: FushDatabase,
) : DocumentNumberGuard {
    private val sessionStore by lazy { CloudSessionStore(context.applicationContext) }
    private val masterDataSync by lazy { MasterDataCloudSyncEngine(context.applicationContext, db) }
    private val salesReceivablesSync by lazy { SalesReceivablesCloudSyncEngine(context.applicationContext, db) }
    private val purchaseDocumentsSync by lazy { PurchaseDocumentsCloudSyncEngine(context.applicationContext, db) }
    private val supplierPaymentsTreasurySync by lazy { SupplierPaymentsTreasuryCloudSyncEngine(context.applicationContext, db) }
    private val inventoryProductionSync by lazy { InventoryProductionCloudSyncEngine(context.applicationContext, db) }
    private val accountingSync by lazy { AccountingCloudSyncEngine(context.applicationContext, db) }
    private val salesAuxiliarySync by lazy { SalesAuxiliaryCloudSyncEngine(context.applicationContext, db) }
    private val masterDataMutex = Mutex()
    private val salesReceivablesMutex = Mutex()
    private val purchaseDocumentsMutex = Mutex()
    private val supplierPaymentsTreasuryMutex = Mutex()
    private val inventoryProductionMutex = Mutex()
    private val accountingSyncMutex = Mutex()
    private val salesAuxiliarySyncMutex = Mutex()
    private val companySyncAllMutex = Mutex()

    fun currentSession(localUser: UserEntity): CloudSession? =
        sessionStore.load(localUser.id, adoptLegacyForAdmin = localUser.role == "ADMIN")

    /** v210: authenticated session for FUSH AI gateway; refreshes expired cloud sessions safely. */
    suspend fun validSessionForAi(localUser: UserEntity): CloudOperationResult<CloudSession> =
        ensureValidSession(localUser)

    fun bindingFor(localUser: UserEntity): CloudUserBinding? = sessionStore.binding(localUser.id)

    fun clearSession(localUser: UserEntity) = sessionStore.clearSession(localUser.id)

    fun clearBinding(localUser: UserEntity) {
        sessionStore.clearSession(localUser.id)
        sessionStore.clearBinding(localUser.id)
    }

    override suspend fun reserve(
        localUserId: Long,
        documentType: String,
        documentNo: String,
    ): DocumentNumberReservationResult = withContext(Dispatchers.IO) {
        val localUser = db.userDao().byId(localUserId)
            ?: return@withContext DocumentNumberReservationResult.Failure("المستخدم المحلي غير موجود")
        // Preserve offline/local-only operation for installations that have never linked this user to cloud.
        if (sessionStore.load(localUser.id, adoptLegacyForAdmin = localUser.role == "ADMIN") == null) {
            return@withContext DocumentNumberReservationResult.LocalOnly
        }
        val session = when (val valid = ensureValidSession(localUser)) {
            is CloudOperationResult.Success -> valid.value
            is CloudOperationResult.Failure -> return@withContext DocumentNumberReservationResult.Failure(valid.message)
        }
        when (val identity = validateAndBindIdentity(localUser, session)) {
            is CloudOperationResult.Success -> Unit
            is CloudOperationResult.Failure -> return@withContext DocumentNumberReservationResult.Failure(identity.message)
        }
        val payload = JSONObject()
            .put("target_organization_id", session.requireOrganizationId())
            .put("target_document_type", documentType.trim().uppercase())
            .put("target_document_no", documentNo.trim())
            .put("target_device_key", deviceKey())
            .toString()
        when (val response = request(
            "POST",
            "/rest/v1/rpc/fush_reserve_document_number",
            payload,
            session.accessToken,
        )) {
            is HttpResult.Error -> DocumentNumberReservationResult.Failure(apiErrorMessage(response))
            is HttpResult.Ok -> {
                val json = runCatching { JSONObject(response.body) }.getOrElse {
                    return@withContext DocumentNumberReservationResult.Failure("استجابة حجز رقم المستند غير صالحة")
                }
                if (json.optBoolean("reserved", false)) {
                    DocumentNumberReservationResult.Reserved(json.optString("reservation_id"))
                } else {
                    DocumentNumberReservationResult.InUse(
                        reservedBy = json.optString("reserved_by").takeIf { it.isNotBlank() },
                        reservedDevice = json.optString("reserved_device_key").takeIf { it.isNotBlank() },
                    )
                }
            }
        }
    }

    fun lastMasterDataSyncAt(localUser: UserEntity): Long = masterDataSync.lastSuccessAt(localUser.id)
    fun lastSalesReceivablesSyncAt(localUser: UserEntity): Long = salesReceivablesSync.lastSuccessAt(localUser.id)
    fun lastSalesReceivablesConflicts(localUser: UserEntity): List<SalesReceivablesConflict> =
        salesReceivablesSync.lastConflicts(localUser.id)
    fun lastPurchaseDocumentsSyncAt(localUser: UserEntity): Long = purchaseDocumentsSync.lastSuccessAt(localUser.id)
    fun lastPurchaseDocumentsConflicts(localUser: UserEntity): List<PurchaseDocumentsConflict> =
        purchaseDocumentsSync.lastConflicts(localUser.id)
    fun lastSupplierPaymentsTreasurySyncAt(localUser: UserEntity): Long = supplierPaymentsTreasurySync.lastSuccessAt(localUser.id)
    fun lastSupplierPaymentsTreasuryConflicts(localUser: UserEntity): List<SupplierPaymentsTreasuryConflict> =
        supplierPaymentsTreasurySync.lastConflicts(localUser.id)
    fun lastInventoryProductionSyncAt(localUser: UserEntity): Long = inventoryProductionSync.lastSuccessAt(localUser.id)
    fun lastInventoryProductionConflicts(localUser: UserEntity): List<InventoryProductionConflict> =
        inventoryProductionSync.lastConflicts(localUser.id)
    fun lastAccountingSyncAt(localUser: UserEntity): Long = accountingSync.lastSuccessAt(localUser.id)
    fun lastAccountingConflicts(localUser: UserEntity): List<AccountingSyncConflict> =
        accountingSync.lastConflicts(localUser.id)
    fun lastSalesAuxiliarySyncAt(localUser: UserEntity): Long = salesAuxiliarySync.lastSuccessAt(localUser.id)
    fun lastSalesAuxiliaryConflicts(localUser: UserEntity): List<SalesAuxiliaryConflict> = salesAuxiliarySync.lastConflicts(localUser.id)


    suspend fun syncSalesAuxiliary(localUser: UserEntity): CloudOperationResult<SalesAuxiliarySyncResult> =
        withContext(Dispatchers.IO) {
            salesAuxiliarySyncMutex.withLock {
                val session = when (val result = ensureValidSession(localUser)) {
                    is CloudOperationResult.Success -> result.value
                    is CloudOperationResult.Failure -> return@withLock result
                }
                val binding = when (val result = validateAndBindIdentity(localUser, session)) {
                    is CloudOperationResult.Success -> result.value
                    is CloudOperationResult.Failure -> return@withLock result
                }
                salesAuxiliarySync.sync(localUser, session, binding.cloudRole)
            }
        }

    /**
     * v200 pre-pass used by the unified sync.  A support-test deletion tombstone for a shipment
     * expense must remove the local sales-auxiliary row before accounting tries to delete the
     * voucher/journal it references through RESTRICT foreign keys.
     */
    private suspend fun syncSalesAuxiliaryDeletionTombstones(localUser: UserEntity): CloudOperationResult<Int> =
        withContext(Dispatchers.IO) {
            salesAuxiliarySyncMutex.withLock {
                val session = when (val result = ensureValidSession(localUser)) {
                    is CloudOperationResult.Success -> result.value
                    is CloudOperationResult.Failure -> return@withLock result
                }
                val binding = when (val result = validateAndBindIdentity(localUser, session)) {
                    is CloudOperationResult.Success -> result.value
                    is CloudOperationResult.Failure -> return@withLock result
                }
                runCatching { salesAuxiliarySync.applyDeletionTombstones(localUser, session, binding.cloudRole) }
                    .fold(
                        onSuccess = { CloudOperationResult.Success(it) },
                        onFailure = { CloudOperationResult.Failure(it.message?.takeIf(String::isNotBlank) ?: "تعذر تطبيق حذف بيانات الاختبار السحابي") },
                    )
            }
        }

    /**
     * Publish durable support-test tombstones using the existing v177/v179 cloud document tables.
     * No Supabase schema change is required: the canonical cloud document remains at the same
     * natural key with an explicit _deleted marker.  Other devices consume the tombstone before
     * publishing their local copy, so the test data cannot be resurrected by bidirectional sync.
     * Returns false only for a genuinely local-only installation with no cloud session.
     */
    suspend fun publishTestShipmentExpenseDeletionTombstones(
        localUserId: Long,
        expenseId: Long,
        reason: String,
    ): CloudOperationResult<Boolean> = withContext(Dispatchers.IO) {
        val localUser = db.userDao().byId(localUserId)
            ?: return@withContext CloudOperationResult.Failure("مستخدم الصيانة غير موجود")
        if (currentSession(localUser) == null) {
            return@withContext CloudOperationResult.Success(false)
        }
        val session = when (val result = ensureValidSession(localUser)) {
            is CloudOperationResult.Success -> result.value
            is CloudOperationResult.Failure -> return@withContext result
        }
        val binding = when (val result = validateAndBindIdentity(localUser, session)) {
            is CloudOperationResult.Success -> result.value
            is CloudOperationResult.Failure -> return@withContext result
        }
        if (binding.cloudRole.trim().uppercase() !in setOf("OWNER", "ADMIN", "ACCOUNTANT")) {
            return@withContext CloudOperationResult.Failure("تثبيت حذف بيانات الاختبار في السحابة يتطلب OWNER أو ADMIN أو ACCOUNTANT")
        }

        try {
            // Sales auxiliary first is intentional. If the second phase is interrupted, another
            // device may remove the expense while the accounting rows remain, which is safe. The
            // inverse order could leave a shipment-expense FK pointing at a deleted voucher.
            salesAuxiliarySync.publishSupportDeletionForShipmentExpense(
                localUser = localUser,
                session = session,
                cloudRole = binding.cloudRole,
                expenseId = expenseId,
                reason = reason,
            )
            accountingSync.publishSupportDeletionForShipmentExpenseAccounting(
                localUser = localUser,
                session = session,
                cloudRole = binding.cloudRole,
                expenseId = expenseId,
                reason = reason,
            )
            CloudOperationResult.Success(true)
        } catch (error: Throwable) {
            CloudOperationResult.Failure(error.message?.takeIf { it.isNotBlank() } ?: "تعذر تثبيت حذف بيانات الاختبار في السحابة")
        }
    }

    /** v201 durable deletion for an ADMIN-owned unused shipment. */
    suspend fun publishUnusedShipmentDeletionTombstones(
        localUserId: Long,
        shipmentId: Long,
        reason: String,
    ): CloudOperationResult<Boolean> = withContext(Dispatchers.IO) {
        val localUser = db.userDao().byId(localUserId)
            ?: return@withContext CloudOperationResult.Failure("المستخدم غير موجود")
        require(localUser.role == "ADMIN") { "حذف الشحنة يتطلب ADMIN" }
        if (currentSession(localUser) == null) {
            return@withContext CloudOperationResult.Success(false)
        }
        val session = when (val result = ensureValidSession(localUser)) {
            is CloudOperationResult.Success -> result.value
            is CloudOperationResult.Failure -> return@withContext result
        }
        val binding = when (val result = validateAndBindIdentity(localUser, session)) {
            is CloudOperationResult.Success -> result.value
            is CloudOperationResult.Failure -> return@withContext result
        }
        if (binding.cloudRole.trim().uppercase() !in setOf("OWNER", "ADMIN")) {
            return@withContext CloudOperationResult.Failure("حذف الشحنة السحابي يتطلب OWNER أو ADMIN")
        }
        try {
            salesAuxiliarySync.publishAdminDeletionForUnusedShipment(
                localUser = localUser,
                session = session,
                cloudRole = binding.cloudRole,
                shipmentId = shipmentId,
                reason = reason,
            )
            CloudOperationResult.Success(true)
        } catch (error: Throwable) {
            CloudOperationResult.Failure(error.message?.takeIf { it.isNotBlank() } ?: "تعذر تثبيت حذف الشحنة في السحابة")
        }
    }

    suspend fun resolveSalesAuxiliaryConflict(
        localUser: UserEntity,
        conflict: SalesAuxiliaryConflict,
        resolution: SalesAuxiliaryConflictResolution,
    ): CloudOperationResult<SalesAuxiliarySyncResult> = withContext(Dispatchers.IO) {
        salesAuxiliarySyncMutex.withLock {
            val session = when (val result = ensureValidSession(localUser)) {
                is CloudOperationResult.Success -> result.value
                is CloudOperationResult.Failure -> return@withLock result
            }
            val binding = when (val result = validateAndBindIdentity(localUser, session)) {
                is CloudOperationResult.Success -> result.value
                is CloudOperationResult.Failure -> return@withLock result
            }
            salesAuxiliarySync.resolveConflict(localUser, session, binding.cloudRole, conflict, resolution)
        }
    }

    suspend fun syncInventoryProduction(localUser: UserEntity): CloudOperationResult<InventoryProductionSyncResult> =
        withContext(Dispatchers.IO) {
            inventoryProductionMutex.withLock {
                val session = when (val result = ensureValidSession(localUser)) {
                    is CloudOperationResult.Success -> result.value
                    is CloudOperationResult.Failure -> return@withLock result
                }
                val binding = when (val result = validateAndBindIdentity(localUser, session)) {
                    is CloudOperationResult.Success -> result.value
                    is CloudOperationResult.Failure -> return@withLock result
                }
                inventoryProductionSync.sync(localUser, session, binding.cloudRole)
            }
        }

    suspend fun syncAccounting(localUser: UserEntity): CloudOperationResult<AccountingCloudSyncResult> =
        withContext(Dispatchers.IO) {
            accountingSyncMutex.withLock {
                val session = when (val result = ensureValidSession(localUser)) {
                    is CloudOperationResult.Success -> result.value
                    is CloudOperationResult.Failure -> return@withLock result
                }
                val binding = when (val result = validateAndBindIdentity(localUser, session)) {
                    is CloudOperationResult.Success -> result.value
                    is CloudOperationResult.Failure -> return@withLock result
                }
                accountingSync.sync(localUser, session, binding.cloudRole)
            }
        }

    suspend fun syncAccountingReferencePrerequisites(localUser: UserEntity): CloudOperationResult<AccountingReferenceSyncResult> =
        withContext(Dispatchers.IO) {
            accountingSyncMutex.withLock {
                val session = when (val result = ensureValidSession(localUser)) {
                    is CloudOperationResult.Success -> result.value
                    is CloudOperationResult.Failure -> return@withLock result
                }
                val binding = when (val result = validateAndBindIdentity(localUser, session)) {
                    is CloudOperationResult.Success -> result.value
                    is CloudOperationResult.Failure -> return@withLock result
                }
                accountingSync.syncReferencePrerequisites(localUser, session, binding.cloudRole)
            }
        }

    suspend fun resolveAccountingConflict(
        localUser: UserEntity,
        conflict: AccountingSyncConflict,
        resolution: AccountingConflictResolution,
    ): CloudOperationResult<AccountingCloudSyncResult> = withContext(Dispatchers.IO) {
        accountingSyncMutex.withLock {
            val session = when (val result = ensureValidSession(localUser)) {
                is CloudOperationResult.Success -> result.value
                is CloudOperationResult.Failure -> return@withLock result
            }
            val binding = when (val result = validateAndBindIdentity(localUser, session)) {
                is CloudOperationResult.Success -> result.value
                is CloudOperationResult.Failure -> return@withLock result
            }
            accountingSync.resolveConflict(localUser, session, binding.cloudRole, conflict, resolution)
        }
    }


    suspend fun syncSupplierPaymentsTreasury(localUser: UserEntity): CloudOperationResult<SupplierPaymentsTreasurySyncResult> =
        withContext(Dispatchers.IO) {
            supplierPaymentsTreasuryMutex.withLock {
                val session = when (val result = ensureValidSession(localUser)) {
                    is CloudOperationResult.Success -> result.value
                    is CloudOperationResult.Failure -> return@withLock result
                }
                val binding = when (val result = validateAndBindIdentity(localUser, session)) {
                    is CloudOperationResult.Success -> result.value
                    is CloudOperationResult.Failure -> return@withLock result
                }
                supplierPaymentsTreasurySync.sync(localUser, session, binding.cloudRole)
            }
        }

    suspend fun syncPurchaseDocuments(localUser: UserEntity): CloudOperationResult<PurchaseDocumentsSyncResult> =
        withContext(Dispatchers.IO) {
            purchaseDocumentsMutex.withLock {
                val session = when (val result = ensureValidSession(localUser)) {
                    is CloudOperationResult.Success -> result.value
                    is CloudOperationResult.Failure -> return@withLock result
                }
                val binding = when (val result = validateAndBindIdentity(localUser, session)) {
                    is CloudOperationResult.Success -> result.value
                    is CloudOperationResult.Failure -> return@withLock result
                }
                purchaseDocumentsSync.sync(localUser, session, binding.cloudRole)
            }
        }

    suspend fun syncSalesReceivables(localUser: UserEntity): CloudOperationResult<SalesReceivablesSyncResult> =
        withContext(Dispatchers.IO) {
            salesReceivablesMutex.withLock {
                val session = when (val result = ensureValidSession(localUser)) {
                    is CloudOperationResult.Success -> result.value
                    is CloudOperationResult.Failure -> return@withLock result
                }
                val binding = when (val result = validateAndBindIdentity(localUser, session)) {
                    is CloudOperationResult.Success -> result.value
                    is CloudOperationResult.Failure -> return@withLock result
                }
                salesReceivablesSync.sync(localUser, session, binding.cloudRole)
            }
        }

    suspend fun syncMasterData(localUser: UserEntity): CloudOperationResult<MasterDataSyncResult> =
        withContext(Dispatchers.IO) {
            masterDataMutex.withLock {
                val session = when (val result = ensureValidSession(localUser)) {
                    is CloudOperationResult.Success -> result.value
                    is CloudOperationResult.Failure -> return@withLock result
                }
                val binding = when (val result = validateAndBindIdentity(localUser, session)) {
                    is CloudOperationResult.Success -> result.value
                    is CloudOperationResult.Failure -> return@withLock result
                }
                masterDataSync.sync(localUser, session, binding.cloudRole)
            }
        }

    /**
     * v185 single company-truth synchronization. Business roles are deliberately not consulted here:
     * validateAndBindIdentity already proves an active organization binding; operation permissions
     * remain enforced by the domain services when a user creates/posts business transactions.
     */
    suspend fun syncAllCompanyData(localUser: UserEntity): CloudOperationResult<CompanySyncAllResult> =
        companySyncAllMutex.withLock {
            val master = when (val x = syncMasterData(localUser)) {
                is CloudOperationResult.Success -> x.value
                is CloudOperationResult.Failure -> return@withLock CloudOperationResult.Failure("البيانات الأساسية: ${x.message}", x.httpCode)
            }
            // v198: hydrate accounting prerequisites before any transaction domain. This ensures
            // custom ledger accounts, treasuries, employees and sales reps exist before journals,
            // supplier payments or party vouchers try to resolve their foreign keys.
            val accountingReferences = when (val x = syncAccountingReferencePrerequisites(localUser)) {
                is CloudOperationResult.Success -> x.value
                is CloudOperationResult.Failure -> return@withLock CloudOperationResult.Failure("مرجعيات المحاسبة: ${x.message}", x.httpCode)
            }
            val sales = when (val x = syncSalesReceivables(localUser)) {
                is CloudOperationResult.Success -> x.value
                is CloudOperationResult.Failure -> return@withLock CloudOperationResult.Failure("المبيعات والتحصيلات: ${x.message}", x.httpCode)
            }
            val purchases = when (val x = syncPurchaseDocuments(localUser)) {
                is CloudOperationResult.Success -> x.value
                is CloudOperationResult.Failure -> return@withLock CloudOperationResult.Failure("المشتريات: ${x.message}", x.httpCode)
            }
            val supplierPayments = when (val x = syncSupplierPaymentsTreasury(localUser)) {
                is CloudOperationResult.Success -> x.value
                is CloudOperationResult.Failure -> return@withLock CloudOperationResult.Failure("دفعات الموردين والخزينة: ${x.message}", x.httpCode)
            }
            val inventoryProduction = when (val x = syncInventoryProduction(localUser)) {
                is CloudOperationResult.Success -> x.value
                is CloudOperationResult.Failure -> return@withLock CloudOperationResult.Failure("المخزون والإنتاج: ${x.message}", x.httpCode)
            }
            // v200: consume sales-auxiliary deletion tombstones before accounting. Shipment
            // expenses reference party_vouchers with RESTRICT, so this pre-pass removes the child
            // row first and lets the accounting tombstone safely remove voucher + journal in the
            // same unified sync run.
            val salesAuxDeletionCount = when (val x = syncSalesAuxiliaryDeletionTombstones(localUser)) {
                is CloudOperationResult.Success -> x.value
                is CloudOperationResult.Failure -> return@withLock CloudOperationResult.Failure("حذف بيانات الاختبار السحابي: ${x.message}", x.httpCode)
            }
            val accounting = when (val x = syncAccounting(localUser)) {
                is CloudOperationResult.Success -> x.value
                is CloudOperationResult.Failure -> return@withLock CloudOperationResult.Failure("الأستاذ والخزينة: ${x.message}", x.httpCode)
            }
            // Sales auxiliary is last because shipment expenses hydrate against accounting vouchers.
            val salesAux = when (val x = syncSalesAuxiliary(localUser)) {
                is CloudOperationResult.Success -> x.value
                is CloudOperationResult.Failure -> return@withLock CloudOperationResult.Failure("الرسوم والشحنات: ${x.message}", x.httpCode)
            }
            val conflicts = master.conflicts + sales.conflicts + purchases.conflicts + supplierPayments.conflicts +
                inventoryProduction.productionConflicts + accounting.conflicts + salesAux.conflicts
            val uploaded = master.uploaded + (if (accountingReferences.publishedSnapshot) 1 else 0) + sales.uploadedDocuments + purchases.uploadedDocuments + supplierPayments.uploadedPayments +
                inventoryProduction.uploadedProductionOrders + inventoryProduction.inventoryLotsPublished + accounting.uploadedJournals +
                accounting.uploadedTreasuryVouchers + salesAux.uploadedDocuments
            val downloaded = master.downloaded + accountingReferences.downloadedRows + sales.downloadedDocuments + purchases.downloadedDocuments + supplierPayments.downloadedPayments +
                inventoryProduction.downloadedProductionOrders + inventoryProduction.inventoryLotsAdjusted + accounting.downloadedJournals +
                accounting.downloadedTreasuryVouchers + salesAux.downloadedDocuments + salesAuxDeletionCount
            CloudOperationResult.Success(
                CompanySyncAllResult(uploaded, downloaded, conflicts, System.currentTimeMillis())
            )
        }

    suspend fun signIn(
        localUser: UserEntity,
        email: String,
        password: String,
    ): CloudOperationResult<CloudSession> = withContext(Dispatchers.IO) {
        val normalizedEmail = email.trim()
        if (normalizedEmail.isBlank() || password.isBlank()) {
            return@withContext CloudOperationResult.Failure(text(R.string.cloud_error_credentials_required))
        }

        val body = JSONObject()
            .put("email", normalizedEmail)
            .put("password", password)
            .toString()

        val session = when (val response = request(
            method = "POST",
            path = "/auth/v1/token?grant_type=password",
            body = body,
            bearerToken = null,
        )) {
            is HttpResult.Error -> return@withContext CloudOperationResult.Failure(authErrorMessage(response), response.code)
            is HttpResult.Ok -> when (val parsed = parseSession(response.body, fallbackEmail = normalizedEmail)) {
                is CloudOperationResult.Success -> parsed.value
                is CloudOperationResult.Failure -> return@withContext parsed
            }
        }

        val scopedSession = when (val tenant = resolveOrganization(session)) {
            is CloudOperationResult.Success -> tenant.value
            is CloudOperationResult.Failure -> return@withContext tenant
        }

        when (val identity = validateAndBindIdentity(localUser, scopedSession)) {
            is CloudOperationResult.Success -> {
                if (!bindDatabaseOrganization(scopedSession.requireOrganizationId())) {
                    return@withContext CloudOperationResult.Failure(
                        "قاعدة البيانات المحلية مرتبطة بشركة أخرى. لا يمكن مزامنة بيانات شركتين داخل نفس قاعدة البيانات."
                    )
                }
                sessionStore.save(localUser.id, scopedSession)
                CloudOperationResult.Success(scopedSession)
            }
            is CloudOperationResult.Failure -> {
                sessionStore.clearSession(localUser.id)
                identity
            }
        }
    }

    suspend fun joinExistingCompany(
        email: String,
        password: String,
    ): CloudOperationResult<CloudJoinIdentity> = withContext(Dispatchers.IO) {
        val normalizedEmail = email.trim()
        if (normalizedEmail.isBlank() || password.isBlank()) {
            return@withContext CloudOperationResult.Failure(text(R.string.cloud_error_credentials_required))
        }
        val body = JSONObject()
            .put("email", normalizedEmail)
            .put("password", password)
            .toString()
        val session = when (val response = request(
            method = "POST",
            path = "/auth/v1/token?grant_type=password",
            body = body,
            bearerToken = null,
        )) {
            is HttpResult.Error -> return@withContext CloudOperationResult.Failure(authErrorMessage(response), response.code)
            is HttpResult.Ok -> when (val parsed = parseSession(response.body, fallbackEmail = normalizedEmail)) {
                is CloudOperationResult.Success -> parsed.value
                is CloudOperationResult.Failure -> return@withContext parsed
            }
        }

        val scopedSession = when (val tenant = resolveOrganization(session)) {
            is CloudOperationResult.Success -> tenant.value
            is CloudOperationResult.Failure -> return@withContext tenant
        }
        val rawBinding = when (val result = loadCloudBinding(scopedSession)) {
            is CloudOperationResult.Success -> result.value
            is CloudOperationResult.Failure -> return@withContext result
        }
        if (rawBinding.localUsername.isBlank() || rawBinding.cloudRole.isBlank()) {
            return@withContext CloudOperationResult.Failure(text(R.string.cloud_error_identity_response))
        }
        CloudOperationResult.Success(CloudJoinIdentity(scopedSession, rawBinding))
    }

    fun completeJoinedIdentity(localUser: UserEntity, joined: CloudJoinIdentity) {
        require(joined.binding.localUsername.equals(localUser.username, ignoreCase = true)) { "Cloud/local username mismatch" }
        require(bindDatabaseOrganization(joined.session.requireOrganizationId())) {
            "Local database is already bound to another cloud organization"
        }
        sessionStore.save(localUser.id, joined.session)
        sessionStore.saveBinding(joined.binding.copy(localUserId = localUser.id))
    }

    suspend fun testAndRegisterDevice(localUser: UserEntity): CloudOperationResult<CloudConnectionResult> = withContext(Dispatchers.IO) {
        val session = when (val sessionResult = ensureValidSession(localUser)) {
            is CloudOperationResult.Success -> sessionResult.value
            is CloudOperationResult.Failure -> return@withContext sessionResult
        }

        val binding = when (val identity = validateAndBindIdentity(localUser, session)) {
            is CloudOperationResult.Success -> identity.value
            is CloudOperationResult.Failure -> return@withContext identity
        }

        val orgPath = "/rest/v1/fush_organizations" +
            "?select=id,code,name&id=eq.${session.requireOrganizationId()}"
        val organization = when (val response = request("GET", orgPath, null, session.accessToken)) {
            is HttpResult.Error -> return@withContext CloudOperationResult.Failure(apiErrorMessage(response), response.code)
            is HttpResult.Ok -> {
                val rows = runCatching { JSONArray(response.body) }.getOrNull()
                    ?: return@withContext CloudOperationResult.Failure(text(R.string.cloud_error_org_response))
                if (rows.length() == 0) {
                    return@withContext CloudOperationResult.Failure(text(R.string.cloud_error_not_member))
                }
                rows.getJSONObject(0)
            }
        }

        val now = Instant.now().toString()
        val deviceName = buildDeviceName()
        val payload = JSONObject()
            .put("organization_id", session.requireOrganizationId())
            .put("user_id", session.userId)
            .put("device_key", deviceKey())
            .put("device_name", deviceName)
            .put("app_version", BuildConfig.VERSION_NAME)
            .put("last_seen_at", now)
            .put("updated_at", now)
            .toString()

        val devicePath = "/rest/v1/fush_sync_devices?on_conflict=organization_id,user_id,device_key"
        val device = when (val response = request(
            method = "POST",
            path = devicePath,
            body = payload,
            bearerToken = session.accessToken,
            prefer = "resolution=merge-duplicates,return=representation",
        )) {
            is HttpResult.Error -> return@withContext CloudOperationResult.Failure(apiErrorMessage(response), response.code)
            is HttpResult.Ok -> {
                val rows = runCatching { JSONArray(response.body) }.getOrNull()
                    ?: return@withContext CloudOperationResult.Failure(text(R.string.cloud_error_device_response))
                if (rows.length() == 0) {
                    return@withContext CloudOperationResult.Failure(text(R.string.cloud_error_device_missing))
                }
                rows.getJSONObject(0)
            }
        }

        CloudOperationResult.Success(
            CloudConnectionResult(
                userId = session.userId,
                email = session.email,
                organizationId = organization.optString("id", session.requireOrganizationId()),
                organizationName = organization.optString("name", "FUSH ERP"),
                deviceId = device.optString("id"),
                deviceName = device.optString("device_name", deviceName),
                cloudRole = binding.cloudRole,
                localUsername = binding.localUsername,
            )
        )
    }

    /**
     * Creates a Supabase Auth account using the public sign-up endpoint, then asks the
     * owner-only PostgreSQL RPC to attach it to the same FUSH organization and to the
     * selected local ERP username/role. No service-role or secret key is stored in APK.
     */
    suspend fun provisionCloudUser(
        actor: UserEntity,
        target: UserEntity,
        email: String,
        temporaryPassword: String,
    ): CloudOperationResult<CloudProvisionResult> = withContext(Dispatchers.IO) {
        if (actor.role != "ADMIN") {
            return@withContext CloudOperationResult.Failure(text(R.string.cloud_error_owner_required))
        }
        val normalizedEmail = email.trim().lowercase()
        if (normalizedEmail.isBlank() || temporaryPassword.isBlank()) {
            return@withContext CloudOperationResult.Failure(text(R.string.cloud_error_credentials_required))
        }

        val actorSession = when (val result = ensureValidSession(actor)) {
            is CloudOperationResult.Success -> result.value
            is CloudOperationResult.Failure -> return@withContext result
        }
        val actorBinding = when (val result = validateAndBindIdentity(actor, actorSession)) {
            is CloudOperationResult.Success -> result.value
            is CloudOperationResult.Failure -> return@withContext result
        }
        if (actorBinding.cloudRole.uppercase() !in setOf("OWNER", "ADMIN")) {
            return@withContext CloudOperationResult.Failure(text(R.string.cloud_error_owner_required))
        }

        var requiresConfirmation = false
        val signupBody = JSONObject()
            .put("email", normalizedEmail)
            .put("password", temporaryPassword)
            .toString()
        when (val signup = request("POST", "/auth/v1/signup", signupBody, null)) {
            is HttpResult.Ok -> {
                val json = runCatching { JSONObject(signup.body) }.getOrNull()
                val accessToken = json?.optString("access_token").orEmpty()
                requiresConfirmation = accessToken.isBlank()
            }
            is HttpResult.Error -> {
                val alreadyExists = signup.code == 422 && signup.body.lowercase().let {
                    "already" in it || "registered" in it || "exists" in it
                }
                if (!alreadyExists) {
                    return@withContext CloudOperationResult.Failure(authErrorMessage(signup), signup.code)
                }
            }
        }

        val rpcPayload = JSONObject()
            .put("target_organization_id", actorSession.requireOrganizationId())
            .put("target_email", normalizedEmail)
            .put("target_local_username", target.username)
            .put("target_display_name", target.displayName)
            .put("target_role", target.role)
            .toString()

        val rpc = when (val response = request(
            "POST",
            "/rest/v1/rpc/fush_provision_member",
            rpcPayload,
            actorSession.accessToken,
        )) {
            is HttpResult.Error -> return@withContext CloudOperationResult.Failure(apiErrorMessage(response), response.code)
            is HttpResult.Ok -> runCatching { JSONObject(response.body) }.getOrElse {
                return@withContext CloudOperationResult.Failure(text(R.string.cloud_error_provision_response))
            }
        }

        val cloudUserId = rpc.optString("cloud_user_id")
        val cloudRole = rpc.optString("role", target.role).uppercase()
        if (cloudUserId.isBlank()) {
            return@withContext CloudOperationResult.Failure(text(R.string.cloud_error_provision_response))
        }
        val binding = CloudUserBinding(
            localUserId = target.id,
            localUsername = target.username,
            cloudUserId = cloudUserId,
            email = normalizedEmail,
            cloudRole = cloudRole,
            displayName = target.displayName,
        )
        sessionStore.saveBinding(binding)
        CloudOperationResult.Success(
            CloudProvisionResult(
                cloudUserId = cloudUserId,
                email = normalizedEmail,
                localUsername = target.username,
                cloudRole = cloudRole,
                requiresEmailConfirmation = requiresConfirmation,
            )
        )
    }

    private suspend fun ensureValidSession(localUser: UserEntity): CloudOperationResult<CloudSession> {
        val storedSession = sessionStore.load(localUser.id, adoptLegacyForAdmin = localUser.role == "ADMIN")
            ?: return CloudOperationResult.Failure(text(R.string.cloud_error_sign_in_first))
        val session = when (val tenant = resolveOrganization(storedSession)) {
            is CloudOperationResult.Success -> tenant.value
            is CloudOperationResult.Failure -> return tenant
        }
        if (session != storedSession) sessionStore.save(localUser.id, session)
        val nowSeconds = System.currentTimeMillis() / 1000L
        if (session.expiresAtEpochSeconds > nowSeconds + 60L) {
            return CloudOperationResult.Success(session)
        }

        val body = JSONObject().put("refresh_token", session.refreshToken).toString()
        return when (val response = request(
            method = "POST",
            path = "/auth/v1/token?grant_type=refresh_token",
            body = body,
            bearerToken = null,
        )) {
            is HttpResult.Error -> {
                sessionStore.clearSession(localUser.id)
                CloudOperationResult.Failure(text(R.string.cloud_error_session_expired), response.code)
            }
            is HttpResult.Ok -> when (val parsed = parseSession(response.body, fallbackEmail = session.email)) {
                is CloudOperationResult.Success -> {
                    val refreshed = parsed.value.copy(organizationId = session.organizationId)
                    sessionStore.save(localUser.id, refreshed)
                    CloudOperationResult.Success(refreshed)
                }
                is CloudOperationResult.Failure -> parsed
            }
        }
    }

    /**
     * The tenant marker lives inside Room so it travels with encrypted portable backups.
     * This closes the restore/login gap where Company A data could otherwise be restored on a
     * device and then uploaded while signed into Company B.
     */
    private fun databaseOrganizationId(): String? = runCatching {
        db.openHelper.readableDatabase.query(
            "SELECT organization_id FROM cloud_tenant_binding WHERE id = 1 LIMIT 1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.trim()?.takeIf { it.isNotEmpty() } else null
        }
    }.getOrNull()

    private fun bindDatabaseOrganization(organizationId: String): Boolean {
        val normalized = organizationId.trim()
        if (normalized.isEmpty()) return false
        val existing = databaseOrganizationId()
        if (existing != null) return existing == normalized
        val now = System.currentTimeMillis()
        db.openHelper.writableDatabase.execSQL(
            "INSERT OR IGNORE INTO cloud_tenant_binding(id, organization_id, bound_at, updated_at) VALUES (1, ?, ?, ?)",
            arrayOf(normalized, now, now),
        )
        return databaseOrganizationId() == normalized
    }

    /**
     * v213 resolves the tenant from the authenticated user's ACTIVE memberships.
     * Existing v212 sessions migrate lazily: the fixed organization is no longer read from BuildConfig.
     * A session may keep its previously selected organization only while it remains an active membership.
     */
    private fun resolveOrganization(session: CloudSession): CloudOperationResult<CloudSession> {
        val userId = encode(session.userId)
        val path = "/rest/v1/fush_organization_members" +
            "?select=organization_id,role,is_active&user_id=eq.$userId&is_active=eq.true"
        return when (val response = request("GET", path, null, session.accessToken)) {
            is HttpResult.Error -> CloudOperationResult.Failure(apiErrorMessage(response), response.code)
            is HttpResult.Ok -> {
                val rows = runCatching { JSONArray(response.body) }.getOrNull()
                    ?: return CloudOperationResult.Failure(text(R.string.cloud_error_identity_response))
                val organizationIds = buildList {
                    for (i in 0 until rows.length()) {
                        val id = rows.optJSONObject(i)?.optString("organization_id")?.trim().orEmpty()
                        if (id.isNotBlank() && id !in this) add(id)
                    }
                }
                if (organizationIds.isEmpty()) {
                    return CloudOperationResult.Failure(text(R.string.cloud_error_not_member))
                }
                val preferred = session.organizationId?.trim()?.takeIf { it in organizationIds }
                    ?: databaseOrganizationId()?.takeIf { it in organizationIds }
                val selected = preferred ?: when (organizationIds.size) {
                    1 -> organizationIds.first()
                    else -> return CloudOperationResult.Failure(
                        "هذا الحساب عضو في أكثر من شركة. يلزم اختيار الشركة النشطة قبل المزامنة."
                    )
                }
                CloudOperationResult.Success(session.copy(organizationId = selected))
            }
        }
    }

    private suspend fun validateAndBindIdentity(
        localUser: UserEntity,
        session: CloudSession,
    ): CloudOperationResult<CloudUserBinding> {
        var binding = loadCloudBinding(session)
        if (binding is CloudOperationResult.Failure && localUser.role == "ADMIN") {
            val membership = loadOwnMembership(session)
            if (membership is CloudOperationResult.Success && membership.value.uppercase() in setOf("OWNER", "ADMIN")) {
                val claimed = claimOwnerIdentity(localUser, session)
                if (claimed is CloudOperationResult.Failure) return claimed
                binding = loadCloudBinding(session)
            }
        }

        val row = when (binding) {
            is CloudOperationResult.Success -> binding.value
            is CloudOperationResult.Failure -> return binding
        }
        if (!row.localUsername.equals(localUser.username, ignoreCase = true)) {
            return CloudOperationResult.Failure(
                text(R.string.cloud_error_identity_mismatch, localUser.username, row.localUsername)
            )
        }
        // v185: every sync attempt must prove ACTIVE organization membership. The business role is
        // retained for conflict-resolution authority/display only; it never gates transport itself.
        val membershipRole = when (val membership = loadOwnMembership(session)) {
            is CloudOperationResult.Success -> membership.value.uppercase()
            is CloudOperationResult.Failure -> return membership
        }
        if (!bindDatabaseOrganization(session.requireOrganizationId())) {
            return CloudOperationResult.Failure(
                "قاعدة البيانات المحلية مرتبطة بشركة أخرى. تم منع المزامنة لحماية عزل بيانات الشركات."
            )
        }
        val scoped = row.copy(localUserId = localUser.id, cloudRole = membershipRole)
        sessionStore.saveBinding(scoped)
        return CloudOperationResult.Success(scoped)
    }

    private fun loadCloudBinding(session: CloudSession): CloudOperationResult<CloudUserBinding> {
        val userId = encode(session.userId)
        val orgId = encode(session.requireOrganizationId())
        val path = "/rest/v1/fush_cloud_user_bindings" +
            "?select=cloud_user_id,local_username,display_name,role,is_active,email" +
            "&organization_id=eq.$orgId&cloud_user_id=eq.$userId"
        return when (val response = request("GET", path, null, session.accessToken)) {
            is HttpResult.Error -> CloudOperationResult.Failure(apiErrorMessage(response), response.code)
            is HttpResult.Ok -> {
                val rows = runCatching { JSONArray(response.body) }.getOrNull()
                    ?: return CloudOperationResult.Failure(text(R.string.cloud_error_identity_response))
                if (rows.length() == 0) {
                    return CloudOperationResult.Failure(text(R.string.cloud_error_identity_not_provisioned))
                }
                val row = rows.getJSONObject(0)
                if (!row.optBoolean("is_active", true)) {
                    return CloudOperationResult.Failure(text(R.string.cloud_error_identity_inactive))
                }
                CloudOperationResult.Success(
                    CloudUserBinding(
                        localUserId = 0,
                        localUsername = row.optString("local_username"),
                        cloudUserId = row.optString("cloud_user_id", session.userId),
                        email = row.optString("email").takeIf { it.isNotBlank() } ?: session.email,
                        cloudRole = row.optString("role"),
                        displayName = row.optString("display_name").takeIf { it.isNotBlank() },
                    )
                )
            }
        }
    }

    private fun loadOwnMembership(session: CloudSession): CloudOperationResult<String> {
        val path = "/rest/v1/fush_organization_members" +
            "?select=role,is_active&organization_id=eq.${encode(session.requireOrganizationId())}" +
            "&user_id=eq.${encode(session.userId)}"
        return when (val response = request("GET", path, null, session.accessToken)) {
            is HttpResult.Error -> CloudOperationResult.Failure(apiErrorMessage(response), response.code)
            is HttpResult.Ok -> {
                val rows = runCatching { JSONArray(response.body) }.getOrNull()
                    ?: return CloudOperationResult.Failure(text(R.string.cloud_error_identity_response))
                if (rows.length() == 0) return CloudOperationResult.Failure(text(R.string.cloud_error_not_member))
                val row = rows.getJSONObject(0)
                if (!row.optBoolean("is_active", true)) return CloudOperationResult.Failure(text(R.string.cloud_error_identity_inactive))
                CloudOperationResult.Success(row.optString("role"))
            }
        }
    }

    private fun claimOwnerIdentity(localUser: UserEntity, session: CloudSession): CloudOperationResult<CloudUserBinding> {
        val body = JSONObject()
            .put("target_organization_id", session.requireOrganizationId())
            .put("target_local_username", localUser.username)
            .put("target_display_name", localUser.displayName)
            .toString()
        return when (val response = request(
            "POST",
            "/rest/v1/rpc/fush_claim_owner_identity",
            body,
            session.accessToken,
        )) {
            is HttpResult.Error -> CloudOperationResult.Failure(apiErrorMessage(response), response.code)
            is HttpResult.Ok -> {
                val json = runCatching { JSONObject(response.body) }.getOrNull()
                    ?: return CloudOperationResult.Failure(text(R.string.cloud_error_identity_response))
                CloudOperationResult.Success(
                    CloudUserBinding(
                        localUserId = localUser.id,
                        localUsername = json.optString("local_username", localUser.username),
                        cloudUserId = json.optString("cloud_user_id", session.userId),
                        email = json.optString("email").takeIf { it.isNotBlank() } ?: session.email,
                        cloudRole = json.optString("role", "OWNER"),
                        displayName = json.optString("display_name", localUser.displayName),
                    )
                )
            }
        }
    }

    private fun parseSession(raw: String, fallbackEmail: String?): CloudOperationResult<CloudSession> = runCatching {
        val json = JSONObject(raw)
        val accessToken = json.getString("access_token")
        val refreshToken = json.getString("refresh_token")
        val expiresIn = json.optLong("expires_in", 3600L).coerceAtLeast(60L)
        val user = json.optJSONObject("user") ?: JSONObject()
        val userId = user.optString("id").ifBlank { extractJwtSubject(accessToken).orEmpty() }
        require(userId.isNotBlank()) { "Missing Supabase user id" }
        val responseEmail = user.optString("email").trim()
        val email = responseEmail.takeIf { it.isNotBlank() } ?: fallbackEmail
        CloudOperationResult.Success(
            CloudSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                userId = userId,
                email = email,
                expiresAtEpochSeconds = (System.currentTimeMillis() / 1000L) + expiresIn,
            )
        )
    }.getOrElse {
        CloudOperationResult.Failure(text(R.string.cloud_error_session_parse, it.message ?: "Invalid response"))
    }

    private fun request(
        method: String,
        path: String,
        body: String?,
        bearerToken: String?,
        prefer: String? = null,
    ): HttpResult {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        val connection = (URL(base + path).openConnection() as HttpURLConnection)
        return try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            connection.setRequestProperty("Accept", "application/json")
            bearerToken?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            prefer?.let { connection.setRequestProperty("Prefer", it) }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code in 200..299) HttpResult.Ok(code, responseBody) else HttpResult.Error(code, responseBody)
        } catch (t: Throwable) {
            HttpResult.Error(null, t.message ?: t.javaClass.simpleName)
        } finally {
            connection.disconnect()
        }
    }

    private fun authErrorMessage(error: HttpResult.Error): String {
        if (error.code == null) return text(R.string.cloud_error_network_auth)
        val json = runCatching { JSONObject(error.body) }.getOrNull()
        val detail = json?.optString("msg")?.takeIf { it.isNotBlank() }
            ?: json?.optString("error_description")?.takeIf { it.isNotBlank() }
            ?: json?.optString("message")?.takeIf { it.isNotBlank() }
        val base = when (error.code) {
            400, 401, 422 -> text(R.string.cloud_error_invalid_credentials)
            429 -> text(R.string.cloud_error_rate_limit)
            else -> text(R.string.cloud_error_auth_http, error.code)
        }
        return detail?.let { "$base $it" } ?: base
    }

    private fun apiErrorMessage(error: HttpResult.Error): String {
        if (error.code == null) return text(R.string.cloud_error_network_data)
        val json = runCatching { JSONObject(error.body) }.getOrNull()
        val detail = json?.optString("message")?.takeIf { it.isNotBlank() }
            ?: json?.optString("details")?.takeIf { it.isNotBlank() }
            ?: json?.optString("hint")?.takeIf { it.isNotBlank() }
        val base = text(R.string.cloud_error_data_http, error.code)
        return detail?.let { "$base $it" } ?: base
    }

    private fun text(@StringRes resId: Int, vararg args: Any): String =
        context.getString(resId, *args)

    private fun buildDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        return listOf(manufacturer, model)
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .joinToString(" ")
            .ifBlank { "Android device" }
            .take(120)
    }

    private fun deviceKey(): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        val raw = "${context.packageName}|$androidId"
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun extractJwtSubject(token: String): String? = runCatching {
        val parts = token.split('.')
        if (parts.size < 2) return null
        val payload = parts[1]
            .replace('-', '+')
            .replace('_', '/')
            .let { it + "=".repeat((4 - it.length % 4) % 4) }
        val decoded = android.util.Base64.decode(payload, android.util.Base64.DEFAULT)
        JSONObject(decoded.toString(Charsets.UTF_8)).optString("sub").takeIf { it.isNotBlank() }
    }.getOrNull()

    private sealed interface HttpResult {
        data class Ok(val code: Int, val body: String) : HttpResult
        data class Error(val code: Int?, val body: String) : HttpResult
    }
}
