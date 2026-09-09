package com.fush.erp.domain

import android.content.Context
import android.provider.Settings
import androidx.room.withTransaction
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.AuditEventEntity
import com.fush.erp.data.entity.UserEntity
import com.fush.erp.data.entity.UserPasswordHistoryEntity
import com.fush.erp.data.entity.VendorSupportIdentityEntity
import com.fush.erp.data.entity.VendorSupportKeyEntity
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID

object VendorLifecycleAction {
    const val PROVISION = "PROVISION"
    const val REBIND = "REBIND"
    const val ROTATE = "ROTATE"
    const val LEGACY_CLAIM = "LEGACY_CLAIM"
    const val KEY_ROTATE = "KEY_ROTATE"

    val identityActions = setOf(PROVISION, REBIND, ROTATE, LEGACY_CLAIM)
}

data class VendorProvisioningChallenge(
    val purpose: String,
    val installationBinding: String,
    val challenge: String,
    val issuedAt: Long,
    val previousPackageFingerprint: String,
    val targetSupportUserId: Long,
    val targetUsername: String,
    val nextCredentialVersion: Long,
    val currentKeyId: String,
) {
    fun requestToken(): String {
        val usernameB64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(targetUsername.toByteArray(StandardCharsets.UTF_8))
        val previous = previousPackageFingerprint.ifBlank { "-" }
        return listOf(
            "FUSH-PROVISION-REQUEST", "2", purpose, installationBinding, challenge,
            issuedAt.toString(), previous, targetSupportUserId.toString(), usernameB64,
            nextCredentialVersion.toString(), currentKeyId,
        ).joinToString("|")
    }
}

data class VendorLifecycleState(
    val installationBinding: String,
    val latestIdentity: VendorSupportIdentityEntity?,
    val legacySupportUsers: List<UserEntity>,
) {
    val current: Boolean get() = latestIdentity?.installationBinding == installationBinding
    val requiresRebind: Boolean get() = latestIdentity != null && !current
    val requiresLegacyReview: Boolean get() = latestIdentity == null && legacySupportUsers.isNotEmpty()
    val fresh: Boolean get() = latestIdentity == null && legacySupportUsers.isEmpty()
}

object VendorSupportProvisioningPolicy {
    const val BOOTSTRAP_KEY_ID = "fush-support-v1"
    const val SUPPORT_USERNAME = "fush.support"
    const val CHALLENGE_TTL_MS = 24L * 60L * 60L * 1000L
    const val PACKAGE_TTL_MS = 24L * 60L * 60L * 1000L
    const val CLOCK_SKEW_MS = 5L * 60L * 1000L
    const val BOOTSTRAP_PUBLIC_KEY_DER_BASE64 = "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAoBTU+YWehQim6KIvOBZH8mWkBynMLNyPCxBZIu14NnOA9TjD0GwMX/wOnGj1nwaxyi4VZeqK9pEg7dCgiXRCxUbvfzzeyWlYRNgcY6IoXDUoYHdPEug0DWuAck+BssHErkY2tz2tctYILtyXRjUJwb4NJjwoe6OMXeL3lJtqkRrL1TFHWABo/hKkZh8dQg3EWT7Ld78NAZy1volp6esj26JhqRR/fhI4tuBZCkhVVtMsi+B8r0BoJvp+aW4k8hbwZpNQL6+LN4oLpVuBHqlwZsg9vIPnbZKp7E1oB1+SFngIwZ/9rVV5E+JGLNLKQX4fEGNkP0lKPEcPjbfOXcsXSAh1n7ChDX/VqtR6pbEMznAHUhGd1bEQTpo6ZayGwug4evoZrEMmocw71X4TV9Duqg34tXc8XuRtDfYBRPFX+gG3SthmIw2XQckqBmGPt/nIBF1jE+rTANlnC1LcBAPRmdf+pXw0TvME+mS2g9DaKoRl2U3Ob3C8eqA3g7hjXehZAgMBAAE="
}

class VendorInstallationIdentityStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("fush_vendor_installation_identity", Context.MODE_PRIVATE)
    private val random = SecureRandom()

    private fun installSecret(): String {
        prefs.getString("install_secret", null)?.let { return it }
        val bytes = ByteArray(32).also(random::nextBytes)
        val value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        check(prefs.edit().putString("install_secret", value).commit()) { "تعذر تثبيت هوية الجهاز" }
        return value
    }

    fun installationBinding(): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        val material = "FUSH-INSTALL-v1|${context.packageName}|$androidId|${installSecret()}"
        return sha256Hex(material.toByteArray(StandardCharsets.UTF_8))
    }

    fun createChallenge(
        purpose: String,
        previousPackageFingerprint: String,
        targetSupportUserId: Long,
        targetUsername: String,
        nextCredentialVersion: Long,
        currentKeyId: String,
        now: Long,
    ): VendorProvisioningChallenge {
        val bytes = ByteArray(32).also(random::nextBytes)
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val ok = prefs.edit()
            .putString("challenge", challenge)
            .putLong("challenge_at", now)
            .putString("challenge_purpose", purpose)
            .putString("challenge_previous_fingerprint", previousPackageFingerprint)
            .putLong("challenge_target_user", targetSupportUserId)
            .putString("challenge_target_username", targetUsername)
            .putLong("challenge_next_version", nextCredentialVersion)
            .putString("challenge_key_id", currentKeyId)
            .commit()
        check(ok) { "تعذر حفظ Challenge" }
        return currentChallenge() ?: error("تعذر قراءة Challenge")
    }

    fun currentChallenge(): VendorProvisioningChallenge? {
        val value = prefs.getString("challenge", null) ?: return null
        val at = prefs.getLong("challenge_at", 0L)
        val purpose = prefs.getString("challenge_purpose", null) ?: return null
        val username = prefs.getString("challenge_target_username", null) ?: return null
        val keyId = prefs.getString("challenge_key_id", null) ?: return null
        if (at <= 0L) return null
        return VendorProvisioningChallenge(
            purpose = purpose,
            installationBinding = installationBinding(),
            challenge = value,
            issuedAt = at,
            previousPackageFingerprint = prefs.getString("challenge_previous_fingerprint", "").orEmpty(),
            targetSupportUserId = prefs.getLong("challenge_target_user", 0L),
            targetUsername = username,
            nextCredentialVersion = prefs.getLong("challenge_next_version", 1L),
            currentKeyId = keyId,
        )
    }

    fun consumeChallenge(expected: String) {
        require(prefs.getString("challenge", null) == expected) { "Challenge غير مطابق" }
        check(
            prefs.edit()
                .remove("challenge")
                .remove("challenge_at")
                .remove("challenge_purpose")
                .remove("challenge_previous_fingerprint")
                .remove("challenge_target_user")
                .remove("challenge_target_username")
                .remove("challenge_next_version")
                .remove("challenge_key_id")
                .commit()
        ) { "تعذر استهلاك Challenge" }
    }
}

