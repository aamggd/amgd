package com.fush.erp.domain

import androidx.room.withTransaction
import com.fush.erp.cloud.CloudOperationResult
import com.fush.erp.cloud.CloudSyncRepository
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.AccountingPostedJournalLifecycleDatabaseGuard
import com.fush.erp.data.entity.*
import kotlin.math.abs

object SupportPermissions {
    const val VIEW = "SUPPORT_VIEW"
    const val DIAGNOSE = "SUPPORT_DIAGNOSE"
    const val REPAIR = "SUPPORT_REPAIR"
    const val RECALCULATE = "SUPPORT_RECALCULATE"
    const val CORRECT_DATA = "SUPPORT_CORRECT_DATA"
    const val TEST_DATA_DELETE = "SUPPORT_TEST_DATA_DELETE"
    val all = setOf(VIEW, DIAGNOSE, REPAIR, RECALCULATE, CORRECT_DATA, TEST_DATA_DELETE)
}

object SupportPolicy {
    const val SUPPORT_ROLE = "FUSH_SUPPORT"
    const val DEFAULT_COMPANY_ID = "LOCAL"
    val quickDurationsMinutes = listOf(60L, 360L, 1_440L)
    const val ADMIN_TEST_CLEANUP_MINUTES = 60L

    fun expiresAt(startedAt: Long, durationMinutes: Long): Long {
        require(durationMinutes in quickDurationsMinutes) { "مدة جلسة الدعم يجب أن تكون ساعة أو 6 ساعات أو 24 ساعة فقط" }
        return startedAt + durationMinutes * 60_000L
    }

    fun isActive(session: SupportSessionEntity, now: Long): Boolean {
        val boundedLifetime = session.expiresAt != Long.MAX_VALUE &&
            session.expiresAt > session.startedAt &&
            session.expiresAt - session.startedAt <= 1_440L * 60_000L
        return boundedLifetime && session.status == "ACTIVE" && session.revokedAt == null && session.closedAt == null &&
            session.startedAt <= now && session.expiresAt > now
    }

    fun isAdminTemporaryTestCleanupSession(session: SupportSessionEntity, adminUserId: Long, now: Long): Boolean {
        val oneHourOrLess = session.expiresAt > session.startedAt &&
            session.expiresAt - session.startedAt <= ADMIN_TEST_CLEANUP_MINUTES * 60_000L
        return oneHourOrLess && session.supportUserId == adminUserId && session.activatedBy == adminUserId && isActive(session, now)
    }
}

data class SupportCommandResult(
    val command: String,
    val status: String,
    val findings: List<SupportFindingRow>,
    val summary: String,
)

