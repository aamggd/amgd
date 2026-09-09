package com.fush.erp.domain

import androidx.room.withTransaction
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.AuditEventEntity
import com.fush.erp.data.entity.RoleEntity
import com.fush.erp.data.entity.RolePermissionEntity
import com.fush.erp.data.entity.UserEntity
import com.fush.erp.data.entity.UserPasswordHistoryEntity

sealed class AuthenticationResult {
    data class Success(val user: UserEntity) : AuthenticationResult()
    data class Failure(val message: String) : AuthenticationResult()
    data class Locked(val until: Long) : AuthenticationResult()
}

sealed class ReauthenticationResult {
    data object Success : ReauthenticationResult()
    data class Failure(val message: String) : ReauthenticationResult()
    data class Locked(val until: Long) : ReauthenticationResult()
}

class RecentAuthenticationRequiredException(message: String) : SecurityException(message)

internal fun rolePermissionChangeRequiresSessionInvalidation(
    previousPermissions: Set<String>,
    nextPermissions: Set<String>,
): Boolean = !nextPermissions.containsAll(previousPermissions)

private data class ReauthenticationStamp(val sessionVersion: Long, val verifiedAt: Long)

class SecurityService(
    private val db: FushDatabase,
    private val vendorSupportProvisioning: VendorSupportProvisioningManager? = null,
) {
    private val recentReauthentication = mutableMapOf<Long, ReauthenticationStamp>()

    suspend fun seedDefaults() {
        val dao = db.securityDao()
        dao.upsertPermissions(PermissionCatalog.permissions)

        // Seed the full default role catalog only for a brand-new database.
        // After initial setup, only protected system roles (currently ADMIN) are re-ensured.
        // This keeps an administrator's deliberate deletion of non-system roles permanent.
        if (dao.roleCount() == 0) {
            dao.insertRolesIgnore(PermissionCatalog.roles)
        } else {
            dao.insertRolesIgnore(PermissionCatalog.roles.filter { it.isSystem })
        }

        PermissionCatalog.defaultRolePermissions.forEach { (roleCode, codes) ->
            val role = dao.roleByCode(roleCode) ?: return@forEach
            if (role.code == "ADMIN" || dao.rolePermissionCount(roleCode) == 0) {
                dao.insertRolePermissionsIgnore(codes.map { RolePermissionEntity(roleCode, it) })
            }
        }

        // One-time compatibility upgrade for databases created before the accounting hardening permissions existed.
        // Only roles that already had the broad write permission inherit the corresponding specialized rights.
        // Read-only roles (AUDITOR/VIEWER or custom read-only roles) receive nothing automatically.
        // The marker prevents later app starts from re-granting permissions an administrator intentionally revoked.
        val hardeningMarker = "ACCOUNTING_AUTH_V108"
        if (db.governanceDao().auditMarkerCount("SECURITY_PERMISSION_UPGRADE", "SYSTEM", hardeningMarker) == 0) {
            dao.activeRoles().filter { it.code != "ADMIN" }.forEach { role ->
                val current = dao.permissionCodesForRole(role.code).toSet()
                val upgrades = mutableSetOf<String>()
                if (SecurityPermissions.ACCOUNTING_POST in current) {
                    upgrades += SecurityPermissions.BANK_RECONCILIATION_POST
                    upgrades += SecurityPermissions.FIXED_ASSET_POST
                    upgrades += SecurityPermissions.FX_REVALUATION_POST
                    upgrades += SecurityPermissions.ACCOUNTING_PERIOD_MANAGE
                    upgrades += SecurityPermissions.ACCOUNTING_YEAR_CLOSE
                }
                if (SecurityPermissions.TREASURY_POST in current) {
                    upgrades += SecurityPermissions.CASH_COUNT_POST
                }
                if (upgrades.isNotEmpty()) {
                    dao.insertRolePermissionsIgnore(upgrades.map { RolePermissionEntity(role.code, it) })
                }
            }
            db.governanceDao().insertAudit(
                AuditEventEntity(
                    userId = 0L,
                    action = "SECURITY_PERMISSION_UPGRADE",
                    entityType = "SYSTEM",
                    entityId = hardeningMarker,
                    newValue = "specialized accounting permissions seeded from legacy write permissions",
                    reason = "v108 accounting authorization hardening compatibility upgrade"
                )
            )
        }

        // One-time compatibility upgrade for v118 customer creation permission.
        // Before v118, createCustomer() was guarded by SALES_POST. Any role that already
        // had SALES_POST therefore keeps its existing ability to create customers after
        // the permission is separated into CUSTOMERS_CREATE. The marker makes this a
        // one-time migration of authorization state and avoids re-granting it later.
        val customerCreateMarker = "CUSTOMER_CREATE_V118"
        if (db.governanceDao().auditMarkerCount("SECURITY_PERMISSION_UPGRADE", "SYSTEM", customerCreateMarker) == 0) {
            dao.activeRoles().filter { it.code != "ADMIN" }.forEach { role ->
                val current = dao.permissionCodesForRole(role.code).toSet()
                if (SecurityPermissions.SALES_POST in current) {
                    dao.insertRolePermissionsIgnore(
                        listOf(RolePermissionEntity(role.code, SecurityPermissions.CUSTOMERS_CREATE))
                    )
                }
            }
            db.governanceDao().insertAudit(
                AuditEventEntity(
                    userId = 0L,
                    action = "SECURITY_PERMISSION_UPGRADE",
                    entityType = "SYSTEM",
                    entityId = customerCreateMarker,
                    newValue = "CUSTOMERS_CREATE inherited once from legacy SALES_POST",
                    reason = "v118 customer create permission compatibility upgrade"
                )
            )
        }

        // v207: customer master-data editing is now a dedicated permission instead of piggybacking on SALES_POST.
        // Roles that historically could edit customers through SALES_POST keep that capability once.
        // The marker prevents a later administrator revocation from being silently re-granted.
        val customerEditMarker = "CUSTOMER_EDIT_V207"
        if (db.governanceDao().auditMarkerCount("SECURITY_PERMISSION_UPGRADE", "SYSTEM", customerEditMarker) == 0) {
            dao.activeRoles().filter { it.code != "ADMIN" }.forEach { role ->
                val current = dao.permissionCodesForRole(role.code).toSet()
                if (SecurityPermissions.SALES_POST in current) {
                    dao.insertRolePermissionsIgnore(
                        listOf(RolePermissionEntity(role.code, SecurityPermissions.CUSTOMERS_EDIT))
                    )
                }
            }
            db.governanceDao().insertAudit(
                AuditEventEntity(
                    userId = 0L,
                    action = "SECURITY_PERMISSION_UPGRADE",
                    entityType = "SYSTEM",
                    entityId = customerEditMarker,
                    newValue = "CUSTOMERS_EDIT inherited once from legacy SALES_POST",
                    reason = "v207 customer edit permission compatibility upgrade"
                )
            )
        }

        // One-time compatibility upgrade for v126 collection settlement discounts.
        // The permission is intentionally separate from COLLECTION_POST. Existing SALES system roles
        // retain the new business capability, while ACCOUNTANT/CASHIER/custom roles
        // do not receive it automatically and can be granted it explicitly by an administrator.
        val collectionDiscountMarker = "COLLECTION_DISCOUNT_V126"
        if (db.governanceDao().auditMarkerCount("SECURITY_PERMISSION_UPGRADE", "SYSTEM", collectionDiscountMarker) == 0) {
            listOf("SALES").forEach { roleCode ->
                val role = dao.roleByCode(roleCode)
                if (role != null) {
                    val current = dao.permissionCodesForRole(roleCode).toSet()
                    if (SecurityPermissions.COLLECTION_POST in current) {
                        dao.insertRolePermissionsIgnore(
                            listOf(RolePermissionEntity(roleCode, SecurityPermissions.COLLECTION_DISCOUNT_POST))
                        )
                    }
                }
            }
            db.governanceDao().insertAudit(
                AuditEventEntity(
                    userId = 0L,
                    action = "SECURITY_PERMISSION_UPGRADE",
                    entityType = "SYSTEM",
                    entityId = collectionDiscountMarker,
                    newValue = "COLLECTION_DISCOUNT_POST granted once to eligible SALES system role",
                    reason = "v126 collection settlement discount authorization"
                )
            )
        }

        // v128: ACCOUNTANT is an accounting role, not a cashier/collector/payment operator.
        // Revoke legacy operational-cash permissions once so old databases match the new role boundary.
        val accountantSeparationMarker = "ACCOUNTANT_SEPARATION_V128"
        if (db.governanceDao().auditMarkerCount("SECURITY_PERMISSION_HARDENING", "SYSTEM", accountantSeparationMarker) == 0) {
            val revoked = setOf(
                SecurityPermissions.TREASURY_POST,
                SecurityPermissions.CASH_COUNT_POST,
                SecurityPermissions.COLLECTION_POST,
                SecurityPermissions.COLLECTION_DISCOUNT_POST,
                SecurityPermissions.SUPPLIER_PAYMENT_POST,
                SecurityPermissions.GEOGRAPHY_MANAGE
            )
            if (dao.roleByCode("ACCOUNTANT") != null) {
                dao.deleteRolePermissions("ACCOUNTANT", revoked)
            }
            db.governanceDao().insertAudit(
                AuditEventEntity(
                    userId = 0L,
                    action = "SECURITY_PERMISSION_HARDENING",
                    entityType = "SYSTEM",
                    entityId = accountantSeparationMarker,
                    newValue = "ACCOUNTANT separated from treasury, cash count, collections, collection discounts, supplier payments and geography management",
                    reason = "v128 accountant role separation"
                )
            )
        }

        // v202: split exchange-rate engine permissions from broad geography management.
        // Existing roles inherit only capabilities they already had, except ACCOUNTANT which is
        // explicitly allowed to view, refresh and approve market rates as requested.
        val exchangeRatePermissionsMarker = "EXCHANGE_RATE_PERMISSIONS_V202"
        if (db.governanceDao().auditMarkerCount("SECURITY_PERMISSION_UPGRADE", "SYSTEM", exchangeRatePermissionsMarker) == 0) {
            dao.activeRoles().forEach { role ->
                val current = dao.permissionCodesForRole(role.code).toSet()
                val grants = mutableSetOf<String>()
                if (role.code == "ADMIN" || SecurityPermissions.GEOGRAPHY_MANAGE in current) {
                    grants += SecurityPermissions.EXCHANGE_RATE_VIEW
                    grants += SecurityPermissions.EXCHANGE_RATE_REFRESH
                    grants += SecurityPermissions.EXCHANGE_RATE_APPROVE
                    grants += SecurityPermissions.EXCHANGE_RATE_OVERRIDE
                    grants += SecurityPermissions.EXCHANGE_RATE_SETTINGS
                } else if (SecurityPermissions.GEOGRAPHY_VIEW in current) {
                    grants += SecurityPermissions.EXCHANGE_RATE_VIEW
                }
                if (role.code == "ACCOUNTANT") {
                    grants += SecurityPermissions.EXCHANGE_RATE_VIEW
                    grants += SecurityPermissions.EXCHANGE_RATE_REFRESH
                    grants += SecurityPermissions.EXCHANGE_RATE_APPROVE
                }
                if (grants.isNotEmpty()) {
                    dao.insertRolePermissionsIgnore(grants.map { RolePermissionEntity(role.code, it) })
                }
            }
            db.governanceDao().insertAudit(
                AuditEventEntity(
                    userId = 0L,
                    action = "SECURITY_PERMISSION_UPGRADE",
                    entityType = "SYSTEM",
                    entityId = exchangeRatePermissionsMarker,
                    newValue = "FX engine permissions split; accountant receives view/refresh/approve",
                    reason = "v202 local Yemen exchange-rate engine"
                )
            )
        }

        // v154: user-requested ACCOUNTANT scope.
        // Existing databases need a one-time synchronization because system-role defaults are only
        // seeded when a role is new/empty. The requested boundary is:
        // - all SALES permissions
        // - all PURCHASES permissions
        // - all PRODUCTION permissions
        // - both BACKUP permissions
        // - INVENTORY_VIEW only (no transfer/count/adjust)
        // Existing accounting/reporting permissions remain unchanged.
        val accountantScopeMarker = "ACCOUNTANT_SCOPE_V154"
        if (db.governanceDao().auditMarkerCount("SECURITY_PERMISSION_UPGRADE", "SYSTEM", accountantScopeMarker) == 0) {
            if (dao.roleByCode("ACCOUNTANT") != null) {
                val granted = PermissionCatalog.permissions
                    .filter { it.moduleKey in setOf("SALES", "PURCHASES", "PRODUCTION") }
                    .map { it.code }
                    .toMutableSet()
                    .apply {
                        add(SecurityPermissions.INVENTORY_VIEW)
                        add(SecurityPermissions.BACKUP_CREATE)
                        add(SecurityPermissions.BACKUP_RESTORE)
                    }
                dao.insertRolePermissionsIgnore(
                    granted.map { RolePermissionEntity("ACCOUNTANT", it) }
                )

                val revokedInventoryWrites = setOf(
                    SecurityPermissions.INVENTORY_TRANSFER,
                    SecurityPermissions.INVENTORY_COUNT,
                    SecurityPermissions.INVENTORY_ADJUST
                )
                dao.deleteRolePermissions("ACCOUNTANT", revokedInventoryWrites)
            }
            db.governanceDao().insertAudit(
                AuditEventEntity(
                    userId = 0L,
                    action = "SECURITY_PERMISSION_UPGRADE",
                    entityType = "SYSTEM",
                    entityId = accountantScopeMarker,
                    newValue = "ACCOUNTANT: all purchases, sales, production and backup permissions; inventory view only",
                    reason = "v154 accountant requested permission scope"
                )
            )
        }

        // v204: requested default ACCOUNTANT capability to manage treasury vouchers.
        // This grants only TREASURY_POST (receipt/payment/income/expense/transfer vouchers).
        // Cash-count, collection-discount and other specialized treasury capabilities remain separate.
        val accountantTreasuryVoucherMarker = "ACCOUNTANT_TREASURY_VOUCHERS_V204"
        if (db.governanceDao().auditMarkerCount("SECURITY_PERMISSION_UPGRADE", "SYSTEM", accountantTreasuryVoucherMarker) == 0) {
            if (dao.roleByCode("ACCOUNTANT") != null) {
                dao.insertRolePermissionsIgnore(
                    listOf(RolePermissionEntity("ACCOUNTANT", SecurityPermissions.TREASURY_POST))
                )
            }
            db.governanceDao().insertAudit(
                AuditEventEntity(
                    userId = 0L,
                    action = "SECURITY_PERMISSION_UPGRADE",
                    entityType = "SYSTEM",
                    entityId = accountantTreasuryVoucherMarker,
                    newValue = "ACCOUNTANT granted TREASURY_POST for treasury voucher management",
                    reason = "v204 requested accountant default treasury voucher management"
                )
            )
        }

    }

    suspend fun bootstrapFirstAdmin(
        username: String,
        displayName: String,
        password: CharArray,
        now: Long = com.fush.erp.domain.TrustedTimeService.now()
    ): UserEntity = db.withTransaction {
        require(db.userDao().count() == 0) { "تم إعداد مدير النظام مسبقًا" }
        val normalized = username.trim()
        require(normalized.matches(Regex("[A-Za-z0-9._-]{3,40}"))) {
            "اسم المستخدم يجب أن يكون 3-40 حرفًا إنجليزيًا/رقمًا ويمكن استخدام . _ -"
        }
        require(displayName.trim().length >= 2) { "أدخل الاسم الظاهر لمدير النظام" }
        PasswordPolicy.validate(password, normalized)?.let { throw IllegalArgumentException(it) }

        val salt = PasswordHasher.newSalt()
        val hash = PasswordHasher.hash(password, salt)
        val id = db.userDao().insert(
            UserEntity(
                username = normalized,
                displayName = displayName.trim(),
                passwordHash = hash,
                salt = salt,
                role = "ADMIN",
                isActive = true,
                mustChangePassword = false,
                lastLoginAt = now,
                passwordChangedAt = now,
                sessionVersion = 1,
                createdAt = now,
                updatedAt = now
            )
        )
        db.securityDao().insertPasswordHistory(
            UserPasswordHistoryEntity(userId = id, passwordHash = hash, salt = salt, createdAt = now)
        )
        audit(id, "ADMIN_BOOTSTRAPPED", "USER", id.toString(), newValue = "username=$normalized;role=ADMIN")
        db.userDao().byId(id) ?: error("تعذر إنشاء مدير النظام")
    }

    suspend fun bootstrapCloudMember(
        username: String,
        displayName: String,
        role: String,
        password: CharArray,
        now: Long = com.fush.erp.domain.TrustedTimeService.now(),
    ): UserEntity = db.withTransaction {
        require(db.userDao().count() == 0) { "تم إعداد مستخدم محلي على هذا الهاتف مسبقًا" }
        val normalized = username.trim()
        require(normalized.matches(Regex("[A-Za-z0-9._-]{3,40}"))) {
            "اسم المستخدم السحابي المرتبط غير صالح للاستخدام المحلي"
        }
        require(displayName.trim().length >= 2) { "الاسم الظاهر في الهوية السحابية غير صالح" }
        val normalizedRole = role.trim().uppercase().let { if (it in setOf("OWNER", "ADMIN")) "ADMIN" else it }
        require(db.securityDao().roleByCode(normalizedRole) != null) {
            "الدور السحابي $normalizedRole غير موجود في هذا الإصدار من التطبيق"
        }
        PasswordPolicy.validate(password, normalized)?.let { throw IllegalArgumentException(it) }

        val salt = PasswordHasher.newSalt()
        val hash = PasswordHasher.hash(password, salt)
        val id = db.userDao().insert(
            UserEntity(
                username = normalized,
                displayName = displayName.trim(),
                passwordHash = hash,
                salt = salt,
                role = normalizedRole,
                isActive = true,
                mustChangePassword = false,
                lastLoginAt = now,
                passwordChangedAt = now,
                sessionVersion = 1,
                createdAt = now,
                updatedAt = now,
            )
        )
        db.securityDao().insertPasswordHistory(
            UserPasswordHistoryEntity(userId = id, passwordHash = hash, salt = salt, createdAt = now)
        )
        audit(id, "CLOUD_MEMBER_BOOTSTRAPPED", "USER", id.toString(), newValue = "username=$normalized;role=$normalizedRole")
        db.userDao().byId(id) ?: error("تعذر إنشاء مستخدم FUSH من الهوية السحابية")
    }

    suspend fun authenticate(
        username: String,
        password: CharArray,
        now: Long = TrustedTimeService.now()
    ): AuthenticationResult {
        TrustedTimeService.failureReason()?.let { return AuthenticationResult.Failure(it) }
        val normalized = username.trim()
        if (normalized.isBlank() || password.isEmpty()) return AuthenticationResult.Failure("اسم المستخدم أو كلمة المرور غير صحيحة")
        return db.withTransaction {
            val dao = db.userDao()
            var user = dao.byUsername(normalized)
                ?: return@withTransaction AuthenticationResult.Failure("اسم المستخدم أو كلمة المرور غير صحيحة")
            if (!user.isActive) return@withTransaction AuthenticationResult.Failure("الحساب غير نشط. راجع مدير النظام")
            if (user.role == SupportPolicy.SUPPORT_ROLE && vendorSupportProvisioning?.isCurrentVendorIdentity(user.id) != true) {
                return@withTransaction AuthenticationResult.Failure("هوية FUSH Support غير Provisioned لهذا التثبيت")
            }
            val lockedUntil = user.lockedUntil
            if (lockedUntil != null && lockedUntil > now) return@withTransaction AuthenticationResult.Locked(lockedUntil)
            if (lockedUntil != null && lockedUntil <= now) {
                user = user.copy(failedLoginAttempts = 0, lockedUntil = null, updatedAt = now)
                dao.update(user)
            }
            if (!PasswordHasher.verify(password, user.salt, user.passwordHash)) {
                return@withTransaction registerFailedLogin(user, now, "BAD_CREDENTIALS")
            }
            val nextSessionVersion = user.sessionVersion + 1
            val updated = user.copy(
                failedLoginAttempts = 0,
                lockedUntil = null,
                lastLoginAt = now,
                mustChangePassword = user.mustChangePassword || PasswordPolicy.isExpired(user.passwordChangedAt, now),
                sessionVersion = nextSessionVersion,
                mfaEnabled = false,
                mfaSecretCiphertext = null,
                mfaConfirmedAt = null,
                mfaVerifiedSessionVersion = -1,
                updatedAt = now
            )
            dao.update(updated)
            db.securityDao().deleteMfaRecoveryCodes(user.id)
            recordRecentReauthentication(user.id, nextSessionVersion, TrustedTimeService.elapsedRealtime())
            audit(user.id, "LOGIN_SUCCESS", "USER", user.id.toString(), reason = "PASSWORD_ONLY")
            AuthenticationResult.Success(updated)
        }
    }

    suspend fun permissionsFor(user: UserEntity): Set<String> {
        if (!user.isActive) return emptySet()
        if (user.role == SupportPolicy.SUPPORT_ROLE && vendorSupportProvisioning?.isCurrentVendorIdentity(user.id) != true) return emptySet()
        return if (user.role == "ADMIN") PermissionCatalog.permissions.map { it.code }.toSet()
        else db.securityDao().permissionCodesForRole(user.role).toSet()
    }

    suspend fun hasPermission(userId: Long, permissionCode: String): Boolean {
        val user = db.userDao().byId(userId) ?: return false
        if (!user.isActive) return false
        if (user.role == SupportPolicy.SUPPORT_ROLE && vendorSupportProvisioning?.isCurrentVendorIdentity(user.id) != true) return false
        if (user.role == "ADMIN") return true
        return db.securityDao().hasPermission(user.role, permissionCode) > 0
    }

    suspend fun requirePermission(userId: Long, permissionCode: String) {
        val user = db.userDao().byId(userId) ?: throw SecurityException("المستخدم غير موجود")
        if (user.role == SupportPolicy.SUPPORT_ROLE) {
            require(vendorSupportProvisioning?.isCurrentVendorIdentity(user.id) == true) { "Vendor Support Identity غير صالحة لهذا التثبيت" }
        }
        db.requireUserPermission(userId, permissionCode)
    }

    suspend fun hasRecentReauthentication(
        userId: Long,
        now: Long = TrustedTimeService.elapsedRealtime()
    ): Boolean {
        if (!TrustedTimeService.isTrusted()) return false
        val user = db.userDao().byId(userId) ?: return false
        if (!user.isActive) return false
        val stamp = synchronized(recentReauthentication) { recentReauthentication[userId] } ?: return false
        return stamp.sessionVersion == user.sessionVersion && ReauthenticationPolicy.isFresh(stamp.verifiedAt, now)
    }

    suspend fun requireRecentReauthentication(
        userId: Long,
        actionCode: String,
        now: Long = TrustedTimeService.elapsedRealtime()
    ) {
        TrustedTimeService.failureReason()?.let { throw ClockTamperDetectedException(it) }
        if (hasRecentReauthentication(userId, now)) return
        audit(userId, "REAUTH_REQUIRED", "SECURITY_ACTION", actionCode, reason = "FRESH_AUTH_REQUIRED")
        throw RecentAuthenticationRequiredException(
            "يلزم إعادة التحقق بكلمة المرور خلال آخر ${ReauthenticationPolicy.WINDOW_MINUTES} دقائق لهذه العملية"
        )
    }

    suspend fun reauthenticate(
        userId: Long,
        password: CharArray,
        now: Long = TrustedTimeService.now()
    ): ReauthenticationResult {
        TrustedTimeService.failureReason()?.let { return ReauthenticationResult.Failure(it) }
        return db.withTransaction {
        val dao = db.userDao()
        var user = dao.byId(userId) ?: return@withTransaction ReauthenticationResult.Failure("المستخدم غير موجود")
        if (!user.isActive) return@withTransaction ReauthenticationResult.Failure("الحساب غير نشط")
        if (user.role == SupportPolicy.SUPPORT_ROLE && vendorSupportProvisioning?.isCurrentVendorIdentity(user.id) != true) {
            return@withTransaction ReauthenticationResult.Failure("هوية FUSH Support غير Provisioned لهذا التثبيت")
        }
        val lockedUntil = user.lockedUntil
        if (lockedUntil != null && lockedUntil > now) return@withTransaction ReauthenticationResult.Locked(lockedUntil)
        if (lockedUntil != null && lockedUntil <= now) {
            user = user.copy(failedLoginAttempts = 0, lockedUntil = null, updatedAt = now)
            dao.update(user)
        }
        if (!PasswordHasher.verify(password, user.salt, user.passwordHash)) {
            return@withTransaction registerFailedReauthentication(user, now, "BAD_PASSWORD")
        }
        if (user.failedLoginAttempts != 0 || user.lockedUntil != null) {
            dao.update(user.copy(failedLoginAttempts = 0, lockedUntil = null, updatedAt = now))
        }
        recordRecentReauthentication(user.id, user.sessionVersion, TrustedTimeService.elapsedRealtime())
        audit(user.id, "REAUTH_SUCCESS", "USER", user.id.toString(), reason = "PASSWORD_VERIFIED")
        ReauthenticationResult.Success
        }
    }

    suspend fun createVendorSupportProvisioningChallenge(
        actorUserId: Long,
        purpose: String? = null,
    ): VendorProvisioningChallenge {
        val actor = db.userDao().byId(actorUserId) ?: throw IllegalArgumentException("المستخدم غير موجود")
        require(actor.isActive && actor.role == "ADMIN") { "Provisioning يتطلب مدير الشركة" }
        requireRecentReauthentication(actorUserId, "VENDOR_SUPPORT_PROVISION_CHALLENGE")
        val manager = vendorSupportProvisioning ?: throw IllegalStateException("Vendor provisioning غير متاح")
        val challenge = manager.createChallenge(purpose)
        audit(
            actorUserId,
            "VENDOR_SUPPORT_PROVISION_CHALLENGE",
            "VENDOR_SUPPORT",
            "LIFECYCLE",
            newValue = "purpose=${challenge.purpose};nextCredentialVersion=${challenge.nextCredentialVersion};keyId=${challenge.currentKeyId}",
            reason = "SIGNED_VENDOR_LIFECYCLE_REQUEST",
        )
        return challenge
    }

    suspend fun provisionVendorSupport(actorUserId: Long, signedPackage: String): Long {
        val actor = db.userDao().byId(actorUserId) ?: throw IllegalArgumentException("المستخدم غير موجود")
        require(actor.isActive && actor.role == "ADMIN") { "Provisioning يتطلب مدير الشركة" }
        requireRecentReauthentication(actorUserId, "VENDOR_SUPPORT_PROVISION")
        val manager = vendorSupportProvisioning ?: throw IllegalStateException("Vendor provisioning غير متاح")
        val id = manager.provision(actorUserId, signedPackage)
        val latest = manager.lifecycleState().latestIdentity
        audit(
            actorUserId,
            "VENDOR_SUPPORT_${latest?.lifecycleAction ?: "PROVISION"}_SUCCESS",
            "USER",
            id.toString(),
            newValue = "role=${SupportPolicy.SUPPORT_ROLE};credentialVersion=${latest?.credentialVersion ?: 0}",
            reason = "FUSH_SIGNED_VENDOR_PACKAGE",
        )
        return id
    }

    suspend fun rotateVendorSupportSigningKey(actorUserId: Long, signedPackage: String): String {
        val actor = db.userDao().byId(actorUserId) ?: throw IllegalArgumentException("المستخدم غير موجود")
        require(actor.isActive && actor.role == "ADMIN") { "Key rotation يتطلب مدير الشركة" }
        requireRecentReauthentication(actorUserId, "VENDOR_SUPPORT_KEY_ROTATE")
        val manager = vendorSupportProvisioning ?: throw IllegalStateException("Vendor provisioning غير متاح")
        val keyId = manager.rotateSigningKey(actorUserId, signedPackage)
        audit(
            actorUserId,
            "VENDOR_SUPPORT_KEY_ROTATED",
            "VENDOR_SUPPORT_KEY",
            keyId,
            reason = "FUSH_SIGNED_KEY_SUPERSESSION",
        )
        return keyId
    }

    suspend fun createUser(
        actorUserId: Long,
        username: String,
        displayName: String,
        roleCode: String,
        temporaryPassword: CharArray
    ): Long = db.withTransaction {
        requirePermission(actorUserId, SecurityPermissions.USERS_MANAGE)
        requireRecentReauthentication(actorUserId, "USER_CREATE")
        val normalized = username.trim()
        require(normalized.matches(Regex("[A-Za-z0-9._-]{3,40}"))) { "اسم المستخدم يجب أن يكون 3-40 حرفًا إنجليزيًا/رقمًا ويمكن استخدام . _ -" }
        require(displayName.trim().length >= 2) { "أدخل الاسم الظاهر للمستخدم" }
        require(db.userDao().byUsername(normalized) == null) { "اسم المستخدم مستخدم مسبقًا" }
        val role = db.securityDao().roleByCode(roleCode) ?: throw IllegalArgumentException("الدور غير موجود")
        require(role.isActive) { "الدور غير نشط" }
        require(role.code != SupportPolicy.SUPPORT_ROLE) {
            "دور FUSH_SUPPORT هو Vendor Identity محمي ولا يمكن لمدير الشركة إنشاء حساب دعم محليًا"
        }
        PasswordPolicy.validate(temporaryPassword, normalized)?.let { throw IllegalArgumentException(it) }
        val salt = PasswordHasher.newSalt()
        val hash = PasswordHasher.hash(temporaryPassword, salt)
        val now = com.fush.erp.domain.TrustedTimeService.now()
        val id = db.userDao().insert(
            UserEntity(
                username = normalized,
                displayName = displayName.trim(),
                passwordHash = hash,
                salt = salt,
                role = roleCode,
                mustChangePassword = true,
                passwordChangedAt = now,
                createdAt = now,
                updatedAt = now
            )
        )
        db.securityDao().insertPasswordHistory(UserPasswordHistoryEntity(userId = id, passwordHash = hash, salt = salt, createdAt = now))
        audit(actorUserId, "USER_CREATED", "USER", id.toString(), newValue = "username=$normalized;role=$roleCode")
        id
    }

    suspend fun setUserActive(actorUserId: Long, targetUserId: Long, active: Boolean) = db.withTransaction {
        requirePermission(actorUserId, SecurityPermissions.USERS_MANAGE)
        requireRecentReauthentication(actorUserId, if (active) "USER_ENABLE" else "USER_DISABLE")
        require(actorUserId != targetUserId || active) { "لا يمكنك تعطيل حسابك الحالي" }
        val target = db.userDao().byId(targetUserId) ?: throw IllegalArgumentException("المستخدم غير موجود")
        require(target.role != SupportPolicy.SUPPORT_ROLE) { "حساب Vendor Support لا يُدار من شاشة مستخدمي الشركة؛ ألغِ Support Session بدلًا من ذلك" }
        if (!active && target.role == "ADMIN" && target.isActive && db.userDao().activeAdminCount() <= 1) {
            throw IllegalStateException("لا يمكن تعطيل آخر مدير نظام نشط")
        }
        if (target.isActive == active) return@withTransaction
        val updated = target.copy(isActive = active, sessionVersion = target.sessionVersion + 1, mfaVerifiedSessionVersion = -1, updatedAt = com.fush.erp.domain.TrustedTimeService.now())
        db.userDao().update(updated)
        audit(actorUserId, if (active) "USER_ENABLED" else "USER_DISABLED", "USER", targetUserId.toString())
    }

    suspend fun assignRole(actorUserId: Long, targetUserId: Long, roleCode: String) = db.withTransaction {
        requirePermission(actorUserId, SecurityPermissions.USERS_MANAGE)
        requireRecentReauthentication(actorUserId, "USER_ROLE_CHANGE")
        val role = db.securityDao().roleByCode(roleCode) ?: throw IllegalArgumentException("الدور غير موجود")
        require(role.isActive) { "الدور غير نشط" }
        val target = db.userDao().byId(targetUserId) ?: throw IllegalArgumentException("المستخدم غير موجود")
        require(role.code != SupportPolicy.SUPPORT_ROLE && target.role != SupportPolicy.SUPPORT_ROLE) {
            "FUSH_SUPPORT Vendor Identity لا يمكن إنشاؤها أو إسنادها أو نزعها من مدير الشركة المحلي"
        }
        if (target.role == "ADMIN" && roleCode != "ADMIN" && target.isActive && db.userDao().activeAdminCount() <= 1) {
            throw IllegalStateException("لا يمكن إزالة دور المدير من آخر مدير نظام نشط")
        }
        if (target.role == roleCode) return@withTransaction
        db.userDao().update(target.copy(role = roleCode, sessionVersion = target.sessionVersion + 1, mfaVerifiedSessionVersion = -1, updatedAt = com.fush.erp.domain.TrustedTimeService.now()))
        audit(actorUserId, "USER_ROLE_CHANGED", "USER", targetUserId.toString(), oldValue = target.role, newValue = roleCode)
    }

    suspend fun resetPassword(actorUserId: Long, targetUserId: Long, temporaryPassword: CharArray) = db.withTransaction {
        requirePermission(actorUserId, SecurityPermissions.USERS_MANAGE)
        requireRecentReauthentication(actorUserId, "PASSWORD_RESET")
        val target = db.userDao().byId(targetUserId) ?: throw IllegalArgumentException("المستخدم غير موجود")
        require(target.role != SupportPolicy.SUPPORT_ROLE) { "كلمة مرور Vendor Support لا يمكن إعادة ضبطها بواسطة مدير الشركة" }
        PasswordPolicy.validate(temporaryPassword, target.username)?.let { throw IllegalArgumentException(it) }
        rejectPasswordReuse(target, temporaryPassword)
        ensureCurrentPasswordInHistory(target)
        val salt = PasswordHasher.newSalt()
        val hash = PasswordHasher.hash(temporaryPassword, salt)
        val now = com.fush.erp.domain.TrustedTimeService.now()
        db.userDao().update(target.copy(
            passwordHash = hash,
            salt = salt,
            mustChangePassword = true,
            passwordChangedAt = now,
            failedLoginAttempts = 0,
            lockedUntil = null,
            sessionVersion = target.sessionVersion + 1,
            mfaEnabled = false,
            mfaSecretCiphertext = null,
            mfaConfirmedAt = null,
            mfaVerifiedSessionVersion = -1,
            updatedAt = now
        ))
        db.securityDao().deleteMfaRecoveryCodes(target.id)
        db.securityDao().insertPasswordHistory(UserPasswordHistoryEntity(userId = target.id, passwordHash = hash, salt = salt, createdAt = now))
        db.securityDao().prunePasswordHistory(target.id, PasswordPolicy.HISTORY_COUNT)
        audit(actorUserId, "PASSWORD_RESET", "USER", targetUserId.toString(), reason = "ADMIN_RESET")
    }

    suspend fun changePassword(userId: Long, currentPassword: CharArray, newPassword: CharArray): UserEntity = db.withTransaction {
        val user = db.userDao().byId(userId) ?: throw IllegalArgumentException("المستخدم غير موجود")
        require(user.isActive) { "الحساب غير نشط" }
        require(user.role != SupportPolicy.SUPPORT_ROLE) { "بيانات اعتماد Vendor Support تُدار فقط عبر Signed Provisioning" }
        require(PasswordHasher.verify(currentPassword, user.salt, user.passwordHash)) { "كلمة المرور الحالية غير صحيحة" }
        PasswordPolicy.validate(newPassword, user.username)?.let { throw IllegalArgumentException(it) }
        rejectPasswordReuse(user, newPassword)
        ensureCurrentPasswordInHistory(user)
        val salt = PasswordHasher.newSalt()
        val hash = PasswordHasher.hash(newPassword, salt)
        val now = com.fush.erp.domain.TrustedTimeService.now()
        val nextSessionVersion = user.sessionVersion + 1
        val updated = user.copy(
            passwordHash = hash,
            salt = salt,
            mustChangePassword = false,
            passwordChangedAt = now,
            sessionVersion = nextSessionVersion,
            mfaEnabled = false,
            mfaSecretCiphertext = null,
            mfaConfirmedAt = null,
            mfaVerifiedSessionVersion = -1,
            updatedAt = now
        )
        db.userDao().update(updated)
        db.securityDao().deleteMfaRecoveryCodes(user.id)
        clearRecentReauthentication(user.id)
        db.securityDao().insertPasswordHistory(UserPasswordHistoryEntity(userId = user.id, passwordHash = hash, salt = salt, createdAt = now))
        db.securityDao().prunePasswordHistory(user.id, PasswordPolicy.HISTORY_COUNT)
        audit(userId, "PASSWORD_CHANGED", "USER", userId.toString())
        updated
    }

    suspend fun saveRolePermissions(actorUserId: Long, roleCode: String, permissionCodes: Set<String>) = db.withTransaction {
        requirePermission(actorUserId, SecurityPermissions.ROLES_MANAGE)
        requireRecentReauthentication(actorUserId, "ROLE_PERMISSIONS_CHANGE")
        val role = db.securityDao().roleByCode(roleCode) ?: throw IllegalArgumentException("الدور غير موجود")
        require(role.code != SupportPolicy.SUPPORT_ROLE) { "صلاحيات FUSH_SUPPORT ثابتة ومدارة من FUSH وليست قابلة للتعديل محليًا" }
        val valid = db.securityDao().allPermissions().map { it.code }.toSet()
        require(permissionCodes.all { it in valid }) { "توجد صلاحية غير معروفة" }
        val supportCodes = setOf(
            SecurityPermissions.SUPPORT_VIEW, SecurityPermissions.SUPPORT_DIAGNOSE, SecurityPermissions.SUPPORT_REPAIR,
            SecurityPermissions.SUPPORT_RECALCULATE, SecurityPermissions.SUPPORT_CORRECT_DATA,
            SecurityPermissions.SUPPORT_TEST_DATA_DELETE
        )
        if (role.code != "ADMIN" && role.code != SupportPolicy.SUPPORT_ROLE) {
            require(permissionCodes.none { it in supportCodes }) { "صلاحيات Support لا تُمنح للأدوار العادية" }
        }
        val finalCodes = when (role.code) {
            "ADMIN" -> valid
            SupportPolicy.SUPPORT_ROLE -> permissionCodes.intersect(supportCodes)
            else -> permissionCodes
        }
        val previousCodes = db.securityDao().permissionCodesForRole(roleCode).toSet()
        db.securityDao().replaceRolePermissions(roleCode, finalCodes)

        // Permission grants are observed live by active app sessions through role_permissions.
        // Invalidating every session even for an additive grant caused users (notably ACCOUNTANT)
        // to be logged out by HomeShell's 15-second session-version check shortly after an admin
        // granted TREASURY_POST, making the newly granted permission appear to "lock itself".
        // Keep the fail-closed behavior for any revocation/reduction: removing even one permission
        // still invalidates all sessions for that role immediately.
        val invalidatedSessions = rolePermissionChangeRequiresSessionInvalidation(previousCodes, finalCodes)
        if (invalidatedSessions) {
            db.userDao().invalidateSessionsForRole(roleCode, com.fush.erp.domain.TrustedTimeService.now())
        }
        audit(
            actorUserId,
            "ROLE_PERMISSIONS_CHANGED",
            "ROLE",
            roleCode,
            oldValue = previousCodes.sorted().joinToString(","),
            newValue = finalCodes.sorted().joinToString(","),
            reason = if (invalidatedSessions) "PERMISSION_REVOKED_SESSION_INVALIDATED" else "ADDITIVE_GRANT_APPLIED_LIVE",
        )
    }

    suspend fun deleteRole(actorUserId: Long, roleCode: String) = db.withTransaction {
        requirePermission(actorUserId, SecurityPermissions.ROLES_MANAGE)
        requireRecentReauthentication(actorUserId, "ROLE_DELETE")
        val normalized = roleCode.trim().uppercase()
        val role = db.securityDao().roleByCode(normalized) ?: throw IllegalArgumentException("الدور غير موجود")
        require(!role.isSystem && role.code != "ADMIN") { "لا يمكن حذف دور نظام محمي" }
        val assignedUsers = db.userDao().userCountForRole(role.code)
        require(assignedUsers == 0) {
            "لا يمكن حذف الدور ${role.nameAr}: يوجد $assignedUsers مستخدم مرتبط به. غيّر دور المستخدمين أولاً."
        }
        val deleted = db.securityDao().deleteRole(role.code)
        require(deleted == 1) { "تعذر حذف الدور" }
        audit(actorUserId, "ROLE_DELETED", "ROLE", role.code, oldValue = role.nameAr)
    }

    suspend fun saveCustomRole(actorUserId: Long, code: String, nameAr: String, description: String): RoleEntity = db.withTransaction {
        requirePermission(actorUserId, SecurityPermissions.ROLES_MANAGE)
        requireRecentReauthentication(actorUserId, "ROLE_CREATE_OR_UPDATE")
        val normalized = code.trim().uppercase()
        require(normalized.matches(Regex("[A-Z0-9_]{3,30}"))) { "رمز الدور يجب أن يكون أحرفًا إنجليزية كبيرة/أرقامًا/شرطة سفلية" }
        require(nameAr.trim().length >= 2) { "أدخل اسم الدور" }
        val existing = db.securityDao().roleByCode(normalized)
        require(existing == null || !existing.isSystem) { "لا يمكن تعديل دور نظام أساسي" }
        val now = com.fush.erp.domain.TrustedTimeService.now()
        val row = RoleEntity(
            code = normalized,
            nameAr = nameAr.trim(),
            nameEn = existing?.nameEn ?: normalized,
            description = description.trim(),
            isSystem = false,
            isActive = true,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        db.securityDao().upsertRole(row)
        audit(actorUserId, if (existing == null) "ROLE_CREATED" else "ROLE_UPDATED", "ROLE", normalized, newValue = row.nameAr)
        row
    }

    private fun recordRecentReauthentication(userId: Long, sessionVersion: Long, verifiedAt: Long) {
        synchronized(recentReauthentication) {
            recentReauthentication[userId] = ReauthenticationStamp(sessionVersion, verifiedAt)
        }
    }

    private fun clearRecentReauthentication(userId: Long) {
        synchronized(recentReauthentication) { recentReauthentication.remove(userId) }
    }

    private suspend fun registerFailedReauthentication(
        user: UserEntity,
        now: Long,
        reason: String
    ): ReauthenticationResult {
        val decision = LoginLockoutPolicy.onFailure(user.failedLoginAttempts, user.lockoutCount, now)
        val locked = decision.lockedUntil != null
        db.userDao().update(user.copy(
            failedLoginAttempts = decision.failedAttempts,
            lockoutCount = decision.lockoutCount,
            lockedUntil = decision.lockedUntil,
            sessionVersion = if (locked) user.sessionVersion + 1 else user.sessionVersion,
            mfaVerifiedSessionVersion = if (locked) -1 else user.mfaVerifiedSessionVersion,
            updatedAt = now
        ))
        if (locked) clearRecentReauthentication(user.id)
        audit(
            user.id,
            "REAUTH_FAILED",
            "USER",
            user.id.toString(),
            reason = if (locked) "ACCOUNT_LOCKED_$reason" else reason
        )
        return decision.lockedUntil?.let { ReauthenticationResult.Locked(it) }
            ?: ReauthenticationResult.Failure("كلمة المرور الحالية غير صحيحة")
    }

    private suspend fun registerFailedLogin(user: UserEntity, now: Long, reason: String): AuthenticationResult {
        val decision = LoginLockoutPolicy.onFailure(user.failedLoginAttempts, user.lockoutCount, now)
        db.userDao().update(user.copy(
            failedLoginAttempts = decision.failedAttempts,
            lockoutCount = decision.lockoutCount,
            lockedUntil = decision.lockedUntil,
            mfaVerifiedSessionVersion = -1,
            updatedAt = now
        ))
        audit(user.id, "LOGIN_FAILED", "USER", user.id.toString(), reason = if (decision.lockedUntil != null) "ACCOUNT_LOCKED_$reason" else reason)
        return decision.lockedUntil?.let { AuthenticationResult.Locked(it) }
            ?: AuthenticationResult.Failure("اسم المستخدم أو كلمة المرور غير صحيحة")
    }

    private suspend fun rejectPasswordReuse(user: UserEntity, candidate: CharArray) {
        if (PasswordHasher.verify(candidate, user.salt, user.passwordHash)) throw IllegalArgumentException("لا يمكن إعادة استخدام كلمة المرور الحالية")
        val history = db.securityDao().passwordHistory(user.id, PasswordPolicy.HISTORY_COUNT)
        if (history.any { PasswordHasher.verify(candidate, it.salt, it.passwordHash) }) {
            throw IllegalArgumentException("لا يمكن إعادة استخدام آخر ${PasswordPolicy.HISTORY_COUNT} كلمات مرور")
        }
    }

    private suspend fun ensureCurrentPasswordInHistory(user: UserEntity) {
        val history = db.securityDao().passwordHistory(user.id, PasswordPolicy.HISTORY_COUNT)
        if (history.none { it.passwordHash == user.passwordHash && it.salt == user.salt }) {
            db.securityDao().insertPasswordHistory(
                UserPasswordHistoryEntity(userId = user.id, passwordHash = user.passwordHash, salt = user.salt, createdAt = user.passwordChangedAt ?: user.createdAt)
            )
        }
    }

    suspend fun recordSessionPolicyChange(userId: Long, oldValue: String, newValue: String) {
        audit(
            userId = userId,
            action = "SESSION_POLICY_CHANGED",
            entityType = "SECURITY_POLICY",
            entityId = "SESSION_TIMEOUT",
            oldValue = oldValue,
            newValue = newValue,
            reason = "ADMIN_CONFIGURED"
        )
    }

    suspend fun recordLogout(userId: Long, reason: String = "USER_OR_SESSION_LOGOUT") {
        audit(userId, "LOGOUT", "USER", userId.toString(), reason = reason)
    }

    private suspend fun audit(
        userId: Long,
        action: String,
        entityType: String,
        entityId: String,
        oldValue: String = "",
        newValue: String = "",
        reason: String = ""
    ) {
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = userId,
                action = action,
                entityType = entityType,
                entityId = entityId,
                oldValue = oldValue,
                newValue = newValue,
                reason = reason
            )
        )
    }
}
