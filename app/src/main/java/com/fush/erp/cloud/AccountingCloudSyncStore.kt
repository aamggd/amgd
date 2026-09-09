package com.fush.erp.cloud

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal class AccountingCloudSyncStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lastSuccessAt(localUserId: Long): Long = prefs.getLong("user.$localUserId.last_success_at", 0L)

    fun markSuccess(localUserId: Long, atEpochMillis: Long) {
        prefs.edit().putLong("user.$localUserId.last_success_at", atEpochMillis).apply()
    }

    fun saveConflicts(localUserId: Long, rows: List<AccountingSyncConflict>) {
        val array = JSONArray()
        rows.forEach { row ->
            val diffs = JSONArray()
            row.differences.forEach { diff ->
                diffs.put(JSONObject().put("field", diff.field).put("local", diff.localValue).put("cloud", diff.cloudValue))
            }
            array.put(JSONObject().put("type", row.entityType).put("key", row.entityKey).put("diffs", diffs))
        }
        prefs.edit().putString("user.$localUserId.conflicts", array.toString()).apply()
    }

    fun conflicts(localUserId: Long): List<AccountingSyncConflict> {
        val raw = prefs.getString("user.$localUserId.conflicts", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val row = array.getJSONObject(index)
                    val diffsArray = row.optJSONArray("diffs") ?: JSONArray()
                    val diffs = buildList {
                        for (diffIndex in 0 until diffsArray.length()) {
                            val diff = diffsArray.getJSONObject(diffIndex)
                            add(AccountingSyncConflictDifference(diff.optString("field"), diff.optString("local"), diff.optString("cloud")))
                        }
                    }
                    add(AccountingSyncConflict(row.optString("type"), row.optString("key"), diffs))
                }
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val PREFS = "fush_cloud_accounting_sync_v177"
    }
}
