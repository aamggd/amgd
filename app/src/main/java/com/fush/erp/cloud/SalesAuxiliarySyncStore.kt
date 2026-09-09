package com.fush.erp.cloud

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal class SalesAuxiliarySyncStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lastSuccessAt(localUserId: Long): Long = prefs.getLong("user.$localUserId.last_success_at", 0L)

    fun markSuccess(localUserId: Long, atEpochMillis: Long) {
        prefs.edit().putLong("user.$localUserId.last_success_at", atEpochMillis).apply()
    }

    fun saveConflicts(localUserId: Long, rows: List<SalesAuxiliaryConflict>) {
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

    fun conflicts(localUserId: Long): List<SalesAuxiliaryConflict> {
        val raw = prefs.getString("user.$localUserId.conflicts", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val row = array.getJSONObject(i)
                    val da = row.optJSONArray("diffs") ?: JSONArray()
                    val diffs = buildList {
                        for (j in 0 until da.length()) {
                            val d = da.getJSONObject(j)
                            add(SalesAuxiliaryConflictDifference(d.optString("field"), d.optString("local"), d.optString("cloud")))
                        }
                    }
                    add(SalesAuxiliaryConflict(row.optString("type"), row.optString("key"), diffs))
                }
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val PREFS = "fush_cloud_sales_aux_sync_v179"
    }
}
