package com.fush.erp.cloud

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.fush.erp.BuildConfig
import com.fush.erp.data.entity.UserEntity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** v214 commercial license snapshot returned only by server-side RPCs. */
data class CommercialLicenseSnapshot(
    val organizationId: String,
    val licenseId: String,
    val deviceEntitlementId: String?,
    val planCode: String,
    val status: String,
    val isTrial: Boolean,
    val startsAt: Long,
    val expiresAt: Long,
    val offlineGraceUntil: Long,
    val maxDevices: Int,
    val verifiedAt: Long,
    val serverTime: Long,
)

/**
 * Server-authoritative licensing client.
 * The APK never decides plan limits, expiry, activation validity or device entitlement by itself.
 */
class CommercialLicenseRemoteService(
    context: Context,
    private val cloudRepository: CloudSyncRepository,
) {
    private val appContext = context.applicationContext

    suspend fun refresh(localUser: UserEntity): CloudOperationResult<CommercialLicenseSnapshot> {
        val session = when (val result = cloudRepository.validSessionForAi(localUser)) {
            is CloudOperationResult.Success -> result.value
            is CloudOperationResult.Failure -> return result
        }
        val body = JSONObject()
            .put("target_organization_id", session.requireOrganizationId())
            .put("target_device_key", deviceKey())
            .put("target_device_name", deviceName())
            .put("target_app_version", BuildConfig.VERSION_NAME)
            .toString()
        return rpc("fush_get_commercial_license_snapshot", body, session.accessToken)
    }

    suspend fun activate(
        localUser: UserEntity,
        activationCode: String,
    ): CloudOperationResult<CommercialLicenseSnapshot> {
        val normalized = activationCode.trim().uppercase()
        if (normalized.length !in 8..128) {
            return CloudOperationResult.Failure("رمز التفعيل غير صالح")
        }
        val session = when (val result = cloudRepository.validSessionForAi(localUser)) {
            is CloudOperationResult.Success -> result.value
            is CloudOperationResult.Failure -> return result
        }
        val body = JSONObject()
            .put("target_organization_id", session.requireOrganizationId())
            .put("activation_code", normalized)
            .put("target_device_key", deviceKey())
            .put("target_device_name", deviceName())
            .put("target_app_version", BuildConfig.VERSION_NAME)
            .toString()
        return rpc("fush_activate_commercial_license", body, session.accessToken)
    }

    private fun rpc(
        function: String,
        body: String,
        accessToken: String,
    ): CloudOperationResult<CommercialLicenseSnapshot> {
        val connection = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/rpc/$function")
            .openConnection() as HttpURLConnection)
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.doOutput = true
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val json = runCatching { JSONObject(raw) }.getOrNull()
                val message = json?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: json?.optString("details")?.takeIf { it.isNotBlank() }
                    ?: "تعذر التحقق من الترخيص (HTTP $code)"
                return CloudOperationResult.Failure(message, code)
            }
            val json = runCatching { JSONObject(raw) }.getOrElse {
                return CloudOperationResult.Failure("استجابة خدمة الترخيص غير صالحة")
            }
            parseSnapshot(json)
        } catch (error: Throwable) {
            CloudOperationResult.Failure(error.message?.takeIf { it.isNotBlank() } ?: "تعذر الاتصال بخدمة الترخيص")
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSnapshot(json: JSONObject): CloudOperationResult<CommercialLicenseSnapshot> = runCatching {
        val organizationId = json.getString("organization_id").trim()
        val licenseId = json.getString("license_id").trim()
        require(organizationId.isNotBlank() && licenseId.isNotBlank())
        CommercialLicenseSnapshot(
            organizationId = organizationId,
            licenseId = licenseId,
            deviceEntitlementId = json.optString("device_entitlement_id").trim().takeIf { it.isNotBlank() },
            planCode = json.optString("plan_code", "STANDARD").trim().uppercase(),
            status = json.optString("status", "UNLICENSED").trim().uppercase(),
            isTrial = json.optBoolean("is_trial", false),
            startsAt = json.optLong("starts_at", 0L),
            expiresAt = json.optLong("expires_at", 0L),
            offlineGraceUntil = json.optLong("offline_grace_until", 0L),
            maxDevices = json.optInt("max_devices", 1).coerceAtLeast(1),
            verifiedAt = System.currentTimeMillis(),
            serverTime = json.optLong("server_time", System.currentTimeMillis()),
        )
    }.fold(
        onSuccess = { CloudOperationResult.Success(it) },
        onFailure = { CloudOperationResult.Failure("بيانات الترخيص المستلمة غير مكتملة") },
    )

    private fun deviceName(): String = listOf(Build.MANUFACTURER.orEmpty(), Build.MODEL.orEmpty())
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy(String::lowercase)
        .joinToString(" ")
        .ifBlank { "Android device" }
        .take(120)

    private fun deviceKey(): String {
        val androidId = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        val raw = "${appContext.packageName}|$androidId"
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
