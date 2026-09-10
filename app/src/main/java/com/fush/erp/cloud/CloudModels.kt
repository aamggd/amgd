package com.fush.erp.cloud

data class CloudSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String?,
    val expiresAtEpochSeconds: Long,
    /** v213: runtime-selected tenant. Never compiled into the APK. */
    val organizationId: String? = null,
)

internal fun CloudSession.requireOrganizationId(): String =
    organizationId?.trim()?.takeIf { it.isNotEmpty() }
        ?: error("Cloud organization is not selected")

data class CloudUserBinding(
    val localUserId: Long,
    val localUsername: String,
    val cloudUserId: String,
    val email: String?,
    val cloudRole: String,
    val displayName: String?,
)

data class CloudConnectionResult(
    val userId: String,
    val email: String?,
    val organizationId: String,
    val organizationName: String,
    val deviceId: String,
    val deviceName: String,
    val cloudRole: String,
    val localUsername: String,
)


data class CloudJoinIdentity(
    val session: CloudSession,
    val binding: CloudUserBinding,
)


enum class CloudCompanyBootstrapStage(val label: String, val userMessage: String) {
    CREATING_ACCOUNT("إنشاء الحساب", "جاري إنشاء الحساب السحابي…"),
    SIGNING_IN("تسجيل الدخول", "جاري تسجيل الدخول…"),
    CREATING_COMPANY("إنشاء الشركة", "جاري إنشاء الشركة…"),
    LINKING_OWNER("ربط المالك", "جاري ربط المالك…"),
    BINDING_DEVICE("ربط الجهاز", "جاري ربط الجهاز بالشركة…"),
    SUCCESS("النجاح", "تم إنشاء الشركة وربط المالك بنجاح"),
}

data class CloudCompanyBootstrapResult(
    val session: CloudSession,
    val binding: CloudUserBinding,
    val organizationId: String,
    val organizationCode: String,
    val organizationName: String,
)

data class CloudProvisionResult(
    val cloudUserId: String,
    val email: String,
    val localUsername: String,
    val cloudRole: String,
    val requiresEmailConfirmation: Boolean,
)

sealed interface CloudOperationResult<out T> {
    data class Success<T>(val value: T) : CloudOperationResult<T>
    data class Failure(val message: String, val httpCode: Int? = null) : CloudOperationResult<Nothing>
}

/** v185 summary for the single "Sync all now" action. */
data class CompanySyncAllResult(
    val uploadedRecords: Int,
    val downloadedRecords: Int,
    val conflicts: Int,
    val completedAtEpochMillis: Long,
)


/** v217 server-acknowledged request to delete the authenticated cloud account. */
data class CloudAccountDeletionRequestResult(
    val requestId: String,
    val status: String,
    val requestedAt: String?,
)
