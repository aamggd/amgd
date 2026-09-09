package com.fush.erp.domain

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.fush.erp.cloud.CloudOperationResult
import com.fush.erp.cloud.CommercialLicenseRemoteService
import com.fush.erp.cloud.CommercialLicenseSnapshot
import com.fush.erp.data.entity.UserEntity
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Effective commercial state used by the UI. */
enum class CommercialLicenseMode {
    ACTIVE,
    TRIAL,
    OFFLINE_GRACE,
    READ_ONLY,
    UNLICENSED,
}

data class CommercialLicenseAccess(
    val mode: CommercialLicenseMode,
    val snapshot: CommercialLicenseSnapshot?,
    val canWrite: Boolean,
    val message: String,
)

/**
 * Encrypted local cache for the last server-verified entitlement.
 * Raw activation codes are deliberately never persisted.
 */
internal class CommercialLicenseStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun load(): CommercialLicenseSnapshot? = runCatching {
        val raw = prefs.getString(KEY_SNAPSHOT, null) ?: return null
        val json = JSONObject(decrypt(raw))
        CommercialLicenseSnapshot(
            organizationId = json.getString("organization_id"),
            licenseId = json.getString("license_id"),
            deviceEntitlementId = json.optString("device_entitlement_id").takeIf { it.isNotBlank() },
            planCode = json.optString("plan_code", "STANDARD"),
            status = json.optString("status", "UNLICENSED"),
            isTrial = json.optBoolean("is_trial", false),
            startsAt = json.optLong("starts_at", 0L),
            expiresAt = json.optLong("expires_at", 0L),
            offlineGraceUntil = json.optLong("offline_grace_until", 0L),
            maxDevices = json.optInt("max_devices", 1).coerceAtLeast(1),
            verifiedAt = json.optLong("verified_at", 0L),
            serverTime = json.optLong("server_time", 0L),
        )
    }.getOrElse {
        prefs.edit().remove(KEY_SNAPSHOT).apply()
        null
    }

    fun save(snapshot: CommercialLicenseSnapshot) {
        val json = JSONObject()
            .put("organization_id", snapshot.organizationId)
            .put("license_id", snapshot.licenseId)
            .put("device_entitlement_id", snapshot.deviceEntitlementId)
            .put("plan_code", snapshot.planCode)
            .put("status", snapshot.status)
            .put("is_trial", snapshot.isTrial)
            .put("starts_at", snapshot.startsAt)
            .put("expires_at", snapshot.expiresAt)
            .put("offline_grace_until", snapshot.offlineGraceUntil)
            .put("max_devices", snapshot.maxDevices)
            .put("verified_at", snapshot.verifiedAt)
            .put("server_time", snapshot.serverTime)
            .toString()
        val floor = maxOf(snapshot.serverTime, snapshot.verifiedAt, System.currentTimeMillis())
        prefs.edit()
            .putString(KEY_SNAPSHOT, encrypt(json))
            .putLong(KEY_TIME_FLOOR, floor)
            .apply()
    }

    /** Never allow the effective offline clock to move backwards between app launches. */
    fun trustedNow(): Long {
        val wall = System.currentTimeMillis()
        val previousFloor = prefs.getLong(KEY_TIME_FLOOR, 0L)
        val now = maxOf(wall, previousFloor)
        if (now > previousFloor) prefs.edit().putLong(KEY_TIME_FLOOR, now).apply()
        return now
    }

    fun clear() = prefs.edit().remove(KEY_SNAPSHOT).remove(KEY_TIME_FLOOR).apply()

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val encrypted = Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        return "$iv:$encrypted"
    }

    private fun decrypt(value: String): String {
        val parts = value.split(':', limit = 2)
        require(parts.size == 2) { "Invalid encrypted license snapshot" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS = "fush_commercial_license_v1"
        const val KEY_SNAPSHOT = "license_snapshot"
        const val KEY_TIME_FLOOR = "trusted_time_floor"
        const val KEY_ALIAS = "fush_commercial_license_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

/**
 * v214 license coordinator.
 * Supabase is the authority. Offline decisions use only a previously server-verified snapshot.
 */
class CommercialLicenseManager(
    context: Context,
    private val remote: CommercialLicenseRemoteService,
) {
    private val store = CommercialLicenseStore(context.applicationContext)

    fun currentSnapshot(): CommercialLicenseSnapshot? = store.load()

    fun currentAccess(): CommercialLicenseAccess {
        val now = store.trustedNow()
        val snapshot = store.load()
            ?: return CommercialLicenseAccess(
                CommercialLicenseMode.UNLICENSED,
                null,
                canWrite = false,
                message = "يلزم تفعيل ترخيص FUSH Customer لهذه الشركة.",
            )
        val status = snapshot.status.trim().uppercase()
        if (status in setOf("CANCELLED", "SUSPENDED", "REVOKED", "BLOCKED")) {
            return CommercialLicenseAccess(
                CommercialLicenseMode.READ_ONLY,
                snapshot,
                canWrite = false,
                message = "الترخيص موقوف. البيانات متاحة للقراءة والتصدير فقط.",
            )
        }
        if (status in setOf("ACTIVE", "TRIAL") && now <= snapshot.expiresAt) {
            val mode = if (snapshot.isTrial || status == "TRIAL") CommercialLicenseMode.TRIAL else CommercialLicenseMode.ACTIVE
            return CommercialLicenseAccess(mode, snapshot, canWrite = true, message = "الترخيص نشط")
        }
        if (status in setOf("ACTIVE", "TRIAL") && now <= snapshot.offlineGraceUntil) {
            return CommercialLicenseAccess(
                CommercialLicenseMode.OFFLINE_GRACE,
                snapshot,
                canWrite = true,
                message = "فترة السماح دون اتصال نشطة. يلزم الاتصال قبل انتهائها.",
            )
        }
        return CommercialLicenseAccess(
            CommercialLicenseMode.READ_ONLY,
            snapshot,
            canWrite = false,
            message = "انتهى الترخيص أو فترة السماح. البيانات متاحة للقراءة والتصدير فقط.",
        )
    }

    suspend fun refresh(user: UserEntity): CloudOperationResult<CommercialLicenseSnapshot> {
        return when (val result = remote.refresh(user)) {
            is CloudOperationResult.Success -> {
                store.save(result.value)
                result
            }
            is CloudOperationResult.Failure -> result
        }
    }

    suspend fun activate(user: UserEntity, activationCode: String): CloudOperationResult<CommercialLicenseSnapshot> {
        return when (val result = remote.activate(user, activationCode)) {
            is CloudOperationResult.Success -> {
                store.save(result.value)
                result
            }
            is CloudOperationResult.Failure -> result
        }
    }

    fun clearLocalSnapshot() = store.clear()

    companion object {
        /** Safe destinations that remain usable when the commercial license is read-only. */
        val READ_ONLY_TARGETS = setOf(
            "الرئيسية",
            "التقارير",
            "تقارير الإنتاج",
            "النسخ الاحتياطي",
            "المزامنة السحابية",
            "الدعم والصيانة",
            "الترخيص التجاري",
        )
    }
}
