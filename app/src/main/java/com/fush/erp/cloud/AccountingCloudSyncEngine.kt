package com.fush.erp.cloud

import android.content.Context
import androidx.room.withTransaction
import com.fush.erp.BuildConfig
import com.fush.erp.R
import com.fush.erp.data.AccountingPostedJournalLifecycleDatabaseGuard
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.AccountEntity
import com.fush.erp.data.entity.AuditEventEntity
import com.fush.erp.data.entity.EmployeeEntity
import com.fush.erp.data.entity.JournalEntryEntity
import com.fush.erp.data.entity.JournalLineEntity
import com.fush.erp.data.entity.PartyVoucherEntity
import com.fush.erp.data.entity.SalesRepresentativeEntity
import com.fush.erp.data.entity.TreasuryAccountEntity
import com.fush.erp.data.entity.UserEntity
import com.fush.erp.domain.AccountingCloudHydrationSourceIdentityPolicy
import com.fush.erp.domain.AccountingIntegrationContract
import com.fush.erp.domain.AccountingPostingIdempotencyPolicy
import com.fush.erp.domain.AccountingPrecision
import com.fush.erp.domain.CloudPostedJournalHydrationPolicy
import com.fush.erp.domain.LegacyTreasuryCloudHydrationPolicy
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.math.abs

/**
 * v177 makes posted accounting journals and generic treasury/party vouchers a conflict-safe,
 * bidirectional cloud domain. Posted journal headers + lines are serialized as one canonical JSON
 * document so cloud publication is atomic and a second device never observes a half journal.
 *
 * The backend RPC is insert/compare, not blind last-write-wins. Same natural key + different
 * content becomes an explicit conflict that must be resolved by KEEP_LOCAL or USE_CLOUD.
 */
