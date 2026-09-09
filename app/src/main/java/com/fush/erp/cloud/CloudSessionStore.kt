package com.fush.erp.cloud

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Per-local-user cloud session store.
 *
 * v155 had one device-wide Supabase session. v156 scopes tokens and bindings by the
 * local FUSH user so accountants, production workers and sales users never share the
 * owner's cloud identity on the same Android installation.
 */
internal class CloudSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun load(localUserId: Long, adoptLegacyForAdmin: Boolean = false): CloudSession? {
        loadScoped(localUserId)?.let { return it }
        if (!adoptLegacyForAdmin) return null
        val legacy = loadLegacy() ?: return null
        save(localUserId, legacy)
        legacyPrefs.edit().clear().apply()
        return legacy
    }

    fun save(localUserId: Long, session: CloudSession) {
        val prefix = sessionPrefix(localUserId)
        prefs.edit()
            .putString(prefix + KEY_ACCESS, encrypt(session.accessToken))
            .putString(prefix + KEY_REFRESH, encrypt(session.refreshToken))
            .putString(prefix + KEY_USER_ID, session.userId)
            .putString(prefix + KEY_EMAIL, session.email)
            .putLong(prefix + KEY_EXPIRES_AT, session.expiresAtEpochSeconds)
            .putString(prefix + KEY_ORGANIZATION_ID, session.organizationId)
            .apply()
    }


    fun binding(localUserId: Long): CloudUserBinding? = runCatching {
        val prefix = bindingPrefix(localUserId)
        val localUsername = prefs.getString(prefix + KEY_LOCAL_USERNAME, null) ?: return null
        val cloudUserId = prefs.getString(prefix + KEY_CLOUD_USER_ID, null) ?: return null
        val cloudRole = prefs.getString(prefix + KEY_CLOUD_ROLE, null) ?: return null
        CloudUserBinding(
            localUserId = localUserId,
            localUsername = localUsername,
            cloudUserId = cloudUserId,
            email = prefs.getString(prefix + KEY_EMAIL, null),
            cloudRole = cloudRole,
            displayName = prefs.getString(prefix + KEY_DISPLAY_NAME, null),
        )
    }.getOrNull()

    fun saveBinding(binding: CloudUserBinding) {
        val prefix = bindingPrefix(binding.localUserId)
        prefs.edit()
            .putString(prefix + KEY_LOCAL_USERNAME, binding.localUsername)
            .putString(prefix + KEY_CLOUD_USER_ID, binding.cloudUserId)
            .putString(prefix + KEY_EMAIL, binding.email)
            .putString(prefix + KEY_CLOUD_ROLE, binding.cloudRole)
            .putString(prefix + KEY_DISPLAY_NAME, binding.displayName)
            .apply()
    }

    fun clearSession(localUserId: Long) {
        val prefix = sessionPrefix(localUserId)
        prefs.edit()
            .remove(prefix + KEY_ACCESS)
            .remove(prefix + KEY_REFRESH)
            .remove(prefix + KEY_USER_ID)
            .remove(prefix + KEY_EMAIL)
            .remove(prefix + KEY_EXPIRES_AT)
            .remove(prefix + KEY_ORGANIZATION_ID)
            .apply()
    }

    fun clearBinding(localUserId: Long) {
        val prefix = bindingPrefix(localUserId)
        prefs.edit()
            .remove(prefix + KEY_LOCAL_USERNAME)
            .remove(prefix + KEY_CLOUD_USER_ID)
            .remove(prefix + KEY_EMAIL)
            .remove(prefix + KEY_CLOUD_ROLE)
            .remove(prefix + KEY_DISPLAY_NAME)
            .apply()
    }

    private fun loadScoped(localUserId: Long): CloudSession? = runCatching {
        val prefix = sessionPrefix(localUserId)
        val access = decrypt(prefs.getString(prefix + KEY_ACCESS, null) ?: return null)
        val refresh = decrypt(prefs.getString(prefix + KEY_REFRESH, null) ?: return null)
        val userId = prefs.getString(prefix + KEY_USER_ID, null) ?: return null
        val email = prefs.getString(prefix + KEY_EMAIL, null)
        val expiresAt = prefs.getLong(prefix + KEY_EXPIRES_AT, 0L)
        if (access.isBlank() || refresh.isBlank() || userId.isBlank() || expiresAt <= 0L) return null
        CloudSession(access, refresh, userId, email, expiresAt, prefs.getString(prefix + KEY_ORGANIZATION_ID, null))
    }.getOrElse {
        clearSession(localUserId)
        null
    }

    private fun loadLegacy(): CloudSession? = runCatching {
        val access = decrypt(legacyPrefs.getString(KEY_ACCESS, null) ?: return null)
        val refresh = decrypt(legacyPrefs.getString(KEY_REFRESH, null) ?: return null)
        val userId = legacyPrefs.getString(KEY_USER_ID, null) ?: return null
        val email = legacyPrefs.getString(KEY_EMAIL, null)
        val expiresAt = legacyPrefs.getLong(KEY_EXPIRES_AT, 0L)
        if (access.isBlank() || refresh.isBlank() || userId.isBlank() || expiresAt <= 0L) return null
        CloudSession(access, refresh, userId, email, expiresAt)
    }.getOrNull()

    private fun sessionPrefix(localUserId: Long) = "session.$localUserId."
    private fun bindingPrefix(localUserId: Long) = "binding.$localUserId."

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val encrypted = Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        return "$iv:$encrypted"
    }

    private fun decrypt(value: String): String {
        val parts = value.split(':', limit = 2)
        require(parts.size == 2) { "Invalid encrypted cloud session" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        val decrypted = cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP))
        return decrypted.toString(Charsets.UTF_8)
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
        const val PREFS = "fush_cloud_session_v2"
        const val LEGACY_PREFS = "fush_cloud_session_v1"
        const val KEY_ALIAS = "fush_cloud_auth_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_EMAIL = "email"
        const val KEY_EXPIRES_AT = "expires_at"
        const val KEY_ORGANIZATION_ID = "organization_id"
        const val KEY_LOCAL_USERNAME = "local_username"
        const val KEY_CLOUD_USER_ID = "cloud_user_id"
        const val KEY_CLOUD_ROLE = "cloud_role"
        const val KEY_DISPLAY_NAME = "display_name"
    }
}
