package com.fush.erp.domain

import androidx.room.withTransaction
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.AuditEventEntity
import com.fush.erp.data.entity.MfaRecoveryCodeEntity
import com.fush.erp.data.entity.RoleEntity
import com.fush.erp.data.entity.RolePermissionEntity
import com.fush.erp.data.entity.UserEntity
import com.fush.erp.data.entity.UserPasswordHistoryEntity

sealed class AuthenticationResult {
    data class Success(val user: UserEntity) : AuthenticationResult()
    data class MfaRequired(val displayName: String) : AuthenticationResult()
    data class MfaSetupRequired(val userId: Long) : AuthenticationResult()
    data class Failure(val message: String) : AuthenticationResult()
    data class Locked(val until: Long) : AuthenticationResult()
}

data class MfaSetupData(val secret: String, val provisioningUri: String)
data class MfaEnrollmentResult(val user: UserEntity, val recoveryCodes: List<String>)

sealed class ReauthenticationResult {
    data object Success : ReauthenticationResult()
    data object MfaRequired : ReauthenticationResult()
    data class Failure(val message: String) : ReauthenticationResult()
    data class Locked(val until: Long) : ReauthenticationResult()
}

class RecentAuthenticationRequiredException(message: String) : SecurityException(message)

private data class ReauthenticationStamp(val sessionVersion: Long, val verifiedAt: Long)

class SecurityService(private val db: FushDatabase) {
    private val recentReauthentication = mutableMapOf<Long, ReauthenticationStamp>()

    suspend fun seedDefaults() {
        val dao = db.securityDao()
        dao.upsertPermissions(PermissionCatalog.permissions)
        dao.insertRolesIgnore(PermissionCatalog.roles)
        PermissionCatalog.defaultRolePermissions.forEach { (roleCode, codes) ->
            if (roleCode == "ADMIN" || dao.rolePermissionCount(roleCode) == 0) {
                dao.insertRolePermissionsIgnore(codes.map { RolePermissionEntity(roleCode, it) })
            }
        }
    }