class VendorSupportProvisioningManager(
    private val db: FushDatabase,
    private val installationStore: VendorInstallationIdentityStore,
) {
    fun installationBinding(): String = installationStore.installationBinding()

    suspend fun lifecycleState(): VendorLifecycleState = VendorLifecycleState(
        installationBinding = installationBinding(),
        latestIdentity = db.supportDao().latestVendorIdentity(),
        legacySupportUsers = db.supportDao().legacySupportUsers(),
    )

    suspend fun isCurrentVendorIdentity(supportUserId: Long): Boolean {
        val latest = db.supportDao().latestVendorIdentity() ?: return false
        return latest.supportUserId == supportUserId && latest.installationBinding == installationBinding()
    }

    suspend fun isProvisionedForThisInstallation(): Boolean =
        db.supportDao().vendorIdentityForInstallation(installationBinding()) != null

    suspend fun createChallenge(
        requestedPurpose: String? = null,
        now: Long = TrustedTimeService.now(),
    ): VendorProvisioningChallenge {
        TrustedTimeService.failureReason()?.let { throw ClockTamperDetectedException(it) }
        val state = lifecycleState()
        val trustedKey = currentTrustedKey()
        val purpose: String
        val previous: String
        val targetId: Long
        val targetUsername: String
        val nextVersion: Long

        when (requestedPurpose) {
            VendorLifecycleAction.ROTATE -> {
                val latest = requireNotNull(state.latestIdentity) { "لا توجد Vendor Identity لتدوير بيانات اعتمادها" }
                require(state.current) { "يجب تنفيذ Signed Rebind على هذا الجهاز قبل تدوير البيانات" }
                val user = requireNotNull(db.userDao().byId(latest.supportUserId)) { "حساب Vendor Support غير موجود" }
                purpose = VendorLifecycleAction.ROTATE
                previous = latest.packageFingerprint
                targetId = user.id
                targetUsername = user.username
                nextVersion = latest.credentialVersion + 1
            }
            VendorLifecycleAction.KEY_ROTATE -> {
                val latest = requireNotNull(state.latestIdentity) { "لا توجد Vendor Identity موثقة لتدوير مفتاح FUSH" }
                require(state.current) { "يجب تنفيذ Signed Rebind على هذا الجهاز قبل تدوير المفتاح" }
                val user = requireNotNull(db.userDao().byId(latest.supportUserId)) { "حساب Vendor Support غير موجود" }
                purpose = VendorLifecycleAction.KEY_ROTATE
                previous = latest.packageFingerprint
                targetId = user.id
                targetUsername = user.username
                nextVersion = latest.credentialVersion
            }
            null -> {
                when {
                    state.fresh -> {
                        purpose = VendorLifecycleAction.PROVISION
                        previous = ""
                        targetId = 0L
                        targetUsername = VendorSupportProvisioningPolicy.SUPPORT_USERNAME
                        nextVersion = 1L
                    }
                    state.requiresLegacyReview -> {
                        require(state.legacySupportUsers.size == 1) {
                            "يوجد أكثر من حساب Legacy FUSH_SUPPORT؛ الوصول موقوف ويحتاج مراجعة Vendor يدوية"
                        }
                        val legacy = state.legacySupportUsers.single()
                        purpose = VendorLifecycleAction.LEGACY_CLAIM
                        previous = ""
                        targetId = legacy.id
                        targetUsername = legacy.username
                        nextVersion = 1L
                    }
                    state.requiresRebind -> {
                        val latest = requireNotNull(state.latestIdentity)
                        val user = requireNotNull(db.userDao().byId(latest.supportUserId)) { "حساب Vendor Support التاريخي غير موجود" }
                        purpose = VendorLifecycleAction.REBIND
                        previous = latest.packageFingerprint
                        targetId = user.id
                        targetUsername = user.username
                        nextVersion = latest.credentialVersion + 1
                    }
                    else -> error("Vendor Identity موثقة لهذا التثبيت؛ استخدم Signed Credential Rotation عند الحاجة")
                }
            }
            else -> error("Vendor lifecycle action غير مدعومة")
        }

        return installationStore.createChallenge(
            purpose = purpose,
            previousPackageFingerprint = previous,
            targetSupportUserId = targetId,
            targetUsername = targetUsername,
            nextCredentialVersion = nextVersion,
            currentKeyId = trustedKey.keyId,
            now = now,
        )
    }

    suspend fun provision(actorUserId: Long, token: String, now: Long = TrustedTimeService.now()): Long {
        val purpose = installationStore.currentChallenge()?.purpose ?: "UNKNOWN"
        return try {
            provisionInternal(actorUserId, token, now)
        } catch (t: Throwable) {
            recordProvisionFailure(actorUserId, purpose, provisioningFailureCode(t), now)
            throw t
        }
    }

    private suspend fun provisionInternal(actorUserId: Long, token: String, now: Long): Long {
        TrustedTimeService.failureReason()?.let { throw ClockTamperDetectedException(it) }
        val trustedKey = currentTrustedKey()
        val verified = verifyIdentityToken(token, trustedKey, now)
        val challenge = installationStore.currentChallenge() ?: error("أنشئ Vendor Lifecycle Challenge جديدًا أولًا")
        require(now - challenge.issuedAt in 0..VendorSupportProvisioningPolicy.CHALLENGE_TTL_MS) { "Provisioning Challenge منتهي" }
        require(verified.action == challenge.purpose) { "Lifecycle action لا تطابق Challenge" }
        require(verified.installationBinding == challenge.installationBinding) { "الحزمة ليست لهذا التثبيت" }
        require(verified.challenge == challenge.challenge) { "الحزمة لا تطابق Challenge الحالي" }
        require(verified.previousPackageFingerprint == challenge.previousPackageFingerprint) { "سلسلة Supersession غير مطابقة" }
        require(verified.targetSupportUserId == challenge.targetSupportUserId) { "Target Support User غير مطابق" }
        require(verified.username == challenge.targetUsername) { "Vendor username غير مطابق" }
        require(verified.credentialVersion == challenge.nextCredentialVersion) { "Credential version غير مطابقة" }
        require(verified.keyId == trustedKey.keyId) { "Vendor key غير معتمد" }

        val latest = db.supportDao().latestVendorIdentity()
        val userId = db.withTransaction {
            when (verified.action) {
                VendorLifecycleAction.PROVISION -> {
                    require(latest == null) { "Fresh provisioning غير مسموح مع وجود تاريخ Vendor Identity" }
                    require(db.supportDao().legacySupportUsers().isEmpty()) { "يوجد Legacy FUSH_SUPPORT ويجب مراجعته بدل إنشاء حساب جديد" }
                    require(verified.targetSupportUserId == 0L) { "Fresh target يجب أن يكون جديدًا" }
                    require(db.userDao().byUsername(verified.username) == null) { "اسم Vendor Support مستخدم مسبقًا" }
                    val id = db.userDao().insert(newSupportUser(verified, now))
                    db.securityDao().insertPasswordHistory(UserPasswordHistoryEntity(userId = id, passwordHash = verified.passwordHash, salt = verified.salt, createdAt = now))
                    insertIdentityEvent(id, actorUserId, verified, null, now)
                    id
                }
                VendorLifecycleAction.LEGACY_CLAIM -> {
                    require(latest == null) { "Legacy Claim غير مسموح بعد وجود Vendor Identity" }
                    val legacy = requireNotNull(db.userDao().byId(verified.targetSupportUserId)) { "حساب Legacy غير موجود" }
                    require(legacy.role == SupportPolicy.SUPPORT_ROLE) { "الحساب Legacy ليس FUSH_SUPPORT" }
                    require(legacy.username == verified.username) { "Legacy username تغير أثناء المراجعة" }
                    applySignedCredentials(legacy, verified, now)
                    insertIdentityEvent(legacy.id, actorUserId, verified, null, now)
                    legacy.id
                }
                VendorLifecycleAction.REBIND -> {
                    val previous = requireNotNull(latest) { "لا توجد هوية تاريخية لإعادة الربط" }
                    require(previous.installationBinding != installationBinding()) { "الهوية مرتبطة بهذا التثبيت بالفعل" }
                    require(previous.packageFingerprint == verified.previousPackageFingerprint) { "هوية Rebind السابقة غير مطابقة" }
                    require(previous.supportUserId == verified.targetSupportUserId) { "حساب Rebind غير مطابق" }
                    val user = requireNotNull(db.userDao().byId(previous.supportUserId)) { "حساب Vendor Support غير موجود" }
                    require(user.username == verified.username && user.role == SupportPolicy.SUPPORT_ROLE) { "حساب Vendor Support تغير" }
                    applySignedCredentials(user, verified, now)
                    insertIdentityEvent(user.id, actorUserId, verified, previous, now)
                    user.id
                }
                VendorLifecycleAction.ROTATE -> {
                    val previous = requireNotNull(latest) { "لا توجد هوية لتدويرها" }
                    require(previous.installationBinding == installationBinding()) { "Credential Rotation تتطلب الهوية الحالية لهذا التثبيت" }
                    require(previous.packageFingerprint == verified.previousPackageFingerprint) { "هوية Supersession السابقة غير مطابقة" }
                    require(previous.supportUserId == verified.targetSupportUserId) { "حساب Rotation غير مطابق" }
                    val user = requireNotNull(db.userDao().byId(previous.supportUserId)) { "حساب Vendor Support غير موجود" }
                    require(user.username == verified.username && user.role == SupportPolicy.SUPPORT_ROLE) { "حساب Vendor Support تغير" }
                    applySignedCredentials(user, verified, now)
                    insertIdentityEvent(user.id, actorUserId, verified, previous, now)
                    user.id
                }
                else -> error("Lifecycle package غير مدعومة")
            }
        }
        installationStore.consumeChallenge(verified.challenge)
        return userId
    }

    suspend fun rotateSigningKey(actorUserId: Long, token: String, now: Long = TrustedTimeService.now()): String {
        return try {
            rotateSigningKeyInternal(actorUserId, token, now)
        } catch (t: Throwable) {
            recordKeyRotationFailure(actorUserId, provisioningFailureCode(t), now)
            throw t
        }
    }

    private suspend fun rotateSigningKeyInternal(actorUserId: Long, token: String, now: Long): String {
        TrustedTimeService.failureReason()?.let { throw ClockTamperDetectedException(it) }
        val challenge = installationStore.currentChallenge() ?: error("أنشئ Key Rotation Challenge أولًا")
        require(challenge.purpose == VendorLifecycleAction.KEY_ROTATE) { "Challenge ليست لتدوير المفتاح" }
        require(now - challenge.issuedAt in 0..VendorSupportProvisioningPolicy.CHALLENGE_TTL_MS) { "Key Rotation Challenge منتهي" }
        val currentKey = currentTrustedKey()
        val verified = verifyKeyRotationToken(token, currentKey, now)
        require(verified.installationBinding == challenge.installationBinding) { "حزمة المفتاح ليست لهذا التثبيت" }
        require(verified.challenge == challenge.challenge) { "حزمة المفتاح لا تطابق Challenge" }
        require(verified.supersedesKeyId == currentKey.keyId) { "المفتاح السابق غير مطابق لسلسلة الثقة" }
        require(db.supportDao().vendorSupportKeyCount(verified.newKeyId) == 0) { "Key ID مستخدم مسبقًا" }

        db.supportDao().insertVendorSupportKey(
            VendorSupportKeyEntity(
                keyId = verified.newKeyId,
                publicKeyDerBase64 = verified.newPublicKeyDerBase64,
                supersedesKeyId = currentKey.keyId,
                packageFingerprint = sha256Hex(token.toByteArray(StandardCharsets.UTF_8)),
                rotatedBy = actorUserId,
                activatedAt = now,
            )
        )
        installationStore.consumeChallenge(verified.challenge)
        return verified.newKeyId
    }

    private fun newSupportUser(verified: VerifiedIdentityPackage, now: Long): UserEntity = UserEntity(
        username = verified.username,
        displayName = verified.displayName,
        passwordHash = verified.passwordHash,
        salt = verified.salt,
        role = SupportPolicy.SUPPORT_ROLE,
        isActive = true,
        mustChangePassword = false,
        passwordChangedAt = now,
        sessionVersion = 1,
        createdAt = now,
        updatedAt = now,
    )

    private suspend fun applySignedCredentials(user: UserEntity, verified: VerifiedIdentityPackage, now: Long) {
        db.userDao().update(
            user.copy(
                displayName = verified.displayName,
                passwordHash = verified.passwordHash,
                salt = verified.salt,
                isActive = true,
                mustChangePassword = false,
                failedLoginAttempts = 0,
                lockoutCount = 0,
                lockedUntil = null,
                passwordChangedAt = now,
                sessionVersion = user.sessionVersion + 1,
                mfaEnabled = false,
                mfaSecretCiphertext = null,
                mfaConfirmedAt = null,
                mfaVerifiedSessionVersion = -1,
                updatedAt = now,
            )
        )
        db.securityDao().insertPasswordHistory(
            UserPasswordHistoryEntity(userId = user.id, passwordHash = verified.passwordHash, salt = verified.salt, createdAt = now)
        )
    }

    private suspend fun insertIdentityEvent(
        supportUserId: Long,
        actorUserId: Long,
        verified: VerifiedIdentityPackage,
        previous: VendorSupportIdentityEntity?,
        now: Long,
    ) {
        db.supportDao().insertVendorSupportIdentity(
            VendorSupportIdentityEntity(
                supportUserId = supportUserId,
                installationBinding = verified.installationBinding,
                keyId = verified.keyId,
                packageNonce = verified.nonce,
                packageFingerprint = sha256Hex(verified.rawToken.toByteArray(StandardCharsets.UTF_8)),
                provisionedBy = actorUserId,
                provisionedAt = now,
                lifecycleAction = verified.action,
                credentialVersion = verified.credentialVersion,
                supersedesIdentityId = previous?.id,
                previousPackageFingerprint = verified.previousPackageFingerprint,
            )
        )
    }

    private data class TrustedVendorKey(val keyId: String, val publicKeyDerBase64: String)

    private suspend fun currentTrustedKey(): TrustedVendorKey {
        val rotated = db.supportDao().latestVendorSupportKey()
        return if (rotated != null) {
            TrustedVendorKey(rotated.keyId, rotated.publicKeyDerBase64)
        } else {
            TrustedVendorKey(VendorSupportProvisioningPolicy.BOOTSTRAP_KEY_ID, VendorSupportProvisioningPolicy.BOOTSTRAP_PUBLIC_KEY_DER_BASE64)
        }
    }

    internal data class VerifiedIdentityPackage(
        val rawToken: String,
        val action: String,
        val keyId: String,
        val installationBinding: String,
        val challenge: String,
        val previousPackageFingerprint: String,
        val targetSupportUserId: Long,
        val username: String,
        val credentialVersion: Long,
        val nonce: String,
        val displayName: String,
        val passwordHash: String,
        val salt: String,
        val issuedAt: Long,
        val expiresAt: Long,
    )

    private fun verifyIdentityToken(token: String, trustedKey: TrustedVendorKey, now: Long): VerifiedIdentityPackage {
        val (payload, map) = verifySignedPayload(token, "FSP2", trustedKey)
        require(map["version"] == "2") { "Provisioning version غير مدعوم" }
        val action = map.getValue("action")
        require(action in VendorLifecycleAction.identityActions) { "Lifecycle action غير مدعومة" }
        require(map["keyId"] == trustedKey.keyId) { "Vendor key غير معتمد" }
        val issuedAt = map.getValue("issuedAt").toLong()
        val expiresAt = map.getValue("expiresAt").toLong()
        validatePackageWindow(issuedAt, expiresAt, now)
        val username = String(Base64.getUrlDecoder().decode(map.getValue("usernameB64")), StandardCharsets.UTF_8).trim()
        require(username.matches(Regex("[A-Za-z0-9._-]{3,40}"))) { "Vendor username غير صالح" }
        val displayName = String(Base64.getUrlDecoder().decode(map.getValue("displayNameB64")), StandardCharsets.UTF_8).trim()
        require(displayName.length in 2..80) { "اسم Vendor غير صالح" }
        val salt = map.getValue("salt")
        val passwordHash = map.getValue("passwordHash")
        require(runCatching { Base64.getDecoder().decode(salt).size == 16 }.getOrDefault(false)) { "Salt غير صالح" }
        require(runCatching { Base64.getDecoder().decode(passwordHash).size == 32 }.getOrDefault(false)) { "Password hash غير صالح" }
        val nonce = map.getValue("nonce")
        require(runCatching { UUID.fromString(nonce) }.isSuccess) { "Nonce غير صالح" }
        val previous = map.getValue("previousPackageFingerprint").let { if (it == "-") "" else it }
        val version = map.getValue("credentialVersion").toLong()
        require(version >= 1L) { "Credential version غير صالحة" }
        return VerifiedIdentityPackage(
            rawToken = token.trim(),
            action = action,
            keyId = map.getValue("keyId"),
            installationBinding = map.getValue("installationBinding"),
            challenge = map.getValue("challenge"),
            previousPackageFingerprint = previous,
            targetSupportUserId = map.getValue("targetSupportUserId").toLong(),
            username = username,
            credentialVersion = version,
            nonce = nonce,
            displayName = displayName,
            passwordHash = passwordHash,
            salt = salt,
            issuedAt = issuedAt,
            expiresAt = expiresAt,
        )
    }

    private data class VerifiedKeyRotationPackage(
        val installationBinding: String,
        val challenge: String,
        val supersedesKeyId: String,
        val newKeyId: String,
        val newPublicKeyDerBase64: String,
    )

    private fun verifyKeyRotationToken(token: String, trustedKey: TrustedVendorKey, now: Long): VerifiedKeyRotationPackage {
        val (_, map) = verifySignedPayload(token, "FSK1", trustedKey)
        require(map["version"] == "1") { "Key rotation version غير مدعوم" }
        require(map["keyId"] == trustedKey.keyId) { "Signing key غير مطابق للمفتاح الحالي" }
        val issuedAt = map.getValue("issuedAt").toLong()
        val expiresAt = map.getValue("expiresAt").toLong()
        validatePackageWindow(issuedAt, expiresAt, now)
        val newKeyId = map.getValue("newKeyId")
        require(newKeyId.matches(Regex("[A-Za-z0-9._-]{5,64}"))) { "New Key ID غير صالح" }
        require(newKeyId != trustedKey.keyId) { "المفتاح الجديد يجب أن يختلف عن الحالي" }
        val newDer = map.getValue("newPublicKeyDerBase64")
        val key = parseRsaPublicKey(newDer)
        require(key.modulus.bitLength() >= 3072) { "Vendor key يجب أن تكون RSA 3072-bit على الأقل" }
        val nonce = map.getValue("nonce")
        require(runCatching { UUID.fromString(nonce) }.isSuccess) { "Nonce غير صالح" }
        return VerifiedKeyRotationPackage(
            installationBinding = map.getValue("installationBinding"),
            challenge = map.getValue("challenge"),
            supersedesKeyId = map.getValue("supersedesKeyId"),
            newKeyId = newKeyId,
            newPublicKeyDerBase64 = newDer,
        )
    }

    private fun verifySignedPayload(token: String, prefix: String, trustedKey: TrustedVendorKey): Pair<ByteArray, Map<String, String>> {
        val parts = token.trim().split('.')
        require(parts.size == 3 && parts[0] == prefix) { "صيغة Signed Vendor Package غير صحيحة" }
        val payload = Base64.getUrlDecoder().decode(parts[1])
        val signatureBytes = Base64.getUrlDecoder().decode(parts[2])
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(parseRsaPublicKey(trustedKey.publicKeyDerBase64))
        verifier.update(payload)
        require(verifier.verify(signatureBytes)) { "توقيع FUSH Vendor غير صالح" }
        val map = payload.toString(StandardCharsets.UTF_8).lineSequence()
            .filter { it.isNotBlank() }
            .associate { line ->
                val i = line.indexOf('=')
                require(i > 0) { "Payload غير صالح" }
                line.substring(0, i) to line.substring(i + 1)
            }
        return payload to map
    }

    private fun parseRsaPublicKey(base64: String): RSAPublicKey = KeyFactory.getInstance("RSA")
        .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(base64))) as RSAPublicKey

    private fun validatePackageWindow(issuedAt: Long, expiresAt: Long, now: Long) {
        require(issuedAt <= now + VendorSupportProvisioningPolicy.CLOCK_SKEW_MS) { "وقت إصدار الحزمة في المستقبل" }
        require(expiresAt >= now) { "Signed Vendor Package منتهية" }
        require(expiresAt > issuedAt && expiresAt - issuedAt <= VendorSupportProvisioningPolicy.PACKAGE_TTL_MS) { "مدة الحزمة غير مسموحة" }
    }

    private suspend fun recordProvisionFailure(actorUserId: Long, purpose: String, code: String, now: Long) {
        runCatching {
            db.governanceDao().insertAudit(
                AuditEventEntity(
                    eventAt = now,
                    userId = actorUserId,
                    action = "VENDOR_SUPPORT_PROVISION_FAILED",
                    entityType = "VENDOR_SUPPORT",
                    entityId = "LIFECYCLE",
                    reason = "code=$code;purpose=${sanitizePurpose(purpose)}",
                )
            )
        }
    }

    private suspend fun recordKeyRotationFailure(actorUserId: Long, code: String, now: Long) {
        runCatching {
            db.governanceDao().insertAudit(
                AuditEventEntity(
                    eventAt = now,
                    userId = actorUserId,
                    action = "VENDOR_SUPPORT_KEY_ROTATION_FAILED",
                    entityType = "VENDOR_SUPPORT_KEY",
                    entityId = "TRUST_CHAIN",
                    reason = "code=$code",
                )
            )
        }
    }

    private fun sanitizePurpose(purpose: String): String = when (purpose) {
        VendorLifecycleAction.PROVISION,
        VendorLifecycleAction.REBIND,
        VendorLifecycleAction.ROTATE,
        VendorLifecycleAction.LEGACY_CLAIM,
        VendorLifecycleAction.KEY_ROTATE -> purpose
        else -> "UNKNOWN"
    }

    internal fun provisioningFailureCode(t: Throwable): String {
        val m = t.message.orEmpty()
        return when {
            t is ClockTamperDetectedException -> "TRUSTED_TIME_REQUIRED"
            "توقيع" in m -> "SIGNATURE_INVALID"
            "منته" in m -> "PACKAGE_OR_CHALLENGE_EXPIRED"
            "Challenge" in m || "challenge" in m -> "CHALLENGE_MISMATCH"
            "Lifecycle" in m || "lifecycle" in m -> "LIFECYCLE_MISMATCH"
            "Supersession" in m || "السابقة" in m -> "SUPERSESSION_MISMATCH"
            "Legacy" in m -> "LEGACY_REVIEW_REQUIRED"
            "Key ID" in m || "Vendor key" in m || "Signing key" in m -> "KEY_MISMATCH"
            "username" in m || "اسم Vendor" in m -> "IDENTITY_MISMATCH"
            else -> "PACKAGE_REJECTED"
        }
    }
}

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
