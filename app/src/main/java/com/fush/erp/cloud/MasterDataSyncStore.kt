package com.fush.erp.cloud

import android.content.Context
import java.security.MessageDigest

/**
 * Device-local synchronization checkpoint for non-transactional master data.
 *
 * Room schema intentionally stays at 46. Row hashes are kept outside Room so v158 can
 * identify local edits without adding sync columns to business tables. On a device's
 * first baseline, cloud data is authoritative unless the cloud is completely empty and
 * the current user is OWNER/ADMIN (the initial company bootstrap case).
 */
internal class MasterDataSyncStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun baselineComplete(localUserId: Long): Boolean =
        prefs.getBoolean("user.$localUserId.baseline", false)

    fun markBaselineComplete(localUserId: Long) {
        prefs.edit().putBoolean("user.$localUserId.baseline", true).apply()
    }

    fun lastSuccessAt(localUserId: Long): Long =
        prefs.getLong("user.$localUserId.last_success_at", 0L)

    fun markSuccess(localUserId: Long, atEpochMillis: Long) {
        prefs.edit().putLong("user.$localUserId.last_success_at", atEpochMillis).apply()
    }

    fun rowHash(localUserId: Long, scope: String, key: String): String? =
        prefs.getString(hashKey(localUserId, scope, key), null)

    fun saveRowHash(localUserId: Long, scope: String, key: String, value: String) {
        prefs.edit().putString(hashKey(localUserId, scope, key), value).apply()
    }

    private fun hashKey(localUserId: Long, scope: String, key: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$scope\u001f$key".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "user.$localUserId.row.$digest"
    }

    private companion object {
        const val PREFS = "fush_cloud_master_data_sync_v1"
    }
}