    suspend fun bootstrapFirstAdmin(
        username: String,
        displayName: String,
        password: CharArray,
        now: Long = System.currentTimeMillis()
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

    suspend fun authenticate(
        username: String,
        password: CharArray,
        mfaCode: String? = null,
        now: Long = System.currentTimeMillis()
    ): AuthenticationResult {
        val normalized = username.trim()
        if (normalized.isBlank() || password.isEmpty()) return AuthenticationResult.Failure("اسم المستخدم أو كلمة المرور غير صحيحة")
        return db.withTransaction {
            val dao = db.userDao()
            var user = dao.byUsername(normalized)
                ?: return@withTransaction AuthenticationResult.Failure("اسم المستخدم أو كلمة المرور غير صحيحة")
            if (!user.isActive) return@withTransaction AuthenticationResult.Failure("الحساب غير نشط. راجع مدير النظام")
            val lockedUntil = user.lockedUntil
            if (lockedUntil != null && lockedUntil > now) {
                return@withTransaction AuthenticationResult.Locked(lockedUntil)
            }
            if (lockedUntil != null && lockedUntil <= now) {
                user = user.copy(failedLoginAttempts = 0, lockedUntil = null, updatedAt = now)
                dao.update(user)
            }

            if (!PasswordHasher.verify(password, user.salt, user.passwordHash)) {
                return@withTransaction registerFailedLogin(user, now, "BAD_CREDENTIALS")
            }

            val requireMfa = requiresMfaFor(user)
            if (requireMfa && !user.mfaEnabled) {
                audit(user.id, "MFA_ENROLLMENT_REQUIRED", "USER", user.id.toString())
                return@withTransaction AuthenticationResult.MfaSetupRequired(user.id)
            }

            if (user.mfaEnabled) {
                if (mfaCode.isNullOrBlank()) {
                    audit(user.id, "MFA_CHALLENGE_REQUIRED", "USER", user.id.toString())
                    return@withTransaction AuthenticationResult.MfaRequired(user.displayName)
                }
                val verified = verifyMfaCredential(user, password, mfaCode, now)
                if (!verified) return@withTransaction registerFailedLogin(user, now, "BAD_MFA")
            }

            val nextSessionVersion = user.sessionVersion + 1
            val updated = user.copy(
                failedLoginAttempts = 0,
                lockedUntil = null,
                lastLoginAt = now,
                mustChangePassword = user.mustChangePassword || PasswordPolicy.isExpired(user.passwordChangedAt, now),
                sessionVersion = nextSessionVersion,
                mfaVerifiedSessionVersion = if (user.mfaEnabled) nextSessionVersion else -1,
                updatedAt = now
            )
            dao.update(updated)
            recordRecentReauthentication(user.id, nextSessionVersion, now)
            audit(user.id, "LOGIN_SUCCESS", "USER", user.id.toString(), reason = if (user.mfaEnabled) "MFA_VERIFIED" else "PASSWORD_ONLY")
            AuthenticationResult.Success(updated)
        }
    }

    suspend fun beginMfaSetup(userId: Long, currentPassword: CharArray): MfaSetupData {
        val user = db.userDao().byId(userId) ?: throw IllegalArgumentException("المستخدم غير موجود")
        require(user.isActive) { "الحساب غير نشط" }
        require(PasswordHasher.verify(currentPassword, user.salt, user.passwordHash)) { "كلمة المرور الحالية غير صحيحة" }
        if (user.mfaEnabled) {
            require(isMfaSessionVerified(user)) { "يلزم تسجيل دخول MFA صالح قبل إعادة إعداد المصادقة الثنائية" }
        } else {
            require(requiresMfaFor(user)) { "لا يلزم إعداد MFA لهذا الحساب" }
        }
        val secret = Totp.newSecret()
        return MfaSetupData(secret, Totp.provisioningUri(user.username, secret))
    }

    suspend fun confirmMfaSetup(
        userId: Long,
        currentPassword: CharArray,
        secret: String,
        code: String,
        now: Long = System.currentTimeMillis()
    ): MfaEnrollmentResult = db.withTransaction {
        val user = db.userDao().byId(userId) ?: throw IllegalArgumentException("المستخدم غير موجود")
        require(user.isActive) { "الحساب غير نشط" }
        require(PasswordHasher.verify(currentPassword, user.salt, user.passwordHash)) { "كلمة المرور الحالية غير صحيحة" }
        if (user.mfaEnabled) require(isMfaSessionVerified(user)) { "يلزم تسجيل دخول MFA صالح قبل إعادة الإعداد" }
        else require(requiresMfaFor(user)) { "لا يلزم إعداد MFA لهذا الحساب" }
        require(Totp.verify(secret, code, now)) { "رمز التحقق غير صحيح. تأكد من وقت الهاتف وحاول مجددًا" }

        val recoveryCodes = MfaRecoveryCodes.generate()
        val recoveryRows = recoveryCodes.map { plain ->
            val salt = RecoveryCodeHasher.newSalt()
            MfaRecoveryCodeEntity(
                userId = user.id,
                codeHash = RecoveryCodeHasher.hash(plain, salt),
                salt = salt,
                createdAt = now
            )
        }
        val nextSessionVersion = user.sessionVersion + 1
        val updated = user.copy(
            mfaEnabled = true,
            mfaSecretCiphertext = MfaSecretCrypto.encrypt(secret, currentPassword),
            mfaConfirmedAt = now,
            mustChangePassword = user.mustChangePassword || PasswordPolicy.isExpired(user.passwordChangedAt, now),
            mfaVerifiedSessionVersion = nextSessionVersion,
            sessionVersion = nextSessionVersion,
            lastLoginAt = now,
            failedLoginAttempts = 0,
            lockedUntil = null,
            updatedAt = now
        )
        db.userDao().update(updated)
        recordRecentReauthentication(user.id, nextSessionVersion, now)
        db.securityDao().deleteMfaRecoveryCodes(user.id)
        db.securityDao().insertMfaRecoveryCodes(recoveryRows)
        audit(user.id, "MFA_ENABLED", "USER", user.id.toString())
        audit(user.id, "LOGIN_SUCCESS", "USER", user.id.toString(), reason = "MFA_ENROLLED")
        MfaEnrollmentResult(updated, recoveryCodes)
    }

    suspend fun permissionsFor(user: UserEntity): Set<String> {
        if (!user.isActive) return emptySet()
        if ((requiresMfaFor(user) || user.mfaEnabled) && !isMfaSessionVerified(user)) return emptySet()
        return if (user.role == "ADMIN") PermissionCatalog.permissions.map { it.code }.toSet()
        else db.securityDao().permissionCodesForRole(user.role).toSet()
    }

    suspend fun hasPermission(userId: Long, permissionCode: String): Boolean {
        val user = db.userDao().byId(userId) ?: return false
        if (!user.isActive) return false
        if ((requiresMfaFor(user) || user.mfaEnabled) && !isMfaSessionVerified(user)) return false
        if (user.role == "ADMIN") return true
        return db.securityDao().hasPermission(user.role, permissionCode) > 0
    }

    suspend fun requirePermission(userId: Long, permissionCode: String) {
        db.requireUserPermission(userId, permissionCode)
    }

    suspend fun hasRecentReauthentication(
        userId: Long,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val user = db.userDao().byId(userId) ?: return false
        if (!user.isActive) return false
        val stamp = synchronized(recentReauthentication) { recentReauthentication[userId] } ?: return false
        return stamp.sessionVersion == user.sessionVersion && ReauthenticationPolicy.isFresh(stamp.verifiedAt, now)
    }

    suspend fun requireRecentReauthentication(
        userId: Long,
        actionCode: String,
        now: Long = System.currentTimeMillis()
    ) {
        if (hasRecentReauthentication(userId, now)) return
        audit(userId, "REAUTH_REQUIRED", "SECURITY_ACTION", actionCode, reason = "FRESH_AUTH_REQUIRED")
        throw RecentAuthenticationRequiredException(
            "يلزم إعادة التحقق بكلمة المرور وMFA خلال آخر ${ReauthenticationPolicy.WINDOW_MINUTES} دقائق لهذه العملية"
        )
    }

    suspend fun reauthenticate(
        userId: Long,
        password: CharArray,
        mfaCode: String? = null,
        now: Long = System.currentTimeMillis()
    ): ReauthenticationResult = db.withTransaction {
        val dao = db.userDao()
        var user = dao.byId(userId) ?: return@withTransaction ReauthenticationResult.Failure("المستخدم غير موجود")
        if (!user.isActive) return@withTransaction ReauthenticationResult.Failure("الحساب غير نشط")
        val lockedUntil = user.lockedUntil
        if (lockedUntil != null && lockedUntil > now) return@withTransaction ReauthenticationResult.Locked(lockedUntil)
        if (lockedUntil != null && lockedUntil <= now) {
            user = user.copy(failedLoginAttempts = 0, lockedUntil = null, updatedAt = now)
            dao.update(user)
        }
        if (!PasswordHasher.verify(password, user.salt, user.passwordHash)) {
            return@withTransaction registerFailedReauthentication(user, now, "BAD_PASSWORD")
        }
        if (requiresMfaFor(user) || user.mfaEnabled) {
            if (!user.mfaEnabled) {
                audit(user.id, "REAUTH_FAILED", "USER", user.id.toString(), reason = "MFA_NOT_ENROLLED")
                return@withTransaction ReauthenticationResult.Failure("يجب إعداد MFA لهذا الحساب قبل تنفيذ العمليات الحساسة")
            }
            if (mfaCode.isNullOrBlank()) return@withTransaction ReauthenticationResult.MfaRequired
            if (!verifyMfaCredential(user, password, mfaCode, now)) {
                return@withTransaction registerFailedReauthentication(user, now, "BAD_MFA")
            }
        }
        if (user.failedLoginAttempts != 0 || user.lockedUntil != null) {
            dao.update(user.copy(failedLoginAttempts = 0, lockedUntil = null, updatedAt = now))
        }
        recordRecentReauthentication(user.id, user.sessionVersion, now)
        audit(user.id, "REAUTH_SUCCESS", "USER", user.id.toString(), reason = "CRITICAL_ACTION_WINDOW_OPENED")
        ReauthenticationResult.Success
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
        PasswordPolicy.validate(temporaryPassword, normalized)?.let { throw IllegalArgumentException(it) }
        val salt = PasswordHasher.newSalt()
        val hash = PasswordHasher.hash(temporaryPassword, salt)
        val now = System.currentTimeMillis()
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
        if (!active && target.role == "ADMIN" && target.isActive && db.userDao().activeAdminCount() <= 1) {
            throw IllegalStateException("لا يمكن تعطيل آخر مدير نظام نشط")
        }
        if (target.isActive == active) return@withTransaction
        val updated = target.copy(isActive = active, sessionVersion = target.sessionVersion + 1, mfaVerifiedSessionVersion = -1, updatedAt = System.currentTimeMillis())
        db.userDao().update(updated)
        audit(actorUserId, if (active) "USER_ENABLED" else "USER_DISABLED", "USER", targetUserId.toString())
    }

    suspend fun assignRole(actorUserId: Long, targetUserId: Long, roleCode: String) = db.withTransaction {
        requirePermission(actorUserId, SecurityPermissions.USERS_MANAGE)
        requireRecentReauthentication(actorUserId, "USER_ROLE_CHANGE")
        val role = db.securityDao().roleByCode(roleCode) ?: throw IllegalArgumentException("الدور غير موجود")
        require(role.isActive) { "الدور غير نشط" }
        val target = db.userDao().byId(targetUserId) ?: throw IllegalArgumentException("المستخدم غير موجود")
        if (target.role == "ADMIN" && roleCode != "ADMIN" && target.isActive && db.userDao().activeAdminCount() <= 1) {
            throw IllegalStateException("لا يمكن إزالة دور المدير من آخر مدير نظام نشط")
        }
        if (target.role == roleCode) return@withTransaction
        db.userDao().update(target.copy(role = roleCode, sessionVersion = target.sessionVersion + 1, mfaVerifiedSessionVersion = -1, updatedAt = System.currentTimeMillis()))
        audit(actorUserId, "USER_ROLE_CHANGED", "USER", targetUserId.toString(), oldValue = target.role, newValue = roleCode)
    }

    suspend fun resetMfa(actorUserId: Long, targetUserId: Long) = db.withTransaction {
        requirePermission(actorUserId, SecurityPermissions.USERS_MANAGE)
        requireRecentReauthentication(actorUserId, "MFA_RESET")
        require(actorUserId != targetUserId) { "لا يمكن تعطيل MFA لحسابك الحالي. استخدم رموز الاسترداد عند فقد تطبيق المصادقة" }
        val target = db.userDao().byId(targetUserId) ?: throw IllegalArgumentException("المستخدم غير موجود")
        if (!target.mfaEnabled && target.mfaSecretCiphertext == null) return@withTransaction
        db.userDao().update(target.copy(
            mfaEnabled = false,
            mfaSecretCiphertext = null,
            mfaConfirmedAt = null,
            mfaVerifiedSessionVersion = -1,
            sessionVersion = target.sessionVersion + 1,
            updatedAt = System.currentTimeMillis()
        ))
        db.securityDao().deleteMfaRecoveryCodes(target.id)
        audit(actorUserId, "MFA_RESET_BY_ADMIN", "USER", targetUserId.toString())
    }

    suspend fun resetPassword(actorUserId: Long, targetUserId: Long, temporaryPassword: CharArray) = db.withTransaction {
        requirePermission(actorUserId, SecurityPermissions.USERS_MANAGE)
        requireRecentReauthentication(actorUserId, "PASSWORD_RESET")
        val target = db.userDao().byId(targetUserId) ?: throw IllegalArgumentException("المستخدم غير موجود")
        PasswordPolicy.validate(temporaryPassword, target.username)?.let { throw IllegalArgumentException(it) }
        rejectPasswordReuse(target, temporaryPassword)
        ensureCurrentPasswordInHistory(target)
        val salt = PasswordHasher.newSalt()
        val hash = PasswordHasher.hash(temporaryPassword, salt)
        val now = System.currentTimeMillis()
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
        audit(actorUserId, "PASSWORD_RESET", "USER", targetUserId.toString(), reason = "ADMIN_RESET_MFA_CLEARED")
    }

    suspend fun changePassword(userId: Long, currentPassword: CharArray, newPassword: CharArray): UserEntity = db.withTransaction {
        val user = db.userDao().byId(userId) ?: throw IllegalArgumentException("المستخدم غير موجود")
        require(user.isActive) { "الحساب غير نشط" }
        require(PasswordHasher.verify(currentPassword, user.salt, user.passwordHash)) { "كلمة المرور الحالية غير صحيحة" }
        PasswordPolicy.validate(newPassword, user.username)?.let { throw IllegalArgumentException(it) }
        rejectPasswordReuse(user, newPassword)
        ensureCurrentPasswordInHistory(user)
        val reencryptedMfa = if (user.mfaEnabled) {
            val encrypted = user.mfaSecretCiphertext ?: throw IllegalStateException("بيانات MFA غير مكتملة")
            val secret = MfaSecretCrypto.decrypt(encrypted, currentPassword)
            MfaSecretCrypto.encrypt(secret, newPassword)
        } else null
        val salt = PasswordHasher.newSalt()
        val hash = PasswordHasher.hash(newPassword, salt)
        val now = System.currentTimeMillis()
        val nextSessionVersion = user.sessionVersion + 1
        val wasMfaVerified = user.mfaEnabled && user.mfaVerifiedSessionVersion == user.sessionVersion
        val updated = user.copy(
            passwordHash = hash,
            salt = salt,
            mustChangePassword = false,
            passwordChangedAt = now,
            sessionVersion = nextSessionVersion,
            mfaSecretCiphertext = reencryptedMfa,
            mfaVerifiedSessionVersion = if (wasMfaVerified) nextSessionVersion else -1,
            updatedAt = now
        )
        db.userDao().update(updated)
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
        val valid = db.securityDao().allPermissions().map { it.code }.toSet()
        require(permissionCodes.all { it in valid }) { "توجد صلاحية غير معروفة" }
        val finalCodes = if (role.code == "ADMIN") valid else permissionCodes
        db.securityDao().replaceRolePermissions(roleCode, finalCodes)
        db.userDao().invalidateSessionsForRole(roleCode, System.currentTimeMillis())
        audit(actorUserId, "ROLE_PERMISSIONS_CHANGED", "ROLE", roleCode, newValue = finalCodes.sorted().joinToString(","))
    }

    suspend fun saveCustomRole(actorUserId: Long, code: String, nameAr: String, description: String): RoleEntity = db.withTransaction {
        requirePermission(actorUserId, SecurityPermissions.ROLES_MANAGE)
        requireRecentReauthentication(actorUserId, "ROLE_CREATE_OR_UPDATE")
        val normalized = code.trim().uppercase()
        require(normalized.matches(Regex("[A-Z0-9_]{3,30}"))) { "رمز الدور يجب أن يكون أحرفًا إنجليزية كبيرة/أرقامًا/شرطة سفلية" }
        require(nameAr.trim().length >= 2) { "أدخل اسم الدور" }
        val existing = db.securityDao().roleByCode(normalized)
        require(existing == null || !existing.isSystem) { "لا يمكن تعديل دور نظام أساسي" }
        val now = System.currentTimeMillis()
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

    suspend fun requiresMfaFor(user: UserEntity): Boolean {
        if (user.role == "ADMIN") return true
        return MfaPolicy.privilegedPermissionCodes.any { db.securityDao().hasPermission(user.role, it) > 0 }
    }

    private fun isMfaSessionVerified(user: UserEntity): Boolean =
        user.mfaEnabled && user.mfaVerifiedSessionVersion == user.sessionVersion

    private suspend fun verifyMfaCredential(user: UserEntity, password: CharArray, code: String, now: Long): Boolean {
        val normalizedRecovery = MfaRecoveryCodes.normalize(code)
        val numeric = code.filter(Char::isDigit)
        if (numeric.length == MfaPolicy.TOTP_DIGITS) {
            val encrypted = user.mfaSecretCiphertext ?: return false
            val secret = runCatching { MfaSecretCrypto.decrypt(encrypted, password) }.getOrNull() ?: return false
            if (Totp.verify(secret, numeric, now)) {
                audit(user.id, "MFA_VERIFIED", "USER", user.id.toString(), reason = "TOTP")
                return true
            }
        }
        if (normalizedRecovery.length >= 10) {
            for (row in db.securityDao().unusedMfaRecoveryCodes(user.id)) {
                if (RecoveryCodeHasher.verify(normalizedRecovery, row.salt, row.codeHash)) {
                    if (db.securityDao().markMfaRecoveryCodeUsed(row.id, now) == 1) {
                        audit(user.id, "MFA_RECOVERY_CODE_USED", "MFA_RECOVERY_CODE", row.id.toString())
                        return true
                    }
                }
            }
        }
        return false
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
            ?: ReauthenticationResult.Failure(
                if (reason == "BAD_MFA") "رمز التحقق الثنائي غير صحيح" else "كلمة المرور الحالية غير صحيحة"
            )
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
        audit(user.id, if (reason == "BAD_MFA") "MFA_FAILED" else "LOGIN_FAILED", "USER", user.id.toString(), reason = if (decision.lockedUntil != null) "ACCOUNT_LOCKED_$reason" else reason)
        return decision.lockedUntil?.let { AuthenticationResult.Locked(it) }
            ?: AuthenticationResult.Failure(if (reason == "BAD_MFA") "رمز التحقق الثنائي غير صحيح" else "اسم المستخدم أو كلمة المرور غير صحيحة")
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