class SupportService(
    private val db: FushDatabase,
    private val securityService: SecurityService,
    private val cloudSyncRepository: CloudSyncRepository,
) {
    suspend fun createTicket(
        actorUserId: Long,
        companyId: String,
        branchId: String?,
        problemDescription: String,
    ): Long = db.withTransaction {
        requireCompanyAdmin(actorUserId)
        val company = companyId.trim().ifBlank { SupportPolicy.DEFAULT_COMPANY_ID }
        val problem = problemDescription.trim()
        require(problem.length >= 5) { "وصف المشكلة مطلوب" }
        val id = db.supportDao().insertTicket(
            SupportTicketEntity(
                companyId = company,
                branchId = branchId?.trim()?.takeIf { it.isNotBlank() },
                requestedBy = actorUserId,
                problemDescription = problem,
            )
        )
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = actorUserId,
                action = "SUPPORT_TICKET_CREATED",
                entityType = "SUPPORT_TICKET",
                entityId = id.toString(),
                newValue = "company=$company;branch=${branchId.orEmpty()}",
                reason = problem,
            )
        )
        id
    }

    suspend fun activateSession(
        actorUserId: Long,
        ticketId: Long,
        supportUserId: Long,
        durationMinutes: Long,
    ): Long {
        // Opening vendor maintenance access is a sensitive action and always requires a fresh
        // password re-authentication. The backend enforces this even if a caller bypasses the UI.
        securityService.requireRecentReauthentication(actorUserId, "SUPPORT_SESSION_ACTIVATE")
        return db.withTransaction {
            requireCompanyAdmin(actorUserId)
            require(durationMinutes in SupportPolicy.quickDurationsMinutes) { "اختر مدة دعم محددة: ساعة أو 6 ساعات أو 24 ساعة" }
            val ticket = requireNotNull(db.supportDao().ticketById(ticketId)) { "بلاغ الدعم غير موجود" }
            require(ticket.status != "CLOSED") { "بلاغ الدعم مغلق" }
            require(db.supportDao().activeSessionCountForTicket(ticketId, TrustedTimeService.now()) == 0) {
                "يوجد بالفعل Support Session فعالة لهذا البلاغ"
            }
            val supportUser = requireNotNull(db.userDao().byId(supportUserId)) { "مستخدم الدعم غير موجود" }
            require(supportUser.isActive && supportUser.role == SupportPolicy.SUPPORT_ROLE) { "المستخدم ليس حساب FUSH_SUPPORT نشطًا" }
            require(securityService.hasPermission(supportUser.id, SupportPermissions.VIEW)) {
                "هوية FUSH_SUPPORT غير موثقة لهذا التثبيت؛ يلزم Signed Provision/Rebind/Legacy Claim أولًا"
            }
            val now = TrustedTimeService.now()
            val expiresAt = SupportPolicy.expiresAt(now, durationMinutes)
            val id = db.supportDao().insertSession(
                SupportSessionEntity(
                    ticketId = ticket.id,
                    companyId = ticket.companyId,
                    branchId = ticket.branchId,
                    supportUserId = supportUser.id,
                    activatedBy = actorUserId,
                    startedAt = now,
                    expiresAt = expiresAt,
                )
            )
            if (ticket.status == "OPEN") db.supportDao().updateTicket(ticket.copy(status = "ACTIVE"))
            db.governanceDao().insertAudit(
                AuditEventEntity(
                    userId = actorUserId,
                    action = "SUPPORT_SESSION_ACTIVATED",
                    entityType = "SUPPORT_SESSION",
                    entityId = id.toString(),
                    newValue = "ticket=$ticketId;supportUser=$supportUserId;expiresAt=$expiresAt;durationMinutes=$durationMinutes",
                    reason = "Company-authorized FUSH maintenance access after recent re-authentication",
                )
            )
            id
        }
    }

    /**
     * v191 local emergency path for the company administrator when no FUSH vendor provisioning key
     * exists. This session is intentionally limited to one hour and is accepted only by the two
     * typed test-data deletion commands; it does not unlock diagnosis/repair/repost tools.
     */
    suspend fun activateAdminTemporaryTestCleanupSession(
        actorUserId: Long,
        ticketId: Long,
    ): Long {
        securityService.requireRecentReauthentication(actorUserId, "ADMIN_TEMP_TEST_CLEANUP_ACTIVATE")
        return db.withTransaction {
            val admin = requireCompanyAdmin(actorUserId)
            val ticket = requireNotNull(db.supportDao().ticketById(ticketId)) { "بلاغ الدعم غير موجود" }
            require(ticket.status != "CLOSED") { "بلاغ الدعم مغلق" }
            require(db.supportDao().activeSessionCountForTicket(ticketId, TrustedTimeService.now()) == 0) {
                "يوجد بالفعل Support Session فعالة لهذا البلاغ"
            }
            val now = TrustedTimeService.now()
            val expiresAt = now + SupportPolicy.ADMIN_TEST_CLEANUP_MINUTES * 60_000L
            val id = db.supportDao().insertSession(
                SupportSessionEntity(
                    ticketId = ticket.id,
                    companyId = ticket.companyId,
                    branchId = ticket.branchId,
                    supportUserId = admin.id,
                    activatedBy = admin.id,
                    startedAt = now,
                    expiresAt = expiresAt,
                )
            )
            if (ticket.status == "OPEN") db.supportDao().updateTicket(ticket.copy(status = "ACTIVE"))
            db.governanceDao().insertAudit(
                AuditEventEntity(
                    userId = admin.id,
                    action = "ADMIN_TEMP_TEST_CLEANUP_SESSION_ACTIVATED",
                    entityType = "SUPPORT_SESSION",
                    entityId = id.toString(),
                    newValue = "ticket=$ticketId;expiresAt=$expiresAt;durationMinutes=60;scope=TEST_DATA_DELETE_ONLY",
                    reason = "Administrator-authorized one-hour test-data cleanup after recent re-authentication",
                )
            )
            id
        }
    }

    suspend fun closeSession(actorUserId: Long, sessionId: Long, reason: String) = db.withTransaction {
        val session = requireNotNull(db.supportDao().sessionById(sessionId)) { "جلسة الدعم غير موجودة" }
        val actor = requireNotNull(db.userDao().byId(actorUserId)) { "المستخدم غير موجود" }
        require(actor.role == "ADMIN" || (actor.role == SupportPolicy.SUPPORT_ROLE && actor.id == session.supportUserId)) {
            "لا تملك صلاحية إنهاء جلسة الدعم"
        }
        if (!SupportPolicy.isActive(session, TrustedTimeService.now())) return@withTransaction
        val now = TrustedTimeService.now()
        if (actor.role == SupportPolicy.SUPPORT_ROLE) {
            db.supportDao().insertAudit(
                SupportAuditLogEntity(
                    supportUserId = actor.id, companyId = session.companyId, ticketId = session.ticketId, sessionId = session.id,
                    action = "SUPPORT_SESSION_CLOSE", module = "SUPPORT", recordTable = "support_sessions", recordId = session.id.toString(),
                    reason = reason.trim().ifBlank { "SESSION_CLOSED" }, validationSummary = "Access revoked on close"
                )
            )
        }
        db.supportDao().updateSession(session.copy(status = "CLOSED", closedAt = now, closeReason = reason.trim().ifBlank { "SESSION_CLOSED" }))
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = actorUserId,
                action = "SUPPORT_SESSION_CLOSED",
                entityType = "SUPPORT_SESSION",
                entityId = sessionId.toString(),
                oldValue = "ACTIVE",
                newValue = "CLOSED",
                reason = reason.trim(),
            )
        )
    }

    suspend fun revokeSession(actorUserId: Long, sessionId: Long, reason: String) = db.withTransaction {
        requireCompanyAdmin(actorUserId)
        val session = requireNotNull(db.supportDao().sessionById(sessionId)) { "جلسة الدعم غير موجودة" }
        if (!SupportPolicy.isActive(session, TrustedTimeService.now())) return@withTransaction
        val now = TrustedTimeService.now()
        db.supportDao().updateSession(session.copy(status = "REVOKED", revokedAt = now, closeReason = reason.trim().ifBlank { "MANUAL_REVOKE" }))
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = actorUserId,
                action = "SUPPORT_SESSION_REVOKED",
                entityType = "SUPPORT_SESSION",
                entityId = sessionId.toString(),
                oldValue = "ACTIVE",
                newValue = "REVOKED",
                reason = reason.trim(),
            )
        )
    }

    suspend fun closeTicket(actorUserId: Long, ticketId: Long, reason: String) = db.withTransaction {
        requireCompanyAdmin(actorUserId)
        val ticket = requireNotNull(db.supportDao().ticketById(ticketId)) { "بلاغ الدعم غير موجود" }
        val active = db.supportDao().activeSessionCountForTicket(ticketId, TrustedTimeService.now())
        require(active == 0) { "ألغِ/أغلق جلسة الدعم الفعالة قبل إغلاق البلاغ" }
        if (ticket.status == "CLOSED") return@withTransaction
        db.supportDao().updateTicket(ticket.copy(status = "CLOSED", closedAt = TrustedTimeService.now()))
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = actorUserId,
                action = "SUPPORT_TICKET_CLOSED",
                entityType = "SUPPORT_TICKET",
                entityId = ticketId.toString(),
                oldValue = ticket.status,
                newValue = "CLOSED",
                reason = reason.trim(),
            )
        )
    }

    suspend fun diagnoseInvoice(userId: Long, sessionId: Long, invoiceId: Long, reason: String): SupportCommandResult =
        runReadOnly(userId, sessionId, SupportPermissions.DIAGNOSE, "DIAGNOSE_INVOICE", "SALES/PURCHASES", "invoice", invoiceId.toString(), reason) {
            db.supportDao().diagnoseInvoice(invoiceId)
        }

    suspend fun recalculateInventory(userId: Long, sessionId: Long, warehouseId: Long, itemId: Long, reason: String): SupportCommandResult =
        runReadOnly(userId, sessionId, SupportPermissions.RECALCULATE, "RECALCULATE_INVENTORY", "INVENTORY", "stock_movements", "$warehouseId:$itemId", reason) {
            db.supportDao().recalculateInventory(warehouseId, itemId, TrustedTimeService.now())
        }

    suspend fun recalculateAverageCost(userId: Long, sessionId: Long, itemId: Long, reason: String): SupportCommandResult =
        runReadOnly(userId, sessionId, SupportPermissions.RECALCULATE, "RECALCULATE_AVERAGE_COST", "INVENTORY", "inventory_cost_layers", itemId.toString(), reason) {
            db.supportDao().recalculateAverageCost(itemId)
        }

    suspend fun checkCustomerBalance(userId: Long, sessionId: Long, customerId: Long, reason: String): SupportCommandResult =
        runReadOnly(userId, sessionId, SupportPermissions.DIAGNOSE, "CHECK_CUSTOMER_BALANCE", "SALES", "customers", customerId.toString(), reason) {
            db.supportDao().checkCustomerBalance(customerId)
        }

    suspend fun checkSupplierBalance(userId: Long, sessionId: Long, supplierId: Long, reason: String): SupportCommandResult =
        runReadOnly(userId, sessionId, SupportPermissions.DIAGNOSE, "CHECK_SUPPLIER_BALANCE", "PURCHASES", "suppliers", supplierId.toString(), reason) {
            db.supportDao().checkSupplierBalance(supplierId)
        }

    suspend fun checkTreasuryBalance(userId: Long, sessionId: Long, treasuryId: Long, reason: String): SupportCommandResult =
        runReadOnly(userId, sessionId, SupportPermissions.DIAGNOSE, "CHECK_CASH_BANK_BALANCE", "ACCOUNTING", "treasury_accounts", treasuryId.toString(), reason) {
            db.supportDao().checkTreasuryBalance(treasuryId)
        }

    suspend fun findUnbalancedJournals(userId: Long, sessionId: Long, reason: String): SupportCommandResult =
        runReadOnly(userId, sessionId, SupportPermissions.DIAGNOSE, "FIND_UNBALANCED_JOURNALS", "ACCOUNTING", "journal_entries", "*", reason) {
            db.supportDao().findUnbalancedJournals()
        }

    suspend fun findIncompleteTransactions(userId: Long, sessionId: Long, reason: String): SupportCommandResult =
        runReadOnly(userId, sessionId, SupportPermissions.DIAGNOSE, "FIND_FAILED_INCOMPLETE_TRANSACTIONS", "SYSTEM", "transactions", "*", reason) {
            db.supportDao().findIncompleteTransactions()
        }

    /**
     * Safe repair command. A balanced journal is not enough: before POST the service rebuilds the
     * exact Expected Journal from the immutable business source and compares account + debit +
     * credit + header semantics against the STAGING journal. Any ambiguity or mismatch fails closed.
     *
     * The snapshot is committed before the business mutation. If the mutation/validation later
     * rolls back, a FAILED support audit/validation row is written in a separate transaction so
     * the attempted repair remains permanently auditable.
     */
    suspend fun rebuildAccountingEntry(
        userId: Long,
        sessionId: Long,
        entryId: Long,
        reason: String,
        deviceInfo: String = "ANDROID",
        ipAddress: String = "",
        command: String = "REBUILD_ACCOUNTING_ENTRY",
    ): SupportCommandResult {
        val ctx = requireActiveContext(userId, sessionId, SupportPermissions.REPAIR)
        val cleanReason = reason.trim()
        require(cleanReason.length >= 5) { "سبب الإصلاح مطلوب" }
        val entry = requireNotNull(db.journalDao().byId(entryId)) { "القيد غير موجود" }
        require(entry.sourceType != "MANUAL") { "القيد اليدوي يجب تصحيحه بقيد عكسي/تصحيحي، وليس Support Rebuild" }
        val supportedSources = setOf("SALE", "PURCHASE", "CUSTOMER_RECEIPT", "SUPPLIER_PAYMENT", "SALES_RETURN", "PURCHASE_RETURN")
        require(entry.sourceType in supportedSources) {
            "لا يوجد Repair Command typed مع Expected Journal للمصدر ${entry.sourceType}. أضف Command متخصص بدل تعديل القيد مباشرة."
        }
        require(entry.sourceId?.toLongOrNull() != null) { "مرجع العملية الأصلية غير صالح؛ يلزم Repair Command متخصص" }
        require(entry.status == "STAGING") { "Support Rebuild مسموح فقط لقيد STAGING غير مكتمل" }
        val lines = db.journalDao().linesForEntry(entryId)
        require(lines.isNotEmpty()) { "القيد STAGING بلا سطور؛ يلزم Repair Command خاص بمصدر العملية" }
        val before = journalSnapshot(entry, lines)

        // Durable evidence is intentionally outside the business-mutation transaction.
        val snapshotId = db.withTransaction {
            requireActiveContext(userId, sessionId, SupportPermissions.REPAIR)
            db.supportDao().insertSnapshot(
                SupportSnapshotEntity(
                    supportUserId = userId,
                    companyId = ctx.session.companyId,
                    ticketId = ctx.ticket.id,
                    sessionId = sessionId,
                    module = "ACCOUNTING",
                    recordTable = "journal_entries",
                    recordId = entryId.toString(),
                    beforeValue = before,
                    reason = cleanReason,
                )
            )
        }

        var failureRecorded = false
        try {
            val preflight = JournalSemanticExpectation(db).compare(entry, lines)
            if (!preflight.matches) {
                val failure = "SEMANTIC_SOURCE_MISMATCH:${preflight.summary}"
                recordFailedRepairAttempt(ctx, userId, sessionId, entryId, snapshotId, command, before, cleanReason, failure, ipAddress, deviceInfo)
                failureRecorded = true
                throw IllegalStateException("رفض Safe Repair: القيد STAGING لا يطابق القيد المتوقع من المستند الأصلي. ${preflight.summary}")
            }

            return db.withTransaction {
                // Re-check all authorization/time gates and the exact preflight snapshot to prevent
                // TOCTOU changes between inspection and POST.
                requireActiveContext(userId, sessionId, SupportPermissions.REPAIR)
                val currentEntry = requireNotNull(db.journalDao().byId(entryId)) { "القيد اختفى قبل الإصلاح" }
                val currentLines = db.journalDao().linesForEntry(entryId)
                require(currentEntry.status == "STAGING") { "حالة القيد تغيرت قبل التنفيذ" }
                require(journalSnapshot(currentEntry, currentLines) == before) { "تم تغيير القيد بعد Snapshot؛ أعد التشخيص قبل الإصلاح" }
                val semanticNow = JournalSemanticExpectation(db).compare(currentEntry, currentLines)
                require(semanticNow.matches) { "Expected Journal تغير/لم يعد مطابقًا قبل التنفيذ: ${semanticNow.summary}" }

                val changed = db.journalDao().transitionStagingToPosted(entryId)
                require(changed == 1) { "لم يتم تغيير القيد؛ قد تكون حالته تغيرت بالتزامن" }
                val afterEntry = requireNotNull(db.journalDao().byId(entryId))
                val afterLines = db.journalDao().linesForEntry(entryId)
                val after = journalSnapshot(afterEntry, afterLines)
                val validation = validateJournalRepair(entryId)
                db.supportDao().insertValidation(
                    SupportValidationResultEntity(
                        supportUserId = userId,
                        companyId = ctx.session.companyId,
                        ticketId = ctx.ticket.id,
                        sessionId = sessionId,
                        command = command,
                        status = if (validation.first) "PASS" else "FAIL",
                        summary = validation.second,
                    )
                )
                require(validation.first) { "فشل Validation بعد الإصلاح: ${validation.second}" }
                db.supportDao().insertAudit(
                    SupportAuditLogEntity(
                        supportUserId = userId,
                        companyId = ctx.session.companyId,
                        ticketId = ctx.ticket.id,
                        sessionId = sessionId,
                        snapshotId = snapshotId,
                        action = command,
                        module = "ACCOUNTING",
                        recordTable = "journal_entries",
                        recordId = entryId.toString(),
                        beforeValue = before,
                        afterValue = after,
                        reason = cleanReason,
                        validationSummary = validation.second,
                        ipAddress = ipAddress,
                        deviceInfo = deviceInfo,
                    )
                )
                SupportCommandResult(
                    command = command,
                    status = "PASS",
                    findings = listOf(SupportFindingRow("INFO", "ACCOUNTING", "JOURNAL_ENTRY", entryId.toString(), afterEntry.entryNo, validation.second)),
                    summary = validation.second,
                )
            }
        } catch (t: Throwable) {
            if (!failureRecorded) {
                runCatching {
                    recordFailedRepairAttempt(
                        ctx, userId, sessionId, entryId, snapshotId, command, before, cleanReason,
                        "BUSINESS_MUTATION_ROLLED_BACK:${t.message ?: t::class.java.simpleName}", ipAddress, deviceInfo
                    )
                }
            }
            throw t
        }
    }

    private suspend fun recordFailedRepairAttempt(
        ctx: SupportContext,
        userId: Long,
        sessionId: Long,
        entryId: Long,
        snapshotId: Long,
        command: String,
        before: String,
        reason: String,
        failureSummary: String,
        ipAddress: String,
        deviceInfo: String,
    ) {
        db.withTransaction {
            db.supportDao().insertValidation(
                SupportValidationResultEntity(
                    supportUserId = userId, companyId = ctx.session.companyId, ticketId = ctx.ticket.id, sessionId = sessionId,
                    command = command, status = "FAIL", summary = failureSummary,
                )
            )
            db.supportDao().insertAudit(
                SupportAuditLogEntity(
                    supportUserId = userId, companyId = ctx.session.companyId, ticketId = ctx.ticket.id, sessionId = sessionId,
                    snapshotId = snapshotId, action = "${command}_FAILED", module = "ACCOUNTING",
                    recordTable = "journal_entries", recordId = entryId.toString(), beforeValue = before, afterValue = "",
                    reason = reason, validationSummary = failureSummary, ipAddress = ipAddress, deviceInfo = deviceInfo,
                )
            )
        }
    }

    suspend fun repostTransaction(userId: Long, sessionId: Long, entryId: Long, reason: String): SupportCommandResult =
        rebuildAccountingEntry(userId, sessionId, entryId, reason, command = "REPOST_TRANSACTION")


    /**
     * v189 temporary, tightly-scoped maintenance command for removing a deliberately-created test
     * sales document. This does not change normal sales/return logic and is unreachable outside an
     * active company-authorized FUSH_SUPPORT session. The immutable audit trail is retained.
     */
    suspend fun deleteTestSalesInvoiceBundle(
        userId: Long,
        sessionId: Long,
        invoiceId: Long,
        reason: String,
    ): SupportCommandResult {
        val ctx = requireTestCleanupContext(userId, sessionId)
        val cleanReason = requireTestDeletionReason(reason)
        val invoice = requireNotNull(db.salesDao().invoiceById(invoiceId)) { "فاتورة البيع غير موجودة" }
        val sqlite = db.openHelper.writableDatabase

        // v194: maintenance cleanup must inspect the physical FK graph, not business-list queries.
        // Business queries intentionally filter statuses (for example POSTED returns), while SQLite
        // FK constraints apply to every row. Using direct IDs here prevents an unlisted DRAFT/
        // REVERSED/cloud-hydrated child from surviving and blocking the final invoice DELETE.
        val returnIds = queryLongList(sqlite, "SELECT id FROM sales_returns WHERE salesInvoiceId=? ORDER BY id", invoiceId)
        val receiptIds = queryLongList(
            sqlite,
            "SELECT DISTINCT receiptId FROM customer_receipt_allocations WHERE invoiceId=? ORDER BY receiptId",
            invoiceId,
        )
        val commissionIds = queryLongList(sqlite, "SELECT id FROM sales_commissions WHERE invoiceId=? ORDER BY id", invoiceId)

        // Never delete a receipt that also settles another invoice. This keeps the temporary tool
        // fail-closed if an operator points it at a real document.
        receiptIds.forEach { receiptId ->
            val foreignAllocationCount = queryLongScalar(
                sqlite,
                "SELECT COUNT(*) FROM customer_receipt_allocations WHERE receiptId=? AND invoiceId<>?",
                receiptId,
                invoiceId,
            )
            require(foreignAllocationCount == 0L) {
                "رفض الحذف: يوجد سند تحصيل مرتبط أيضاً بفواتير أخرى"
            }
        }

        val returnNos = returnIds.mapNotNull { id -> db.salesDao().returnById(id)?.returnNo }
        val receiptNos = receiptIds.mapNotNull { id -> db.salesDao().receiptById(id)?.receiptNo }
        val before = "invoice=${invoice.invoiceNo};returns=${returnNos.joinToString()};receipts=${receiptNos.joinToString()};commissions=${commissionIds.size}"

        db.withTransaction {
            requireTestCleanupContext(userId, sessionId)
            withTemporaryPostedJournalDeletionOverride {
                // Delete the most dependent rows first. Explicit deletes are intentional even where
                // a historical schema has CASCADE, so the maintenance command is deterministic.
                returnIds.forEach { returnId ->
                    sqlite.execSQL(
                        "DELETE FROM sales_return_allocations WHERE returnLineId IN (SELECT id FROM sales_return_lines WHERE returnId=?)",
                        arrayOf(returnId),
                    )
                    sqlite.execSQL(
                        "DELETE FROM inventory_cost_layers WHERE stockMovementId IN (SELECT id FROM stock_movements WHERE referenceType='SALES_RETURN' AND referenceId=?)",
                        arrayOf(returnId),
                    )
                    sqlite.execSQL("DELETE FROM stock_movements WHERE referenceType='SALES_RETURN' AND referenceId=?", arrayOf(returnId))
                    sqlite.execSQL("DELETE FROM sales_return_lines WHERE returnId=?", arrayOf(returnId))
                    sqlite.execSQL("DELETE FROM sales_returns WHERE id=?", arrayOf(returnId))
                }

                // Commission rows reference receipt allocations with RESTRICT, so commissions must
                // disappear before any allocation/receipt cleanup.
                sqlite.execSQL("DELETE FROM sales_commissions WHERE invoiceId=?", arrayOf(invoiceId))
                receiptIds.forEach { receiptId ->
                    sqlite.execSQL("DELETE FROM customer_receipt_allocations WHERE receiptId=? AND invoiceId=?", arrayOf(receiptId, invoiceId))
                    sqlite.execSQL("DELETE FROM customer_receipts WHERE id=?", arrayOf(receiptId))
                }

                // All direct RESTRICT children of sales_invoices known in schema 49.
                sqlite.execSQL("DELETE FROM sales_shipment_expense_invoice_allocations WHERE invoiceId=?", arrayOf(invoiceId))
                sqlite.execSQL("DELETE FROM sales_shipment_invoice_item_allocations WHERE invoiceId=?", arrayOf(invoiceId))
                sqlite.execSQL("DELETE FROM sales_additional_charge_settlements WHERE invoiceId=?", arrayOf(invoiceId))
                sqlite.execSQL("DELETE FROM invoice_geographic_costs WHERE invoiceId=?", arrayOf(invoiceId))

                val lineIds = queryLongList(sqlite, "SELECT id FROM sales_lines WHERE invoiceId=? ORDER BY id", invoiceId)
                lineIds.forEach { lineId ->
                    sqlite.execSQL(
                        "DELETE FROM inventory_cost_layers WHERE stockMovementId IN (SELECT id FROM stock_movements WHERE referenceType='SALES_LINE' AND referenceId=?)",
                        arrayOf(lineId),
                    )
                    sqlite.execSQL("DELETE FROM stock_movements WHERE referenceType='SALES_LINE' AND referenceId=?", arrayOf(lineId))
                }

                // Before deleting the invoice, inspect the live SQLite FK graph. Any future NO
                // ACTION/RESTRICT child added by a later schema will now produce a useful table name
                // instead of the generic FOREIGN KEY constraint failed message.
                val blockers = findRestrictingForeignKeyDependents(sqlite, "sales_invoices", invoiceId)
                require(blockers.isEmpty()) {
                    "رفض الحذف: ما زالت الفاتورة مرتبطة بسجلات في: ${blockers.joinToString()}"
                }

                // sales_lines / sales_allocations are CASCADE children of the invoice.
                sqlite.execSQL("DELETE FROM sales_invoices WHERE id=?", arrayOf(invoiceId))

                // Journals are logical effects rather than FK children, and are removed after the
                // source rows. If a journal is unexpectedly referenced by another protected module,
                // SQLite rolls the whole transaction back instead of leaving a half-cleaned invoice.
                commissionIds.forEach { commissionId ->
                    sqlite.execSQL("DELETE FROM journal_entries WHERE sourceType='SALES_COMMISSION' AND sourceId=?", arrayOf("commission:$commissionId"))
                }
                returnIds.forEach { returnId ->
                    sqlite.execSQL("DELETE FROM journal_entries WHERE sourceType='COMMISSION_REVERSAL' AND sourceId=?", arrayOf("sales-return:$returnId"))
                    sqlite.execSQL("DELETE FROM journal_entries WHERE sourceType='SALES_RETURN' AND sourceId=?", arrayOf(returnId.toString()))
                }
                receiptIds.forEach { receiptId ->
                    sqlite.execSQL("DELETE FROM journal_entries WHERE sourceType='RECEIPT_COMMISSION_REVERSAL' AND sourceId LIKE ?", arrayOf("receipt-reversal:$receiptId:%"))
                    sqlite.execSQL("DELETE FROM journal_entries WHERE sourceType='CUSTOMER_RECEIPT' AND sourceId=?", arrayOf(receiptId.toString()))
                }
                sqlite.execSQL("DELETE FROM journal_entries WHERE sourceType='SALE' AND sourceId=?", arrayOf(invoiceId.toString()))

                require(db.salesDao().invoiceById(invoiceId) == null) { "فشل التحقق بعد حذف فاتورة الاختبار" }
                db.supportDao().insertAudit(
                    SupportAuditLogEntity(
                        supportUserId = userId, companyId = ctx.session.companyId, ticketId = ctx.ticket.id, sessionId = sessionId,
                        action = "DELETE_TEST_SALES_INVOICE_BUNDLE", module = "SALES", recordTable = "sales_invoices",
                        recordId = invoiceId.toString(), beforeValue = before, afterValue = "DELETED", reason = cleanReason,
                        validationSummary = "PASS: invoice and all FK-dependent return/receipt/commission/shipment/additional-charge test effects removed"
                    )
                )
            }
        }
        return SupportCommandResult(
            command = "DELETE_TEST_SALES_INVOICE_BUNDLE", status = "PASS", findings = emptyList(),
            summary = "تم حذف فاتورة الاختبار ${invoice.invoiceNo} وكل آثارها المرتبطة بترتيب آمن للمفاتيح الأجنبية."
        )
    }

    private fun queryLongList(
        sqlite: androidx.sqlite.db.SupportSQLiteDatabase,
        sql: String,
        vararg args: Any,
    ): List<Long> = sqlite.query(sql, args).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.getLong(0))
        }
    }

    private fun queryLongScalar(
        sqlite: androidx.sqlite.db.SupportSQLiteDatabase,
        sql: String,
        vararg args: Any,
    ): Long = sqlite.query(sql, args).use { cursor ->
        if (cursor.moveToFirst()) cursor.getLong(0) else 0L
    }

    /** Returns live NO ACTION/RESTRICT child rows that would block deleting parentId. */
    private fun findRestrictingForeignKeyDependents(
        sqlite: androidx.sqlite.db.SupportSQLiteDatabase,
        parentTable: String,
        parentId: Long,
    ): List<String> {
        val tables = sqlite.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        val blockers = linkedSetOf<String>()
        tables.forEach { table ->
            val safeTable = table.replace("\"", "\"\"")
            sqlite.query("PRAGMA foreign_key_list(\"$safeTable\")").use { fk ->
                while (fk.moveToNext()) {
                    val referencedTable = fk.getString(2)
                    val childColumn = fk.getString(3)
                    val onDelete = fk.getString(6).uppercase()
                    if (referencedTable != parentTable || onDelete !in setOf("NO ACTION", "RESTRICT")) continue
                    val safeColumn = childColumn.replace("\"", "\"\"")
                    val count = queryLongScalar(
                        sqlite,
                        "SELECT COUNT(*) FROM \"$safeTable\" WHERE \"$safeColumn\"=?",
                        parentId,
                    )
                    if (count > 0L) blockers += "$table.$childColumn ($count)"
                }
            }
        }
        return blockers.toList()
    }

    /** Temporary typed cleanup for a deliberately-created test shipment expense. */
    suspend fun deleteTestShipmentExpense(
        userId: Long,
        sessionId: Long,
        expenseId: Long,
        reason: String,
    ): SupportCommandResult {
        val ctx = requireTestCleanupContext(userId, sessionId)
        val cleanReason = requireTestDeletionReason(reason)
        val expense = requireNotNull(db.shipmentDao().expenseById(expenseId)) { "مصروف الشحنة غير موجود" }
        val allocations = db.shipmentDao().expenseAllocationsForExpense(expenseId)
        require(allocations.none { it.status == "ACTIVE" }) {
            "رفض الحذف: مصروف الشحنة ما زال مخصصاً لفاتورة. احذف فاتورة الاختبار أولاً ثم أعد المحاولة."
        }
        val voucher = requireNotNull(db.partyDao().voucherByNo(expense.paymentVoucherNo)) { "سند مصروف الشحنة غير موجود" }
        require(voucher.id == expense.partyVoucherId) { "مرجع سند مصروف الشحنة غير متطابق" }
        val before = "expenseId=${expense.id};shipmentId=${expense.shipmentId};voucher=${voucher.voucherNo};amountBase=${expense.amountBase}"
        val sqlite = db.openHelper.writableDatabase

        // v200: a synchronized test document must be retired in the cloud before it disappears
        // locally.  The cloud rows are replaced with explicit support-test tombstones, so another
        // phone cannot rehydrate the expense/voucher/journal on the next sync.  Local-only
        // installations still keep the original maintenance behaviour.
        val cloudTombstoned = when (
            val cloud = cloudSyncRepository.publishTestShipmentExpenseDeletionTombstones(
                localUserId = userId,
                expenseId = expenseId,
                reason = cleanReason,
            )
        ) {
            is CloudOperationResult.Success -> cloud.value
            is CloudOperationResult.Failure -> error("تعذر تثبيت حذف بيانات الاختبار في السحابة: ${cloud.message}")
        }

        db.withTransaction {
            requireTestCleanupContext(userId, sessionId)
            withTemporaryPostedJournalDeletionOverride {
            sqlite.execSQL("DELETE FROM sales_shipment_expense_invoice_allocations WHERE shipmentExpenseId=?", arrayOf(expenseId))
            sqlite.execSQL("DELETE FROM sales_shipment_expenses WHERE id=?", arrayOf(expenseId))
            // expense_dimensions and attachments cascade from party_vouchers.
            sqlite.execSQL("DELETE FROM party_vouchers WHERE id=?", arrayOf(voucher.id))
            sqlite.execSQL("DELETE FROM journal_entries WHERE id=?", arrayOf(voucher.journalEntryId))
            require(db.shipmentDao().expenseById(expenseId) == null) { "فشل التحقق بعد حذف مصروف الشحنة الاختباري" }
            db.supportDao().insertAudit(
                SupportAuditLogEntity(
                    supportUserId = userId, companyId = ctx.session.companyId, ticketId = ctx.ticket.id, sessionId = sessionId,
                    action = "DELETE_TEST_SHIPMENT_EXPENSE", module = "SALES", recordTable = "sales_shipment_expenses",
                    recordId = expenseId.toString(), beforeValue = before, afterValue = "DELETED", reason = cleanReason,
                    validationSummary = "PASS: shipment expense, voucher, expense dimension and journal removed;cloudTombstone=$cloudTombstoned"
                )
            )
            }
        }
        return SupportCommandResult(
            command = "DELETE_TEST_SHIPMENT_EXPENSE", status = "PASS", findings = emptyList(),
            summary = if (cloudTombstoned) {
                "تم حذف مصروف الشحنة الاختباري وسند الصرف والقيد المرتبط به، وتثبيت حذفها في السحابة حتى لا تعود على الأجهزة الأخرى."
            } else {
                "تم حذف مصروف الشحنة الاختباري وسند الصرف والقيد المرتبط به محلياً."
            }
        )
    }

    private suspend fun <T> withTemporaryPostedJournalDeletionOverride(block: suspend () -> T): T {
        val sqlite = db.openHelper.writableDatabase
        // Only the two DELETE guards are relaxed, and only inside the surrounding Room transaction.
        // All insert/update lifecycle protections stay active. SQLite DDL is transactional, so any
        // failure rolls the trigger state back; finally also reinstalls the complete guard set.
        sqlite.execSQL("DROP TRIGGER IF EXISTS trg_posted_journal_line_no_delete")
        sqlite.execSQL("DROP TRIGGER IF EXISTS trg_posted_journal_no_delete")
        // v194: only test-cleanup transactions may remove the immutable cost layer that belongs
        // to the stock movement being removed. Normal inventory operations remain protected.
        sqlite.execSQL("DROP TRIGGER IF EXISTS trg_inventory_cost_layer_no_delete")
        return try {
            block()
        } finally {
            AccountingPostedJournalLifecycleDatabaseGuard.install(sqlite)
            com.fush.erp.data.installInventoryCostLayerSupport(sqlite)
        }
    }

    private fun requireTestDeletionReason(reason: String): String {
        val clean = reason.trim()
        require(clean.length >= 8) { "اكتب سبباً واضحاً للحذف" }
        val marker = clean.lowercase()
        require(marker.contains("اختبار") || marker.contains("وهم") || marker.contains("test") || marker.contains("demo")) {
            "للحماية يجب أن يذكر السبب صراحة أن العملية اختبار/وهمية"
        }
        return clean
    }

    /** No generic table editor is deliberately exposed. */
    suspend fun correctDataExceptionally(userId: Long, sessionId: Long, reason: String): Nothing {
        requireActiveContext(userId, sessionId, SupportPermissions.CORRECT_DATA)
        require(reason.trim().length >= 10) { "سبب الاستثناء يجب أن يكون واضحًا" }
        throw UnsupportedOperationException("لا يوجد تعديل Database عام. يجب إنشاء Repair Command typed ومراجع لهذا النوع من البيانات.")
    }

    private data class SupportContext(val session: SupportSessionEntity, val ticket: SupportTicketEntity)

    private suspend fun requireTestCleanupContext(userId: Long, sessionId: Long): SupportContext {
        val user = requireNotNull(db.userDao().byId(userId)) { "مستخدم الصيانة غير موجود" }
        require(user.isActive) { "المستخدم غير نشط" }
        val session = requireNotNull(db.supportDao().sessionById(sessionId)) { "Support Session غير موجودة" }
        val ticket = requireNotNull(db.supportDao().ticketById(session.ticketId)) { "Support Ticket غير موجود" }
        require(session.supportUserId == userId) { "الجلسة ليست مخصصة لهذا المستخدم" }
        require(SupportPolicy.isActive(session, TrustedTimeService.now())) { "Support Session منتهية أو ملغاة" }
        require(ticket.status != "CLOSED") { "Support Ticket مغلق" }
        require(ticket.companyId == session.companyId && ticket.branchId == session.branchId) { "نطاق الشركة/الفرع غير متطابق" }

        when (user.role) {
            SupportPolicy.SUPPORT_ROLE -> {
                require(db.securityDao().hasPermission(user.role, SupportPermissions.TEST_DATA_DELETE) > 0) {
                    "لا يملك حساب الدعم صلاحية ${SupportPermissions.TEST_DATA_DELETE}"
                }
            }
            "ADMIN" -> {
                require(SupportPolicy.isAdminTemporaryTestCleanupSession(session, user.id, TrustedTimeService.now())) {
                    "جلسة تنظيف ADMIN غير صالحة أو تجاوزت ساعة واحدة"
                }
            }
            else -> error("حذف بيانات الاختبار متاح فقط لـ FUSH_SUPPORT أو ADMIN داخل جلسة تنظيف مؤقتة")
        }
        return SupportContext(session, ticket)
    }

    private suspend fun requireActiveContext(userId: Long, sessionId: Long, requiredPermission: String): SupportContext {
        val user = requireNotNull(db.userDao().byId(userId)) { "مستخدم الدعم غير موجود" }
        require(user.isActive && user.role == SupportPolicy.SUPPORT_ROLE) { "هذه الأدوات متاحة فقط لحساب FUSH_SUPPORT" }
        require(requiredPermission in SupportPermissions.all) { "Support permission غير معروفة" }
        require(db.securityDao().hasPermission(user.role, requiredPermission) > 0) { "لا يملك حساب الدعم الصلاحية $requiredPermission" }
        val session = requireNotNull(db.supportDao().sessionById(sessionId)) { "Support Session غير موجودة" }
        val ticket = requireNotNull(db.supportDao().ticketById(session.ticketId)) { "Support Ticket غير موجود" }
        require(session.supportUserId == userId) { "الجلسة ليست مخصصة لهذا المستخدم" }
        require(SupportPolicy.isActive(session, TrustedTimeService.now())) { "Support Session منتهية أو ملغاة" }
        require(ticket.status != "CLOSED") { "Support Ticket مغلق" }
        require(ticket.companyId == session.companyId && ticket.branchId == session.branchId) { "نطاق الشركة/الفرع غير متطابق" }
        return SupportContext(session, ticket)
    }

    private suspend fun requireCompanyAdmin(userId: Long): UserEntity {
        val user = requireNotNull(db.userDao().byId(userId)) { "المستخدم غير موجود" }
        require(user.isActive && user.role == "ADMIN") { "تفعيل صلاحية الصيانة وإلغاؤها يتطلب مدير الشركة" }
        return user
    }

    private suspend fun runReadOnly(
        userId: Long,
        sessionId: Long,
        permission: String,
        command: String,
        module: String,
        table: String,
        recordId: String,
        reason: String,
        block: suspend () -> List<SupportFindingRow>,
    ): SupportCommandResult = db.withTransaction {
        val ctx = requireActiveContext(userId, sessionId, permission)
        require(reason.trim().length >= 3) { "سبب الفحص مطلوب" }
        val findings = block()
        val summary = if (findings.isEmpty()) "لم يتم العثور على نتائج/مشكلات مطابقة" else "تمت إعادة ${findings.size} نتيجة"
        db.supportDao().insertAudit(
            SupportAuditLogEntity(
                supportUserId = userId,
                companyId = ctx.session.companyId,
                ticketId = ctx.ticket.id,
                sessionId = sessionId,
                action = command,
                module = module,
                recordTable = table,
                recordId = recordId,
                reason = reason.trim(),
                validationSummary = summary,
            )
        )
        SupportCommandResult(command, "PASS", findings, summary)
    }

    private fun journalSnapshot(entry: JournalEntryEntity, lines: List<JournalLineEntity>): String = buildString {
        append("id=${entry.id};entryNo=${entry.entryNo};status=${entry.status};source=${entry.sourceType}:${entry.sourceId};")
        append("lines=")
        append(lines.joinToString("|") { "${it.id}:${it.accountId}:${it.debit}:${it.credit}" })
    }

    private suspend fun validateJournalRepair(entryId: Long): Pair<Boolean, String> {
        val entry = db.journalDao().byId(entryId) ?: return false to "القيد اختفى بعد الإصلاح"
        val lines = db.journalDao().linesForEntry(entryId)
        val debit = lines.sumOf { it.debit }
        val credit = lines.sumOf { it.credit }
        val balanced = lines.isNotEmpty() && abs(debit - credit) <= 0.0001
        val semantic = runCatching { JournalSemanticExpectation(db).compare(entry, lines) }
            .getOrElse { return false to "semanticExpectedJournal=false;error=${it.message}" }
        val sourceId = entry.sourceId?.toLongOrNull()
            ?: return false to "source=${entry.sourceType};sourceId=${entry.sourceId};referenceValid=false"
        val checks = mutableListOf(
            "status=${entry.status}",
            "debit=${"%.2f".format(debit)}",
            "credit=${"%.2f".format(credit)}",
            "balanced=$balanced",
            "semanticExpectedJournal=${semantic.matches}",
            "semanticSummary=${semantic.summary}",
        )
        var sourceOk = true
        when (entry.sourceType) {
            "SALE" -> {
                val exists = db.supportDao().postedSalesInvoiceCount(sourceId) == 1
                val missingStock = db.supportDao().salesLinesMissingStockMovement(sourceId)
                val paymentType = db.supportDao().salesInvoicePaymentType(sourceId)
                val treasuryOk = paymentType != "CASH" || db.supportDao().treasuryDebitLineCount(entryId) > 0
                sourceOk = exists && missingStock == 0 && paymentType != null && treasuryOk
                checks += "salesPosted=$exists"
                checks += "salesMissingStockLines=$missingStock"
                checks += "salesPaymentType=${paymentType ?: "MISSING"}"
                checks += "cashTreasury=$treasuryOk"
            }
            "PURCHASE" -> {
                val exists = db.supportDao().postedPurchaseInvoiceCount(sourceId) == 1
                val missingStock = db.supportDao().purchaseLinesMissingStockMovement(sourceId)
                val paymentType = db.supportDao().purchaseInvoicePaymentType(sourceId)
                val treasuryOk = paymentType != "CASH" || db.supportDao().treasuryCreditLineCount(entryId) > 0
                sourceOk = exists && missingStock == 0 && paymentType != null && treasuryOk
                checks += "purchasePosted=$exists"
                checks += "purchaseMissingStockLines=$missingStock"
                checks += "purchasePaymentType=${paymentType ?: "MISSING"}"
                checks += "cashTreasury=$treasuryOk"
            }
            "CUSTOMER_RECEIPT" -> {
                val exists = db.supportDao().customerReceiptCount(sourceId) == 1
                val treasuryOk = db.supportDao().treasuryDebitLineCount(entryId) > 0
                sourceOk = exists && treasuryOk
                checks += "receiptExists=$exists"
                checks += "cashBankDebit=$treasuryOk"
            }
            "SUPPLIER_PAYMENT" -> {
                val exists = db.supportDao().supplierPaymentCount(sourceId) == 1
                val treasuryOk = db.supportDao().treasuryCreditLineCount(entryId) > 0
                sourceOk = exists && treasuryOk
                checks += "supplierPaymentExists=$exists"
                checks += "cashBankCredit=$treasuryOk"
            }
            "SALES_RETURN" -> {
                val settlement = db.supportDao().postedSalesReturnSettlementType(sourceId)
                val missingStock = db.supportDao().salesReturnLinesMissingStockMovement(sourceId)
                val treasuryOk = settlement != "CASH_REFUND" || db.supportDao().treasuryCreditLineCount(entryId) > 0
                sourceOk = settlement != null && missingStock == 0 && treasuryOk
                checks += "salesReturnSettlement=${settlement ?: "MISSING"}"
                checks += "salesReturnMissingStockItems=$missingStock"
                checks += "cashRefundTreasury=$treasuryOk"
            }
            "PURCHASE_RETURN" -> {
                val settlement = db.supportDao().postedPurchaseReturnSettlementType(sourceId)
                val missingStock = db.supportDao().purchaseReturnLinesMissingStockMovement(sourceId)
                val treasuryOk = settlement != "CASH_REFUND" || db.supportDao().treasuryDebitLineCount(entryId) > 0
                sourceOk = settlement != null && missingStock == 0 && treasuryOk
                checks += "purchaseReturnSettlement=${settlement ?: "MISSING"}"
                checks += "purchaseReturnMissingStockItems=$missingStock"
                checks += "cashRefundTreasury=$treasuryOk"
            }
            else -> sourceOk = false
        }
        val ok = entry.status == "POSTED" && balanced && sourceOk && semantic.matches
        checks += "crossModule=$sourceOk"
        return ok to checks.joinToString(";")
    }
}