internal class AccountingCloudSyncEngine(
    private val context: Context,
    private val db: FushDatabase,
) {
    private val store = AccountingCloudSyncStore(context.applicationContext)

    fun lastSuccessAt(localUserId: Long): Long = store.lastSuccessAt(localUserId)
    fun lastConflicts(localUserId: Long): List<AccountingSyncConflict> = store.conflicts(localUserId)

    /**
     * v198 prerequisite reconciliation. Accounting journals/vouchers depend on company master
     * references that historically were not mirrored (custom ledger accounts, employees and
     * sales representatives). To avoid a server DDL dependency, one semantically unchanged
     * canonical cloud journal carries a versioned reference snapshot in its JSON content.
     * OWNER/ADMIN may refresh that metadata; every company member may hydrate missing local
     * references from it before any journal/voucher is applied. Existing local master rows are
     * never overwritten here.
     */
    suspend fun syncReferencePrerequisites(
        localUser: UserEntity,
        session: CloudSession,
        cloudRole: String,
    ): CloudOperationResult<AccountingReferenceSyncResult> = try {
        val role = cloudRole.trim().uppercase()
        val reconciled = reconcileReferencePrerequisites(localUser, session, role, fetchAll(session))
        val completedAt = System.currentTimeMillis()
        val result = reconciled.result.copy(completedAtEpochMillis = completedAt)
        if (result.publishedSnapshot || result.downloadedRows > 0) {
            db.governanceDao().insertAudit(
                AuditEventEntity(
                    userId = localUser.id,
                    action = "CLOUD_ACCOUNTING_REFERENCE_PREREQUISITES",
                    entityType = "SYSTEM",
                    entityId = session.requireOrganizationId(),
                    newValue = "snapshotUp=${result.publishedSnapshot};accounts=${result.downloadedAccounts};treasuries=${result.downloadedTreasuries};employees=${result.downloadedEmployees};reps=${result.downloadedSalesRepresentatives}",
                    reason = "Accounting prerequisite reference reconciliation before GL/Treasury hydration",
                    deviceInfo = "ANDROID_CLOUD_SYNC_V198",
                )
            )
        }
        CloudOperationResult.Success(result)
    } catch (error: Throwable) {
        CloudOperationResult.Failure(error.message?.takeIf { it.isNotBlank() } ?: "تعذر مزامنة مرجعيات المحاسبة")
    }

    suspend fun sync(
        localUser: UserEntity,
        session: CloudSession,
        cloudRole: String,
    ): CloudOperationResult<AccountingCloudSyncResult> = try {
        val role = cloudRole.trim().uppercase() // retained for conflict-resolution + v198 snapshot authority
        val canPublish = true // v185: active organization membership authorizes sync transport
        var remote = fetchAll(session)
        val tombstoneDeletes = applyDeletionTombstones(remote, localUser)
        remote = remote.withoutSupportTestDeletionTombstones()
        remote = reconcileReferencePrerequisites(localUser, session, role, remote).remote
        val referenceCarrier = referenceSnapshotCarrier(remote)
        val localJournals = localJournalPayloads(referenceCarrier?.first, referenceCarrier?.second)
        val localVouchers = localVoucherPayloads()
        val remoteJournalKeysBefore = remote.journals.mapTo(linkedSetOf()) { it.optString("entry_no").uppercase() }
        val remoteVoucherKeysBefore = remote.vouchers.mapTo(linkedSetOf()) { it.optString("voucher_no").uppercase() }
        val bootstrapped = remote.journals.isEmpty() && remote.vouchers.isEmpty() && canPublish && (localJournals.isNotEmpty() || localVouchers.isNotEmpty())

        if (canPublish && (localJournals.isNotEmpty() || localVouchers.isNotEmpty())) {
            publishBatch(localJournals, localVouchers, session)
            remote = fetchAll(session).withoutSupportTestDeletionTombstones()
            // If this was the first accounting publication, there is now a safe carrier journal
            // for the v198 prerequisite snapshot. Refresh/apply it before hydrating any journals.
            remote = reconcileReferencePrerequisites(localUser, session, role, remote).remote
        }

        val conflicts = mutableListOf<AccountingSyncConflict>()
        var downloadedJournals = tombstoneDeletes.journals
        var unchangedJournals = 0
        var downloadedVouchers = tombstoneDeletes.vouchers
        var unchangedVouchers = 0

        remote.journals.sortedBy { it.optString("entry_no") }.forEach { row ->
            val payload = row.contentObject()
            val key = row.optString("entry_no")
            val existing = db.journalDao().byEntryNo(key)
            if (existing == null) {
                when (val outcome = applyJournalFromCloud(localUser, payload, force = false)) {
                    is ApplyOutcome.Applied -> downloadedJournals++
                    is ApplyOutcome.Conflict -> conflicts += outcome.conflict
                }
            } else {
                val localPayload = journalPayload(existing)
                val differences = journalDifferences(localPayload, payload)
                if (differences.isEmpty()) unchangedJournals++ else conflicts += AccountingSyncConflict("JOURNAL", key, differences)
            }
        }

        // Vouchers are applied after journals so journalEntryId/reversalJournalEntryId can be resolved locally.
        remote.vouchers.sortedBy { it.optString("voucher_no") }.forEach { row ->
            val payload = row.contentObject()
            val key = row.optString("voucher_no")
            val existing = db.partyDao().voucherByNo(key)
            if (existing == null) {
                when (val outcome = applyVoucherFromCloud(localUser, payload, force = false)) {
                    is ApplyOutcome.Applied -> downloadedVouchers++
                    is ApplyOutcome.Conflict -> conflicts += outcome.conflict
                }
            } else {
                val localPayload = voucherPayload(existing)
                val differences = voucherDifferences(localPayload, payload)
                if (differences.isEmpty()) unchangedVouchers++ else conflicts += AccountingSyncConflict("TREASURY_VOUCHER", key, differences)
            }
        }

        val dedupedConflicts = conflicts
            .groupBy { "${it.entityType}:${it.entityKey.uppercase()}" }
            .map { (_, rows) -> rows.first().copy(differences = rows.flatMap { it.differences }.distinctBy { "${it.field}|${it.localValue}|${it.cloudValue}" }) }
            .sortedWith(compareBy({ it.entityType }, { it.entityKey }))

        val treasuryCheck = treasuryBalanceCheck(remote)
        val localJournalKeys = localJournals.mapTo(linkedSetOf()) { it.optString("entry_no").uppercase() }
        val localVoucherKeys = localVouchers.mapTo(linkedSetOf()) { it.optString("voucher_no").uppercase() }
        val finalRemoteJournalKeys = remote.journals.mapTo(linkedSetOf()) { it.optString("entry_no").uppercase() }
        val finalRemoteVoucherKeys = remote.vouchers.mapTo(linkedSetOf()) { it.optString("voucher_no").uppercase() }
        val completedAt = System.currentTimeMillis()

        store.saveConflicts(localUser.id, dedupedConflicts)
        store.markSuccess(localUser.id, completedAt)
        val result = AccountingCloudSyncResult(
            uploadedJournals = if (canPublish) (localJournalKeys - remoteJournalKeysBefore).count { it in finalRemoteJournalKeys } else 0,
            downloadedJournals = downloadedJournals,
            unchangedJournals = unchangedJournals,
            uploadedTreasuryVouchers = if (canPublish) (localVoucherKeys - remoteVoucherKeysBefore).count { it in finalRemoteVoucherKeys } else 0,
            downloadedTreasuryVouchers = downloadedVouchers,
            unchangedTreasuryVouchers = unchangedVouchers,
            skippedLocalJournals = if (canPublish) 0 else (localJournalKeys - finalRemoteJournalKeys).size,
            skippedLocalTreasuryVouchers = if (canPublish) 0 else (localVoucherKeys - finalRemoteVoucherKeys).size,
            conflicts = dedupedConflicts.size,
            treasuryAccountsChecked = treasuryCheck.first,
            treasuryBalanceDifferenceBase = treasuryCheck.second,
            bootstrappedCloud = bootstrapped,
            completedAtEpochMillis = completedAt,
            conflictDetails = dedupedConflicts,
        )

        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = localUser.id,
                action = "CLOUD_ACCOUNTING_BIDIRECTIONAL_SYNC",
                entityType = "SYSTEM",
                entityId = session.requireOrganizationId(),
                newValue = "journalsUp=${result.uploadedJournals};journalsDown=${result.downloadedJournals};vouchersUp=${result.uploadedTreasuryVouchers};vouchersDown=${result.downloadedTreasuryVouchers};conflicts=${result.conflicts};treasuryDiff=${formatNumber(result.treasuryBalanceDifferenceBase)}",
                reason = if (bootstrapped) "Accounting cloud bootstrap" else "General ledger + treasury bidirectional sync",
                deviceInfo = "ANDROID_CLOUD_SYNC_V177",
            )
        )
        CloudOperationResult.Success(result)
    } catch (error: Throwable) {
        CloudOperationResult.Failure(
            error.message?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.cloud_accounting_sync_failed)
        )
    }

    /**
     * v200 publishes durable accounting tombstones for the voucher/journal behind a deliberately
     * deleted test shipment expense. The tombstone is the original valid accounting payload plus
     * explicit deletion metadata, so the already-deployed v177 RPC continues to validate balance
     * and references without any Supabase migration.
     */
    internal suspend fun publishSupportDeletionForShipmentExpenseAccounting(
        localUser: UserEntity,
        session: CloudSession,
        cloudRole: String,
        expenseId: Long,
        reason: String,
    ) {
        require(cloudRole.trim().uppercase() in ACCOUNTING_AUTHORITY_ROLES) {
            "حذف الأثر المحاسبي الاختباري من السحابة يتطلب OWNER أو ADMIN أو ACCOUNTANT"
        }
        val expense = requireNotNull(db.shipmentDao().expenseById(expenseId)) { "مصروف الشحنة غير موجود" }
        val voucher = requireNotNull(db.partyDao().voucherByNo(expense.paymentVoucherNo)) { "سند مصروف الشحنة غير موجود" }
        val journal = requireNotNull(db.journalDao().byId(voucher.journalEntryId)) { "قيد مصروف الشحنة غير موجود" }
        val deletedAt = System.currentTimeMillis()
        val reasonHash = sha256Hex(reason.trim())
        val journalTombstone = journalPayload(journal)
            .put(TOMBSTONE_DELETED_FIELD, true)
            .put(TOMBSTONE_SCOPE_FIELD, TOMBSTONE_SCOPE_TEST_SUPPORT)
            .put(TOMBSTONE_VERSION_FIELD, TOMBSTONE_VERSION)
            .put(TOMBSTONE_DELETED_AT_FIELD, deletedAt)
            .put(TOMBSTONE_REASON_HASH_FIELD, reasonHash)
        val voucherTombstone = voucherPayload(voucher)
            .put(TOMBSTONE_DELETED_FIELD, true)
            .put(TOMBSTONE_SCOPE_FIELD, TOMBSTONE_SCOPE_TEST_SUPPORT)
            .put(TOMBSTONE_VERSION_FIELD, TOMBSTONE_VERSION)
            .put(TOMBSTONE_DELETED_AT_FIELD, deletedAt)
            .put(TOMBSTONE_REASON_HASH_FIELD, reasonHash)

        // The journal is published first by publishBatch, preserving the voucher->journal cloud
        // contract even when the tombstone is the first row ever seen by the backend.
        publishBatch(listOf(journalTombstone), listOf(voucherTombstone), session)
        var remote = fetchAll(session)
        val remoteJournal = remote.journals.firstOrNull { it.optString("entry_no").equals(journal.entryNo, true) }
            ?: error("تعذر العثور على القيد الاختباري في السحابة بعد نشر Tombstone")
        if (!remoteJournal.contentObject().isSupportTestDeletionTombstone()) {
            resolveCloud("JOURNAL", journal.entryNo, "KEEP_LOCAL", journalTombstone, session)
        }
        remote = fetchAll(session)
        val remoteVoucher = remote.vouchers.firstOrNull { it.optString("voucher_no").equals(voucher.voucherNo, true) }
            ?: error("تعذر العثور على سند الاختبار في السحابة بعد نشر Tombstone")
        if (!remoteVoucher.contentObject().isSupportTestDeletionTombstone()) {
            resolveCloud("TREASURY_VOUCHER", voucher.voucherNo, "KEEP_LOCAL", voucherTombstone, session)
        }
        remote = fetchAll(session)
        require(
            remote.journals.firstOrNull { it.optString("entry_no").equals(journal.entryNo, true) }
                ?.contentObject()?.isSupportTestDeletionTombstone() == true
        ) { "فشل تثبيت Tombstone القيد الاختباري في السحابة" }
        require(
            remote.vouchers.firstOrNull { it.optString("voucher_no").equals(voucher.voucherNo, true) }
                ?.contentObject()?.isSupportTestDeletionTombstone() == true
        ) { "فشل تثبيت Tombstone سند الاختبار في السحابة" }

        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = localUser.id,
                action = "CLOUD_SUPPORT_TEST_ACCOUNTING_DELETE_PUBLISHED",
                entityType = "TREASURY_VOUCHER",
                entityId = voucher.voucherNo,
                newValue = "journal=${journal.entryNo};tombstone=v$TOMBSTONE_VERSION",
                reason = "Support test cleanup tombstone",
                deviceInfo = "ANDROID_CLOUD_SYNC_V200",
            )
        )
    }

    suspend fun resolveConflict(
        localUser: UserEntity,
        session: CloudSession,
        cloudRole: String,
        conflict: AccountingSyncConflict,
        resolution: AccountingConflictResolution,
    ): CloudOperationResult<AccountingCloudSyncResult> = try {
        val role = cloudRole.trim().uppercase()
        when (resolution) {
            AccountingConflictResolution.KEEP_LOCAL -> {
                if (role !in ACCOUNTING_AUTHORITY_ROLES) {
                    return CloudOperationResult.Failure(context.getString(R.string.cloud_accounting_resolution_authority_required))
                }
                val localPayload = when (conflict.entityType) {
                    "JOURNAL" -> db.journalDao().byEntryNo(conflict.entityKey)?.let { journalPayload(it) }
                    "TREASURY_VOUCHER" -> db.partyDao().voucherByNo(conflict.entityKey)?.let { voucherPayload(it) }
                    else -> null
                } ?: return CloudOperationResult.Failure(context.getString(R.string.cloud_accounting_conflict_local_missing))
                resolveCloud(conflict.entityType, conflict.entityKey, "KEEP_LOCAL", localPayload, session)
            }
            AccountingConflictResolution.USE_CLOUD -> {
                val remote = fetchAll(session)
                val row = when (conflict.entityType) {
                    "JOURNAL" -> remote.journals.firstOrNull { it.optString("entry_no").equals(conflict.entityKey, true) }
                    "TREASURY_VOUCHER" -> remote.vouchers.firstOrNull { it.optString("voucher_no").equals(conflict.entityKey, true) }
                    else -> null
                } ?: return CloudOperationResult.Failure(context.getString(R.string.cloud_accounting_conflict_cloud_missing))
                val payload = row.contentObject()
                val outcome = when (conflict.entityType) {
                    "JOURNAL" -> applyJournalFromCloud(localUser, payload, force = true)
                    "TREASURY_VOUCHER" -> applyVoucherFromCloud(localUser, payload, force = true)
                    else -> ApplyOutcome.Conflict(AccountingSyncConflict(conflict.entityType, conflict.entityKey, listOf(AccountingSyncConflictDifference("type", "unsupported", conflict.entityType))))
                }
                if (outcome is ApplyOutcome.Conflict) return CloudOperationResult.Failure(outcome.conflict.differences.joinToString { it.field })
                resolveCloud(conflict.entityType, conflict.entityKey, "KEEP_CLOUD", null, session)
            }
        }
        sync(localUser, session, cloudRole)
    } catch (error: Throwable) {
        CloudOperationResult.Failure(error.message ?: context.getString(R.string.cloud_accounting_resolution_failed))
    }

    private suspend fun reconcileReferencePrerequisites(
        localUser: UserEntity,
        session: CloudSession,
        cloudRole: String,
        initialRemote: RemoteData,
    ): ReferenceReconcile {
        var remote = initialRemote
        var published = false

        if (cloudRole in REFERENCE_SNAPSHOT_PUBLISH_ROLES && remote.journals.isNotEmpty()) {
            val desired = buildReferenceSnapshot()
            val desiredHash = desired.getString("snapshot_hash")
            val carrier = findSafeReferenceSnapshotCarrier(remote)
            if (carrier != null) {
                val carrierContent = carrier.contentObject()
                val cloudHash = carrierContent.optJSONObject(REFERENCE_SNAPSHOT_FIELD)
                    ?.optString("snapshot_hash")
                    ?.takeIf { it.isNotBlank() }
                if (cloudHash != desiredHash) {
                    val replacement = JSONObject(carrierContent.toString())
                        .put(REFERENCE_SNAPSHOT_FIELD, desired)
                    resolveCloud(
                        entityType = "JOURNAL",
                        entityKey = carrier.optString("entry_no"),
                        resolution = "KEEP_LOCAL",
                        replacement = replacement,
                        session = session,
                    )
                    published = true
                    remote = fetchAll(session)
                }
            }
        }

        val applied = applyLatestReferenceSnapshot(localUser, remote)
        return ReferenceReconcile(
            remote = remote,
            result = AccountingReferenceSyncResult(
                publishedSnapshot = published,
                downloadedAccounts = applied.accounts,
                downloadedTreasuries = applied.treasuries,
                downloadedEmployees = applied.employees,
                downloadedSalesRepresentatives = applied.salesRepresentatives,
                snapshotHash = applied.snapshotHash,
                completedAtEpochMillis = 0L,
            )
        )
    }

    /**
     * Do not use a journal with a real semantic conflict as the metadata carrier. The snapshot is
     * attached only to a journal whose local and cloud accounting content already match. Unknown
     * metadata fields are intentionally ignored by journalDifferences().
     */
    private suspend fun findSafeReferenceSnapshotCarrier(remote: RemoteData): JSONObject? {
        for (row in remote.journals.sortedBy { it.optString("entry_no") }) {
            val entryNo = row.optString("entry_no")
            val local = db.journalDao().byEntryNo(entryNo) ?: continue
            if (!local.status.equals("POSTED", ignoreCase = true)) continue
            val cloudContent = row.contentObject()
            if (journalDifferences(journalPayload(local), cloudContent).isEmpty()) return row
        }
        return null
    }

    private fun referenceSnapshotCarrier(remote: RemoteData): Pair<String, JSONObject>? {
        return remote.journals
            .mapNotNull { row ->
                val snapshot = row.contentObject().optJSONObject(REFERENCE_SNAPSHOT_FIELD) ?: return@mapNotNull null
                if (snapshot.optInt("schema_version", 0) != REFERENCE_SNAPSHOT_SCHEMA_VERSION) return@mapNotNull null
                row.optString("entry_no") to snapshot
            }
            .maxByOrNull { (_, snapshot) -> snapshot.optLong("generated_at_ms", 0L) }
    }

    private suspend fun buildReferenceSnapshot(): JSONObject {
        val accounts = db.accountDao().allRows().sortedBy { it.code.uppercase() }
        val accountCodeById = accounts.associate { it.id to it.code }
        val employees = db.employeeDao().allEmployeesForCloudReferenceSync().sortedBy { it.code.uppercase() }
        val employeeCodeById = employees.associate { it.id to it.code }
        val reps = db.salesRepresentativeDao().allForCloudReferenceSync().sortedBy { it.code.uppercase() }
        val treasuries = db.accountingDao().allTreasury().sortedBy { it.code.uppercase() }

        val snapshot = JSONObject()
            .put("schema_version", REFERENCE_SNAPSHOT_SCHEMA_VERSION)
            .put("hash_version", REFERENCE_SNAPSHOT_HASH_VERSION)
            .put("accounts", JSONArray().apply {
                accounts.forEach { row ->
                    put(
                        JSONObject()
                            .put("code", row.code)
                            .put("name_ar", row.nameAr)
                            .put("name_en", row.nameEn)
                            .put("type", row.type)
                            .put("parent_code", row.parentCode ?: JSONObject.NULL)
                            .put("is_posting", row.isPosting)
                            .put("is_active", row.isActive)
                    )
                }
            })
            .put("treasuries", JSONArray().apply {
                treasuries.forEach { row ->
                    val ledgerCode = accountCodeById[row.accountId]
                        ?: error("Missing ledger account ${row.accountId} for treasury ${row.code}")
                    put(
                        JSONObject()
                            .put("code", row.code)
                            .put("group_code", row.groupCode.ifBlank { row.code })
                            .put("name_ar", row.nameAr)
                            .put("kind", row.kind)
                            .put("ledger_account_code", ledgerCode)
                            .put("currency_code", row.currencyCode)
                            .put("bank_name", row.bankName)
                            .put("account_number", row.accountNumber)
                            .put("is_active", row.isActive)
                            .put("created_at_ms", row.createdAt)
                    )
                }
            })
            .put("employees", JSONArray().apply {
                employees.forEach { row ->
                    put(
                        JSONObject()
                            .put("code", row.code)
                            .put("full_name_ar", row.fullNameAr)
                            .put("full_name_en", row.fullNameEn)
                            .put("phone", row.phone)
                            .put("job_title", row.jobTitle)
                            .put("department", row.department)
                            .put("hire_date_ms", row.hireDate)
                            .put("status", row.status)
                            .put("notes", row.notes)
                            .put("created_at_ms", row.createdAt)
                    )
                }
            })
            .put("sales_representatives", JSONArray().apply {
                reps.forEach { row ->
                    put(
                        JSONObject()
                            .put("code", row.code)
                            .put("employee_code", row.employeeId?.let(employeeCodeById::get) ?: JSONObject.NULL)
                            .put("rep_type", row.repType)
                            .put("full_name_ar", row.fullNameAr)
                            .put("full_name_en", row.fullNameEn)
                            .put("phone", row.phone)
                            .put("territory", row.territory)
                            .put("commission_rate_pct", row.commissionRatePct)
                            .put("free_qty_limit_pct", row.freeQtyLimitPct)
                            .put("status", row.status)
                            .put("notes", row.notes)
                            .put("created_at_ms", row.createdAt)
                            .put("updated_at_ms", row.updatedAt)
                    )
                }
            })

        snapshot.put("snapshot_hash", referenceSnapshotHash(snapshot))
        snapshot.put("generated_at_ms", System.currentTimeMillis())
        return snapshot
    }

    private suspend fun applyLatestReferenceSnapshot(localUser: UserEntity, remote: RemoteData): ReferenceApplyCounts {
        val snapshot = remote.journals
            .mapNotNull { row -> row.contentObject().optJSONObject(REFERENCE_SNAPSHOT_FIELD) }
            .filter { it.optInt("schema_version", 0) == REFERENCE_SNAPSHOT_SCHEMA_VERSION }
            .maxByOrNull { it.optLong("generated_at_ms", 0L) }
            ?: return ReferenceApplyCounts()

        val expectedHash = snapshot.optString("snapshot_hash")
        require(expectedHash.isNotBlank()) { "CLOUD_ACCOUNTING_REFERENCE_SNAPSHOT_HASH_MISSING" }
        validateReferenceSnapshotShape(snapshot)
        val hashVersion = snapshot.optInt("hash_version", LEGACY_REFERENCE_SNAPSHOT_HASH_VERSION)
        val actualHash = referenceSnapshotHash(snapshot)
        if (hashVersion >= REFERENCE_SNAPSHOT_HASH_VERSION) {
            require(expectedHash == actualHash) { "CLOUD_ACCOUNTING_REFERENCE_SNAPSHOT_HASH_MISMATCH" }
        } else {
            val legacyActualHash = legacyReferenceSnapshotHash(snapshot)
            if (expectedHash != legacyActualHash) {
                // v198 hashed the in-memory org.json number spelling. PostgreSQL JSONB may round-trip
                // 0.0 as 0 (and similar semantically identical numeric spellings), so the old hash
                // can legitimately change in transit. Strict shape validation above plus the v199
                // canonical semantic hash makes this one-time legacy bridge safe and deterministic.
                require(snapshot.optInt("schema_version", 0) == REFERENCE_SNAPSHOT_SCHEMA_VERSION) {
                    "CLOUD_ACCOUNTING_REFERENCE_SNAPSHOT_HASH_MISMATCH"
                }
            }
        }

        var accountsAdded = 0
        var treasuriesAdded = 0
        var employeesAdded = 0
        var repsAdded = 0

        db.withTransaction {
            val accountRows = snapshot.optJSONArray("accounts") ?: JSONArray()
            for (index in 0 until accountRows.length()) {
                val row = accountRows.getJSONObject(index)
                val code = row.optString("code").trim()
                if (code.isBlank() || db.accountDao().byCode(code) != null) continue
                db.accountDao().insert(
                    AccountEntity(
                        code = code,
                        nameAr = row.optString("name_ar"),
                        nameEn = row.optString("name_en"),
                        type = row.optString("type"),
                        parentCode = row.optNullableString("parent_code"),
                        isPosting = row.optBoolean("is_posting", true),
                        isActive = row.optBoolean("is_active", true),
                    )
                )
                accountsAdded++
            }

            val treasuryRows = snapshot.optJSONArray("treasuries") ?: JSONArray()
            for (index in 0 until treasuryRows.length()) {
                val row = treasuryRows.getJSONObject(index)
                val code = row.optString("code").trim()
                if (code.isBlank() || db.accountingDao().treasuryByCode(code) != null) continue
                val ledgerCode = row.optString("ledger_account_code").trim()
                val ledger = db.accountDao().byCode(ledgerCode)
                    ?: error("CLOUD_REFERENCE_TREASURY_LEDGER_MISSING:$code:$ledgerCode")
                if (db.accountingDao().treasuryByAccountId(ledger.id) != null) continue
                db.accountingDao().insertTreasury(
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
                treasuriesAdded++
            }

            val employeeRows = snapshot.optJSONArray("employees") ?: JSONArray()
            for (index in 0 until employeeRows.length()) {
                val row = employeeRows.getJSONObject(index)
                val code = row.optString("code").trim()
                if (code.isBlank() || db.employeeDao().employeeByCode(code) != null) continue
                db.employeeDao().insertEmployee(
                    EmployeeEntity(
                        code = code,
                        fullNameAr = row.optString("full_name_ar"),
                        fullNameEn = row.optString("full_name_en"),
                        phone = row.optString("phone"),
                        jobTitle = row.optString("job_title"),
                        department = row.optString("department"),
                        hireDate = row.optLong("hire_date_ms", System.currentTimeMillis()),
                        status = row.optString("status", "ACTIVE"),
                        notes = row.optString("notes"),
                        createdBy = localUser.id,
                        createdAt = row.optLong("created_at_ms", System.currentTimeMillis()),
                    )
                )
                employeesAdded++
            }

            val repRows = snapshot.optJSONArray("sales_representatives") ?: JSONArray()
            for (index in 0 until repRows.length()) {
                val row = repRows.getJSONObject(index)
                val code = row.optString("code").trim()
                if (code.isBlank() || db.salesRepresentativeDao().byCode(code) != null) continue
                val employeeCode = row.optNullableString("employee_code")
                val employeeId = employeeCode?.let { employeeCodeValue ->
                    db.employeeDao().employeeByCode(employeeCodeValue)?.id
                        ?: error("CLOUD_REFERENCE_REP_EMPLOYEE_MISSING:$code:$employeeCodeValue")
                }
                if (employeeId != null && db.salesRepresentativeDao().byEmployeeId(employeeId) != null) continue
                db.salesRepresentativeDao().insert(
                    SalesRepresentativeEntity(
                        code = code,
                        employeeId = employeeId,
                        repType = row.optString("rep_type", "EXTERNAL"),
                        fullNameAr = row.optString("full_name_ar"),
                        fullNameEn = row.optString("full_name_en"),
                        phone = row.optString("phone"),
                        territory = row.optString("territory"),
                        commissionRatePct = row.optDouble("commission_rate_pct", 0.0),
                        freeQtyLimitPct = row.optDouble("free_qty_limit_pct", 0.0),
                        status = row.optString("status", "ACTIVE"),
                        notes = row.optString("notes"),
                        createdBy = localUser.id,
                        createdAt = row.optLong("created_at_ms", System.currentTimeMillis()),
                        updatedAt = row.optLong("updated_at_ms", System.currentTimeMillis()),
                    )
                )
                repsAdded++
            }
        }

        return ReferenceApplyCounts(
            accounts = accountsAdded,
            treasuries = treasuriesAdded,
            employees = employeesAdded,
            salesRepresentatives = repsAdded,
            snapshotHash = expectedHash,
        )
    }

    private fun referenceSnapshotHash(snapshot: JSONObject): String {
        val canonical = buildString {
            append("schema=").append(snapshot.optInt("schema_version", 0)).append('\n')
            append("hash=").append(REFERENCE_SNAPSHOT_HASH_VERSION).append('\n')
            appendCanonicalSnapshotRows(snapshot.optJSONArray("accounts") ?: JSONArray(), ACCOUNT_SNAPSHOT_FIELDS)
            appendCanonicalSnapshotRows(snapshot.optJSONArray("treasuries") ?: JSONArray(), TREASURY_SNAPSHOT_FIELDS)
            appendCanonicalSnapshotRows(snapshot.optJSONArray("employees") ?: JSONArray(), EMPLOYEE_SNAPSHOT_FIELDS)
            appendCanonicalSnapshotRows(snapshot.optJSONArray("sales_representatives") ?: JSONArray(), SALES_REP_SNAPSHOT_FIELDS)
        }
        return sha256Hex(canonical)
    }

    private fun legacyReferenceSnapshotHash(snapshot: JSONObject): String {
        val canonical = buildString {
            append("v=").append(snapshot.optInt("schema_version", 0)).append('\n')
            appendLegacySnapshotRows(snapshot.optJSONArray("accounts") ?: JSONArray(), ACCOUNT_SNAPSHOT_FIELDS)
            appendLegacySnapshotRows(snapshot.optJSONArray("treasuries") ?: JSONArray(), TREASURY_SNAPSHOT_FIELDS)
            appendLegacySnapshotRows(snapshot.optJSONArray("employees") ?: JSONArray(), EMPLOYEE_SNAPSHOT_FIELDS)
            appendLegacySnapshotRows(snapshot.optJSONArray("sales_representatives") ?: JSONArray(), SALES_REP_SNAPSHOT_FIELDS)
        }
        return sha256Hex(canonical)
    }

    private fun StringBuilder.appendCanonicalSnapshotRows(array: JSONArray, fields: List<String>) {
        val rows = buildList {
            for (index in 0 until array.length()) {
                val row = array.getJSONObject(index)
                add(fields.joinToString("\u001f") { field -> canonicalSnapshotValue(row, field) })
            }
        }.sorted()
        rows.forEach { append(it).append('\n') }
    }

    private fun canonicalSnapshotValue(row: JSONObject, field: String): String {
        val value = if (!row.has(field) || row.isNull(field)) null else row.get(field)
        return canonicalReferenceSnapshotScalar(value)
    }

    private fun StringBuilder.appendLegacySnapshotRows(array: JSONArray, fields: List<String>) {
        val rows = buildList {
            for (index in 0 until array.length()) {
                val row = array.getJSONObject(index)
                add(fields.joinToString("\u001f") { field ->
                    if (!row.has(field) || row.isNull(field)) "∅" else row.get(field).toString()
                })
            }
        }.sorted()
        rows.forEach { append(it).append('\n') }
    }

    private fun validateReferenceSnapshotShape(snapshot: JSONObject) {
        require(snapshot.optInt("schema_version", 0) == REFERENCE_SNAPSHOT_SCHEMA_VERSION) {
            "CLOUD_ACCOUNTING_REFERENCE_SNAPSHOT_SCHEMA_UNSUPPORTED"
        }
        validateReferenceRows(snapshot, "accounts", ACCOUNT_SNAPSHOT_FIELDS, "code")
        validateReferenceRows(snapshot, "treasuries", TREASURY_SNAPSHOT_FIELDS, "code")
        validateReferenceRows(snapshot, "employees", EMPLOYEE_SNAPSHOT_FIELDS, "code")
        validateReferenceRows(snapshot, "sales_representatives", SALES_REP_SNAPSHOT_FIELDS, "code")
    }

    private fun validateReferenceRows(snapshot: JSONObject, key: String, fields: List<String>, identityField: String) {
        val array = snapshot.optJSONArray(key) ?: error("CLOUD_ACCOUNTING_REFERENCE_SNAPSHOT_INVALID:$key")
        val identities = linkedSetOf<String>()
        for (index in 0 until array.length()) {
            val row = array.optJSONObject(index) ?: error("CLOUD_ACCOUNTING_REFERENCE_SNAPSHOT_INVALID:$key:$index")
            val identity = row.optString(identityField).trim().uppercase()
            require(identity.isNotBlank() && identities.add(identity)) {
                "CLOUD_ACCOUNTING_REFERENCE_SNAPSHOT_INVALID:$key:$identity"
            }
            fields.forEach { field ->
                require(row.has(field)) { "CLOUD_ACCOUNTING_REFERENCE_SNAPSHOT_INVALID:$key:$identity:$field" }
            }
        }
    }

    private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private suspend fun localJournalPayloads(
        referenceCarrierEntryNo: String? = null,
        referenceSnapshot: JSONObject? = null,
    ): List<JSONObject> = db.journalDao().allPostedForCloudSync().map { entry ->
        val payload = journalPayload(entry)
        if (referenceSnapshot != null && entry.entryNo.equals(referenceCarrierEntryNo, ignoreCase = true)) {
            payload.put(REFERENCE_SNAPSHOT_FIELD, JSONObject(referenceSnapshot.toString()))
        }
        payload
    }
    private suspend fun localVoucherPayloads(): List<JSONObject> = db.partyDao().allVouchersForCloudSync().map { voucherPayload(it) }

    private suspend fun journalPayload(entry: JournalEntryEntity): JSONObject {
        val lines = db.journalDao().linesForEntry(entry.id)
        val lineArray = JSONArray()
        lines.forEachIndexed { index, line ->
            val accountCode = db.accountDao().byId(line.accountId)?.code ?: error("Missing account ${line.accountId} for ${entry.entryNo}")
            lineArray.put(
                JSONObject()
                    .put("line_no", index + 1)
                    .put("account_code", accountCode)
                    .put("debit_scaled", line.debitScaled)
                    .put("credit_scaled", line.creditScaled)
                    .put("memo", line.memo)
            )
        }
        val reversalOf = if (entry.sourceType.equals("REVERSAL", true)) {
            entry.sourceId?.toLongOrNull()?.let { db.journalDao().byId(it)?.entryNo }
        } else null
        return JSONObject()
            .put("entry_no", entry.entryNo)
            .put("entry_date_ms", entry.entryDate)
            .put("description", entry.description)
            .put("currency_code", entry.currencyCode)
            .put("exchange_rate_scaled", entry.exchangeRateScaled)
            .put("source_type", entry.sourceType)
            .put("source_ref", stableSourceRef(entry))
            .put("reversal_of_entry_no", reversalOf ?: JSONObject.NULL)
            .put("created_at_ms", entry.createdAt)
            .put("lines", lineArray)
    }

    private suspend fun stableSourceRef(entry: JournalEntryEntity): String {
        if (entry.sourceType.equals("REVERSAL", true)) {
            return entry.sourceId?.toLongOrNull()?.let { db.journalDao().byId(it)?.entryNo }.orEmpty()
        }
        if (entry.sourceType.uppercase().startsWith("PROD") || entry.sourceType.uppercase().startsWith("PRODUCTION")) {
            return entry.sourceId.orEmpty().ifBlank { entry.entryNo.removePrefix("JE-") }
        }
        return entry.entryNo.removePrefix("JE-")
    }

    private suspend fun voucherPayload(row: PartyVoucherEntity): JSONObject {
        val treasury = db.accountingDao().treasuryById(row.treasuryAccountId) ?: error("Missing treasury for ${row.voucherNo}")
        val offset = db.accountDao().byId(row.offsetAccountId) ?: error("Missing offset account for ${row.voucherNo}")
        val journal = db.journalDao().byId(row.journalEntryId) ?: error("Missing journal for ${row.voucherNo}")
        val partyCode = when (row.partyType.uppercase()) {
            "CUSTOMER" -> row.customerId?.let { db.customerDao().byId(it)?.code }
            "SUPPLIER" -> row.supplierId?.let { db.supplierDao().byId(it)?.code }
            "EMPLOYEE" -> row.employeeId?.let { db.employeeDao().employeeById(it)?.code }
            "SALES_REP", "SALESREP" -> row.salesRepId?.let { db.salesRepresentativeDao().byId(it)?.code }
            else -> null
        }
        val reversalJournalNo = row.reversalJournalEntryId?.let { db.journalDao().byId(it)?.entryNo }
        return JSONObject()
            .put("voucher_no", row.voucherNo)
            .put("voucher_type", row.voucherType)
            .put("treasury_code", treasury.code)
            .put("offset_account_code", offset.code)
            .put("party_type", row.partyType)
            .put("party_code", partyCode ?: JSONObject.NULL)
            .put("party_name_snapshot", row.partyNameSnapshot)
            .put("voucher_date_ms", row.voucherDate)
            .put("currency_code", row.currencyCode)
            .put("exchange_rate", row.exchangeRate)
            .put("amount_original", row.amountOriginal)
            .put("amount_base", row.amountBase)
            .put("description", row.description)
            .put("reference_no", row.referenceNo)
            .put("journal_entry_no", journal.entryNo)
            .put("status", row.status)
            .put("reversal_reason", row.reversalReason)
            .put("reversed_at_ms", row.reversedAt ?: JSONObject.NULL)
            .put("reversal_journal_entry_no", reversalJournalNo ?: JSONObject.NULL)
            .put("created_at_ms", row.createdAt)
    }

    private suspend fun applyJournalFromCloud(localUser: UserEntity, payload: JSONObject, force: Boolean): ApplyOutcome {
        val entryNo = payload.getString("entry_no")
        val parsedLines = payload.getJSONArray("lines")
        if (parsedLines.length() == 0) {
            return ApplyOutcome.Conflict(AccountingSyncConflict("JOURNAL", entryNo, listOf(AccountingSyncConflictDifference("lines", "∅", "empty cloud journal"))))
        }
        var debitTotal = 0L
        var creditTotal = 0L
        val resolved = mutableListOf<ResolvedLine>()
        for (index in 0 until parsedLines.length()) {
            val line = parsedLines.getJSONObject(index)
            val accountCode = line.getString("account_code")
            val account = db.accountDao().byCode(accountCode)
                ?: return ApplyOutcome.Conflict(AccountingSyncConflict("JOURNAL", entryNo, listOf(AccountingSyncConflictDifference("account_code", "missing locally", accountCode))))
            val debit = line.optLong("debit_scaled")
            val credit = line.optLong("credit_scaled")
            if (debit < 0L || credit < 0L || (debit > 0L && credit > 0L)) {
                return ApplyOutcome.Conflict(AccountingSyncConflict("JOURNAL", entryNo, listOf(AccountingSyncConflictDifference("line_${index + 1}", "invalid", "debit=$debit credit=$credit"))))
            }
            debitTotal = Math.addExact(debitTotal, debit)
            creditTotal = Math.addExact(creditTotal, credit)
            resolved += ResolvedLine(account.id, debit, credit, line.optString("memo"))
        }
        if (debitTotal != creditTotal || debitTotal <= 0L) {
            return ApplyOutcome.Conflict(AccountingSyncConflict("JOURNAL", entryNo, listOf(AccountingSyncConflictDifference("balance", debitTotal.toString(), creditTotal.toString()))))
        }

        val existing = db.journalDao().byEntryNo(entryNo)
        if (existing != null && !force && existing.status.equals("POSTED", ignoreCase = true)) {
            val diffs = journalDifferences(journalPayload(existing), payload)
            return if (diffs.isEmpty()) ApplyOutcome.Applied else ApplyOutcome.Conflict(AccountingSyncConflict("JOURNAL", entryNo, diffs))
        }
        val sourceType = payload.optString("source_type", "MANUAL").trim().uppercase().ifBlank { "MANUAL" }
        val sourceRef = payload.optString("source_ref")
        val reversalEntryNo = payload.optNullableString("reversal_of_entry_no")
        val legacyTreasuryHydration = LegacyTreasuryCloudHydrationPolicy.requiresCompatibilityHydration(sourceType)
        val cloudHydrationStagingSourceId = CloudPostedJournalHydrationPolicy.stagingSourceId(entryNo)

        // Validate against the same fail-closed catalog that SQLite installs on cold open.
        // The catalog deliberately includes canonical treasury voucher sources (TRANSFER,
        // EXPENSE_PAYMENT, EMPLOYEE_PAYMENT, ADJUSTMENT...) in addition to the historical
        // AccountingIntegrationContract list. Valid v194/v195 cloud rows must never be rejected
        // merely because they use one of those canonical treasury source types.
        val cloudHydratableSource = sourceType in AccountingPostingIdempotencyPolicy.registeredSourceTypes &&
            sourceType != LegacyTreasuryCloudHydrationPolicy.STAGING_ALIAS_SOURCE_TYPE &&
            sourceType != CloudPostedJournalHydrationPolicy.STAGING_ALIAS_SOURCE_TYPE
        if (!cloudHydratableSource) {
            return ApplyOutcome.Conflict(
                AccountingSyncConflict(
                    "JOURNAL",
                    entryNo,
                    listOf(AccountingSyncConflictDifference("source_type", "registered accounting source", sourceType))
                )
            )
        }

        val resolvedLocalSourceId = resolveLocalSourceId(sourceType, sourceRef, reversalEntryNo)
        val sourceId = AccountingCloudHydrationSourceIdentityPolicy.sourceIdForHydration(
            sourceType = sourceType,
            sourceRef = sourceRef,
            entryNo = entryNo,
            resolvedLocalSourceId = resolvedLocalSourceId,
            reversalEntryNo = reversalEntryNo,
        )

        // Fail as an explicit sync conflict instead of letting the SQLite duplicate-source trigger
        // abort the whole accounting sync.  This is a safety net for genuinely replay-safe sources;
        // legacy PRODUCTION_ISSUE is intentionally excluded from replaySafeSourceTypes because old
        // releases legitimately emitted multiple correction journals for one production order.
        if (sourceId != null && sourceType in AccountingPostingIdempotencyPolicy.replaySafeSourceTypes) {
            val sameSource = db.journalDao().bySourceNormalized(sourceType, sourceId)
            if (sameSource != null && sameSource.id != existing?.id) {
                return ApplyOutcome.Conflict(
                    AccountingSyncConflict(
                        "JOURNAL",
                        entryNo,
                        listOf(
                            AccountingSyncConflictDifference(
                                "source_identity",
                                "${sameSource.entryNo} (${sameSource.sourceType}:${sameSource.sourceId})",
                                "$entryNo ($sourceType:$sourceId)",
                            )
                        )
                    )
                )
            }
        }

        db.withTransaction {
            // Cloud hydration must respect the same DB lifecycle as locally-created operational journals:
            // STAGING header -> complete line batch -> exact balance trigger -> POSTED.
            // Never bypass the database guard by inserting a cloud journal directly as POSTED.
            val entryId = if (existing == null) {
                db.journalDao().insertEntry(
                    JournalEntryEntity(
                        entryNo = entryNo,
                        entryDate = payload.optLong("entry_date_ms"),
                        description = payload.optString("description"),
                        currencyCode = payload.optString("currency_code"),
                        legacyExchangeRateReal = AccountingPrecision.rateToDouble(payload.optLong("exchange_rate_scaled", AccountingPrecision.RATE_SCALE)),
                        exchangeRateScaled = payload.optLong("exchange_rate_scaled", AccountingPrecision.RATE_SCALE),
                        // Every already-POSTED cloud journal is hydrated through one internal
                        // STAGING-only alias. This prevents local closed-period/manual-approval
                        // gates from mistaking replication for a brand-new local posting. The
                        // original sourceType/sourceId are restored atomically at finalization.
                        sourceType = CloudPostedJournalHydrationPolicy.STAGING_ALIAS_SOURCE_TYPE,
                        sourceId = cloudHydrationStagingSourceId,
                        status = "STAGING",
                        createdBy = localUser.id,
                        createdAt = payload.optLong("created_at_ms", System.currentTimeMillis()),
                    )
                )
            } else {
                if (!existing.status.equals("STAGING", ignoreCase = true)) {
                    val diffs = journalDifferences(journalPayload(existing), payload)
                    if (diffs.isEmpty()) return@withTransaction existing.id
                    throw IllegalStateException("CLOUD_POSTED_JOURNAL_IMMUTABLE_USE_REVERSAL:$entryNo")
                }
                db.journalDao().updateEntry(
                    existing.copy(
                        entryDate = payload.optLong("entry_date_ms"),
                        description = payload.optString("description"),
                        currencyCode = payload.optString("currency_code"),
                        legacyExchangeRateReal = AccountingPrecision.rateToDouble(payload.optLong("exchange_rate_scaled", AccountingPrecision.RATE_SCALE)),
                        exchangeRateScaled = payload.optLong("exchange_rate_scaled", AccountingPrecision.RATE_SCALE),
                        // Keep the generic cloud-only alias until the final atomic
                        // STAGING -> POSTED transition. The original identity is restored below.
                        sourceType = CloudPostedJournalHydrationPolicy.STAGING_ALIAS_SOURCE_TYPE,
                        sourceId = cloudHydrationStagingSourceId,
                        status = "STAGING",
                    )
                )
                db.journalDao().deleteLinesForEntry(existing.id)
                existing.id
            }
            if (existing == null || existing.status.equals("STAGING", ignoreCase = true)) {
                db.journalDao().insertLinesRaw(
                    resolved.map { line ->
                        JournalLineEntity(
                            entryId = entryId,
                            accountId = line.accountId,
                            legacyDebitReal = AccountingPrecision.amountToDouble(line.debitScaled),
                            legacyCreditReal = AccountingPrecision.amountToDouble(line.creditScaled),
                            debitScaled = line.debitScaled,
                            creditScaled = line.creditScaled,
                            memo = line.memo,
                        )
                    }
                )
                val transitioned = db.journalDao().finalizeCloudHydrationToPosted(
                    entryId = entryId,
                    originalSourceType = sourceType,
                    sourceId = sourceId,
                )
                require(transitioned == 1) { "CLOUD_JOURNAL_FAILED_STAGING_TO_POSTED:$entryNo" }
            }
        }
        return ApplyOutcome.Applied
    }

    private suspend fun resolveLocalSourceId(sourceType: String, sourceRef: String, reversalEntryNo: String?): String? = when (sourceType.uppercase()) {
        "REVERSAL" -> reversalEntryNo?.let { db.journalDao().byEntryNo(it)?.id?.toString() }
        "SALE" -> db.salesDao().invoiceByNo(sourceRef)?.id?.toString()
        "CUSTOMER_RECEIPT" -> db.salesDao().receiptByNo(sourceRef)?.id?.toString()
        "SALES_RETURN" -> db.salesDao().returnByNo(sourceRef)?.id?.toString()
        "PURCHASE" -> db.purchaseDao().invoiceByNo(sourceRef)?.id?.toString()
        "PURCHASE_RETURN" -> db.purchaseDao().returnByNo(sourceRef)?.id?.toString()
        "SUPPLIER_PAYMENT" -> db.purchaseDao().supplierPaymentByNo(sourceRef)?.id?.toString()
        else -> if (sourceType.uppercase().startsWith("PROD") || sourceType.uppercase().startsWith("PRODUCTION")) sourceRef.takeIf { it.isNotBlank() } else null
    }

    private suspend fun applyVoucherFromCloud(localUser: UserEntity, payload: JSONObject, force: Boolean): ApplyOutcome {
        val voucherNo = payload.getString("voucher_no")
        val journalNo = payload.getString("journal_entry_no")
        val journal = db.journalDao().byEntryNo(journalNo)
            ?: return ApplyOutcome.Conflict(AccountingSyncConflict("TREASURY_VOUCHER", voucherNo, listOf(AccountingSyncConflictDifference("journal_entry_no", "missing locally", journalNo))))
        val treasuryCode = payload.getString("treasury_code")
        val treasury = db.accountingDao().treasuryByCode(treasuryCode)
            ?: return ApplyOutcome.Conflict(AccountingSyncConflict("TREASURY_VOUCHER", voucherNo, listOf(AccountingSyncConflictDifference("treasury_code", "missing locally", treasuryCode))))
        val offsetCode = payload.getString("offset_account_code")
        val offset = db.accountDao().byCode(offsetCode)
            ?: return ApplyOutcome.Conflict(AccountingSyncConflict("TREASURY_VOUCHER", voucherNo, listOf(AccountingSyncConflictDifference("offset_account_code", "missing locally", offsetCode))))
        val partyType = payload.optString("party_type", "NONE")
        val partyCode = payload.optNullableString("party_code")
        val party = resolvePartyIds(partyType, partyCode)
        if (party.missingReference != null) {
            return ApplyOutcome.Conflict(AccountingSyncConflict("TREASURY_VOUCHER", voucherNo, listOf(AccountingSyncConflictDifference("party_code", "missing locally", party.missingReference))))
        }
        val reversalJournalId = payload.optNullableString("reversal_journal_entry_no")?.let { db.journalDao().byEntryNo(it)?.id }
        val existing = db.partyDao().voucherByNo(voucherNo)
        if (existing != null && !force) {
            val diffs = voucherDifferences(voucherPayload(existing), payload)
            return if (diffs.isEmpty()) ApplyOutcome.Applied else ApplyOutcome.Conflict(AccountingSyncConflict("TREASURY_VOUCHER", voucherNo, diffs))
        }
        val row = PartyVoucherEntity(
            id = existing?.id ?: 0L,
            voucherNo = voucherNo,
            voucherType = payload.optString("voucher_type"),
            treasuryAccountId = treasury.id,
            offsetAccountId = offset.id,
            customerId = party.customerId,
            supplierId = party.supplierId,
            employeeId = party.employeeId,
            salesRepId = party.salesRepId,
            partyType = partyType,
            partyNameSnapshot = payload.optString("party_name_snapshot"),
            voucherDate = payload.optLong("voucher_date_ms"),
            currencyCode = payload.optString("currency_code"),
            exchangeRate = payload.optDouble("exchange_rate", 1.0),
            amountOriginal = payload.optDouble("amount_original", 0.0),
            amountBase = payload.optDouble("amount_base", 0.0),
            description = payload.optString("description"),
            referenceNo = payload.optString("reference_no"),
            journalEntryId = journal.id,
            status = payload.optString("status", "POSTED"),
            createdBy = localUser.id,
            createdAt = payload.optLong("created_at_ms", System.currentTimeMillis()),
            reversalReason = payload.optString("reversal_reason"),
            reversedBy = if (payload.optNullableLong("reversed_at_ms") != null) localUser.id else null,
            reversedAt = payload.optNullableLong("reversed_at_ms"),
            reversalJournalEntryId = reversalJournalId,
        )
        db.withTransaction {
            if (existing == null) db.partyDao().insertVoucher(row) else db.partyDao().updateVoucher(row)
        }
        return ApplyOutcome.Applied
    }

    private suspend fun resolvePartyIds(type: String, code: String?): PartyIds = when (type.uppercase()) {
        "CUSTOMER" -> code?.let { db.customerDao().byCode(it) }?.let { PartyIds(customerId = it.id) } ?: PartyIds(missingReference = code ?: "CUSTOMER")
        "SUPPLIER" -> code?.let { db.supplierDao().byCode(it) }?.let { PartyIds(supplierId = it.id) } ?: PartyIds(missingReference = code ?: "SUPPLIER")
        "EMPLOYEE" -> code?.let { db.employeeDao().employeeByCode(it) }?.let { PartyIds(employeeId = it.id) } ?: PartyIds(missingReference = code ?: "EMPLOYEE")
        "SALES_REP", "SALESREP" -> code?.let { db.salesRepresentativeDao().byCode(it) }?.let { PartyIds(salesRepId = it.id) } ?: PartyIds(missingReference = code ?: "SALES_REP")
        else -> PartyIds()
    }

    private fun journalDifferences(local: JSONObject, cloud: JSONObject): List<AccountingSyncConflictDifference> = buildList {
        stringDiff("entry_date_ms", local.optLong("entry_date_ms").toString(), cloud.optLong("entry_date_ms").toString())?.let(::add)
        stringDiff("description", local.optString("description"), cloud.optString("description"))?.let(::add)
        stringDiff("currency_code", local.optString("currency_code"), cloud.optString("currency_code"))?.let(::add)
        stringDiff("exchange_rate_scaled", local.optLong("exchange_rate_scaled").toString(), cloud.optLong("exchange_rate_scaled").toString())?.let(::add)
        stringDiff("source_type", local.optString("source_type"), cloud.optString("source_type"))?.let(::add)
        stringDiff("source_ref", local.optString("source_ref"), cloud.optString("source_ref"))?.let(::add)
        stringDiff("reversal_of_entry_no", local.optNullableString("reversal_of_entry_no").orEmpty(), cloud.optNullableString("reversal_of_entry_no").orEmpty())?.let(::add)
        val localLines = canonicalLines(local.optJSONArray("lines") ?: JSONArray())
        val cloudLines = canonicalLines(cloud.optJSONArray("lines") ?: JSONArray())
        if (localLines != cloudLines) add(AccountingSyncConflictDifference("lines", localLines.ifBlank { "∅" }, cloudLines.ifBlank { "∅" }))
    }

    private fun voucherDifferences(local: JSONObject, cloud: JSONObject): List<AccountingSyncConflictDifference> = buildList {
        listOf("voucher_type", "treasury_code", "offset_account_code", "party_type", "party_code", "party_name_snapshot", "currency_code", "description", "reference_no", "journal_entry_no", "status", "reversal_reason", "reversal_journal_entry_no").forEach { field ->
            stringDiff(field, local.optNullableString(field).orEmpty(), cloud.optNullableString(field).orEmpty())?.let(::add)
        }
        listOf("voucher_date_ms", "reversed_at_ms").forEach { field ->
            stringDiff(field, local.optNullableLong(field)?.toString().orEmpty(), cloud.optNullableLong(field)?.toString().orEmpty())?.let(::add)
        }
        doubleDiff("exchange_rate", local.optDouble("exchange_rate", 1.0), cloud.optDouble("exchange_rate", 1.0))?.let(::add)
        doubleDiff("amount_original", local.optDouble("amount_original"), cloud.optDouble("amount_original"))?.let(::add)
        doubleDiff("amount_base", local.optDouble("amount_base"), cloud.optDouble("amount_base"))?.let(::add)
    }

    private fun canonicalLines(array: JSONArray): String = buildList {
        for (index in 0 until array.length()) {
            val row = array.getJSONObject(index)
            add(listOf(row.optInt("line_no"), row.optString("account_code"), row.optLong("debit_scaled"), row.optLong("credit_scaled"), row.optString("memo")).joinToString("|"))
        }
    }.sorted().joinToString(";")

    /**
     * Consume support-test tombstones before any local accounting payload is built/published.
     * This ordering is what makes deletion idempotent: a stale device removes its local copy first
     * and therefore has nothing left that could overwrite/resurrect the cloud tombstone.
     */
    private suspend fun applyDeletionTombstones(remote: RemoteData, localUser: UserEntity): DeletionCounts {
        val voucherRows = remote.vouchers.filter { it.contentObject().isSupportTestDeletionTombstone() }
        val journalRows = remote.journals.filter { it.contentObject().isSupportTestDeletionTombstone() }
        if (voucherRows.isEmpty() && journalRows.isEmpty()) return DeletionCounts()

        var vouchersDeleted = 0
        var journalsDeleted = 0
        db.withTransaction {
            val sqlite = db.openHelper.writableDatabase

            // Delete vouchers first because party_vouchers has a RESTRICT FK to journal_entries.
            for (row in voucherRows.sortedBy { it.optString("voucher_no") }) {
                val voucherNo = row.optString("voucher_no")
                val local = db.partyDao().voucherByNo(voucherNo) ?: continue
                val shipmentExpenseRefs = queryLongScalar(
                    sqlite,
                    "SELECT COUNT(*) FROM sales_shipment_expenses WHERE partyVoucherId=?",
                    local.id,
                )
                require(shipmentExpenseRefs == 0L) {
                    "CLOUD_TEST_TOMBSTONE_BLOCKED_BY_SHIPMENT_EXPENSE:$voucherNo"
                }
                sqlite.execSQL("DELETE FROM party_vouchers WHERE id=?", arrayOf(local.id))
                db.governanceDao().insertAudit(
                    AuditEventEntity(
                        userId = localUser.id,
                        action = "CLOUD_SUPPORT_TEST_DELETE_APPLIED",
                        entityType = "TREASURY_VOUCHER",
                        entityId = voucherNo,
                        oldValue = "voucherId=${local.id};journalEntryId=${local.journalEntryId}",
                        newValue = "DELETED_BY_TOMBSTONE",
                        reason = "Support test cleanup replicated from cloud",
                        deviceInfo = "ANDROID_CLOUD_SYNC_V200",
                    )
                )
                vouchersDeleted++
            }

            if (journalRows.isNotEmpty()) {
                // Posted journals are immutable during normal operation. The two delete guards are
                // relaxed only inside this cloud-tombstone transaction and are restored in finally.
                sqlite.execSQL("DROP TRIGGER IF EXISTS trg_posted_journal_line_no_delete")
                sqlite.execSQL("DROP TRIGGER IF EXISTS trg_posted_journal_no_delete")
                try {
                    for (row in journalRows.sortedBy { it.optString("entry_no") }) {
                        val entryNo = row.optString("entry_no")
                        val local = db.journalDao().byEntryNo(entryNo) ?: continue
                        val voucherRefs = queryLongScalar(
                            sqlite,
                            "SELECT COUNT(*) FROM party_vouchers WHERE journalEntryId=? OR reversalJournalEntryId=?",
                            local.id,
                            local.id,
                        )
                        require(voucherRefs == 0L) {
                            "CLOUD_TEST_TOMBSTONE_BLOCKED_BY_VOUCHER:$entryNo"
                        }
                        sqlite.execSQL("DELETE FROM journal_entries WHERE id=?", arrayOf(local.id))
                        db.governanceDao().insertAudit(
                            AuditEventEntity(
                                userId = localUser.id,
                                action = "CLOUD_SUPPORT_TEST_DELETE_APPLIED",
                                entityType = "JOURNAL",
                                entityId = entryNo,
                                oldValue = "journalId=${local.id};source=${local.sourceType}:${local.sourceId.orEmpty()}",
                                newValue = "DELETED_BY_TOMBSTONE",
                                reason = "Support test cleanup replicated from cloud",
                                deviceInfo = "ANDROID_CLOUD_SYNC_V200",
                            )
                        )
                        journalsDeleted++
                    }
                } finally {
                    AccountingPostedJournalLifecycleDatabaseGuard.install(sqlite)
                }
            }
        }
        return DeletionCounts(journals = journalsDeleted, vouchers = vouchersDeleted)
    }

    private fun RemoteData.withoutSupportTestDeletionTombstones(): RemoteData = RemoteData(
        journals = journals.filterNot { it.contentObject().isSupportTestDeletionTombstone() },
        vouchers = vouchers.filterNot { it.contentObject().isSupportTestDeletionTombstone() },
    )

    private fun JSONObject.isSupportTestDeletionTombstone(): Boolean =
        optBoolean(TOMBSTONE_DELETED_FIELD, false) &&
            optString(TOMBSTONE_SCOPE_FIELD).equals(TOMBSTONE_SCOPE_TEST_SUPPORT, ignoreCase = true) &&
            optInt(TOMBSTONE_VERSION_FIELD, 0) >= 1

    private fun queryLongScalar(
        sqlite: androidx.sqlite.db.SupportSQLiteDatabase,
        sql: String,
        vararg args: Any,
    ): Long {
        sqlite.query(sql, args).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
    }

    private suspend fun treasuryBalanceCheck(remote: RemoteData): Pair<Int, Double> {
        val asOf = System.currentTimeMillis()
        var checked = 0
        var absoluteDifference = 0.0
        for (treasury in db.accountingDao().allActiveTreasury()) {
            val account = db.accountDao().byId(treasury.accountId) ?: continue
            val cloudScaled = remote.journals.sumOf { row ->
                val content = row.contentObject()
                if (content.optLong("entry_date_ms") > asOf) return@sumOf 0L
                val lines = content.optJSONArray("lines") ?: JSONArray()
                var net = 0L
                for (index in 0 until lines.length()) {
                    val line = lines.getJSONObject(index)
                    if (line.optString("account_code").equals(account.code, true)) {
                        net = Math.addExact(net, line.optLong("debit_scaled") - line.optLong("credit_scaled"))
                    }
                }
                net
            }
            val cloudBase = AccountingPrecision.amountToDouble(cloudScaled)
            val localBase = db.accountingDao().treasuryBookBalance(treasury.accountId, asOf)
            absoluteDifference += abs(localBase - cloudBase)
            checked++
        }
        return checked to absoluteDifference
    }

    private fun fetchAll(session: CloudSession): RemoteData = RemoteData(
        journals = fetchRows("fush_tx_gl_journals", session),
        vouchers = fetchRows("fush_tx_treasury_vouchers", session),
    )

    private fun fetchRows(table: String, session: CloudSession): List<JSONObject> {
        val org = encode(session.requireOrganizationId())
        val path = "/rest/v1/$table?select=*&organization_id=eq.$org&limit=10000"
        return when (val response = request("GET", path, null, session.accessToken, null)) {
            is HttpResult.Error -> {
                if (response.code == 404) error(context.getString(R.string.cloud_accounting_schema_missing))
                error(apiErrorMessage(response))
            }
            is HttpResult.Ok -> {
                val array = runCatching { JSONArray(response.body) }.getOrElse { error(context.getString(R.string.cloud_accounting_invalid_response)) }
                List(array.length()) { array.getJSONObject(it) }
            }
        }
    }

    /**
     * Publish dependencies in two phases.  A treasury voucher has a hard cloud FK-like contract
     * to journal_entry_no.  Pairing journal batch N with voucher batch N was incorrect because the
     * voucher's journal can live in a later journal batch.  Journals are therefore committed first
     * in full; only then are voucher batches published.  Retrying either phase remains idempotent.
     */
    private fun publishBatch(journals: List<JSONObject>, vouchers: List<JSONObject>, session: CloudSession) {
        publishAccountingChunks(journals = journals, vouchers = emptyList(), session = session)
        publishAccountingChunks(journals = emptyList(), vouchers = vouchers, session = session)
    }

    private fun publishAccountingChunks(
        journals: List<JSONObject>,
        vouchers: List<JSONObject>,
        session: CloudSession,
    ) {
        val rows = if (journals.isNotEmpty()) journals.size else vouchers.size
        if (rows == 0) return
        var offset = 0
        while (offset < rows) {
            val j = JSONArray().apply { journals.drop(offset).take(BATCH_SIZE).forEach { put(it) } }
            val v = JSONArray().apply { vouchers.drop(offset).take(BATCH_SIZE).forEach { put(it) } }
            val body = JSONObject()
                .put("target_organization_id", session.requireOrganizationId())
                .put("journal_payloads", j)
                .put("voucher_payloads", v)
                .toString()
            when (val response = request("POST", "/rest/v1/rpc/fush_publish_accounting_batch", body, session.accessToken, null)) {
                is HttpResult.Ok -> Unit
                is HttpResult.Error -> {
                    if (response.code == 404) error(context.getString(R.string.cloud_accounting_schema_missing))
                    error(apiErrorMessage(response))
                }
            }
            offset += BATCH_SIZE
        }
    }

    private fun resolveCloud(entityType: String, entityKey: String, resolution: String, replacement: JSONObject?, session: CloudSession) {
        val body = JSONObject()
            .put("target_organization_id", session.requireOrganizationId())
            .put("target_entity_type", entityType)
            .put("target_entity_key", entityKey)
            .put("target_resolution", resolution)
            .put("replacement_content", replacement ?: JSONObject.NULL)
            .toString()
        when (val response = request("POST", "/rest/v1/rpc/fush_resolve_accounting_conflict", body, session.accessToken, null)) {
            is HttpResult.Ok -> Unit
            is HttpResult.Error -> error(apiErrorMessage(response))
        }
    }

    private fun request(method: String, path: String, body: String?, bearerToken: String?, prefer: String?): HttpResult {
        val url = URL(BuildConfig.SUPABASE_URL.trimEnd('/') + path)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 45_000
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

    private fun stringDiff(field: String, local: String, cloud: String): AccountingSyncConflictDifference? =
        if (local == cloud) null else AccountingSyncConflictDifference(field, local.ifBlank { "∅" }, cloud.ifBlank { "∅" })

    private fun doubleDiff(field: String, local: Double, cloud: Double): AccountingSyncConflictDifference? =
        if (nearlyEqual(local, cloud)) null else AccountingSyncConflictDifference(field, formatNumber(local), formatNumber(cloud))

    private fun nearlyEqual(left: Double, right: Double): Boolean {
        val scale = maxOf(1.0, abs(left), abs(right))
        return abs(left - right) <= 0.000001 * scale
    }

    private fun formatNumber(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun JSONObject.contentObject(): JSONObject = optJSONObject("content")
        ?: optString("content").takeIf { it.isNotBlank() }?.let(::JSONObject)
        ?: error(context.getString(R.string.cloud_accounting_invalid_response))

    private fun JSONObject.optNullableString(name: String): String? =
        if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

    private fun JSONObject.optNullableLong(name: String): Long? =
        if (!has(name) || isNull(name)) null else optLong(name)

    private sealed interface HttpResult {
        data class Ok(val code: Int, val body: String) : HttpResult
        data class Error(val code: Int, val body: String) : HttpResult
    }

    private sealed interface ApplyOutcome {
        data object Applied : ApplyOutcome
        data class Conflict(val conflict: AccountingSyncConflict) : ApplyOutcome
    }

    private data class ResolvedLine(val accountId: Long, val debitScaled: Long, val creditScaled: Long, val memo: String)
    private data class PartyIds(
        val customerId: Long? = null,
        val supplierId: Long? = null,
        val employeeId: Long? = null,
        val salesRepId: Long? = null,
        val missingReference: String? = null,
    )
    private data class RemoteData(val journals: List<JSONObject>, val vouchers: List<JSONObject>)
    private data class DeletionCounts(val journals: Int = 0, val vouchers: Int = 0)
    private data class ReferenceReconcile(
        val remote: RemoteData,
        val result: AccountingReferenceSyncResult,
    )
    private data class ReferenceApplyCounts(
        val accounts: Int = 0,
        val treasuries: Int = 0,
        val employees: Int = 0,
        val salesRepresentatives: Int = 0,
        val snapshotHash: String? = null,
    )

    private companion object {
        const val BATCH_SIZE = 100
        const val REFERENCE_SNAPSHOT_FIELD = "_fush_accounting_reference_snapshot"
        const val REFERENCE_SNAPSHOT_SCHEMA_VERSION = 1
        const val LEGACY_REFERENCE_SNAPSHOT_HASH_VERSION = 1
        const val REFERENCE_SNAPSHOT_HASH_VERSION = 2
        val WRITER_ROLES = setOf("OWNER", "ADMIN", "ACCOUNTANT", "CASHIER", "SALES", "PURCHASING", "INVENTORY", "PRODUCTION")
        val ACCOUNTING_AUTHORITY_ROLES = setOf("OWNER", "ADMIN", "ACCOUNTANT")
        val REFERENCE_SNAPSHOT_PUBLISH_ROLES = setOf("OWNER", "ADMIN")
        val ACCOUNT_SNAPSHOT_FIELDS = listOf("code", "name_ar", "name_en", "type", "parent_code", "is_posting", "is_active")
        val TREASURY_SNAPSHOT_FIELDS = listOf("code", "name_ar", "kind", "ledger_account_code", "currency_code", "bank_name", "account_number", "is_active", "created_at_ms")
        val EMPLOYEE_SNAPSHOT_FIELDS = listOf("code", "full_name_ar", "full_name_en", "phone", "job_title", "department", "hire_date_ms", "status", "notes", "created_at_ms")
        val SALES_REP_SNAPSHOT_FIELDS = listOf("code", "employee_code", "rep_type", "full_name_ar", "full_name_en", "phone", "territory", "commission_rate_pct", "free_qty_limit_pct", "status", "notes", "created_at_ms", "updated_at_ms")
        const val TOMBSTONE_DELETED_FIELD = "_deleted"
        const val TOMBSTONE_SCOPE_FIELD = "_delete_scope"
        const val TOMBSTONE_SCOPE_TEST_SUPPORT = "TEST_DATA_SUPPORT"
        const val TOMBSTONE_VERSION_FIELD = "_tombstone_version"
        const val TOMBSTONE_VERSION = 1
        const val TOMBSTONE_DELETED_AT_FIELD = "_deleted_at_ms"
        const val TOMBSTONE_REASON_HASH_FIELD = "_reason_sha256"
    }
}

internal fun canonicalReferenceSnapshotScalar(value: Any?): String {
    if (value == null || value === JSONObject.NULL) return "null"
    return when (value) {
        is Boolean -> if (value) "bool:1" else "bool:0"
        is Number -> {
            val normalized = runCatching {
                BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
            }.getOrElse { value.toString() }
            "num:$normalized"
        }
        else -> "str:${JSONObject.quote(value.toString())}"
    }
}
