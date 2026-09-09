package com.fush.erp.cloud

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal class SalesReceivablesSyncStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lastSuccessAt(localUserId: Long): Long = prefs.getLong("last_success_$localUserId", 0L)
    fun baselineComplete(localUserId: Long): Boolean = prefs.getBoolean("baseline_$localUserId", false)
    fun markBaselineComplete(localUserId: Long) = prefs.edit().putBoolean("baseline_$localUserId", true).apply()
    fun markSuccess(localUserId: Long, at: Long) = prefs.edit().putLong("last_success_$localUserId", at).apply()

    fun saveConflicts(localUserId: Long, rows: List<SalesReceivablesConflict>) {
        val array = JSONArray()
        rows.forEach { row ->
            val diffs = JSONArray()
            row.differences.forEach { diff ->
                diffs.put(
                    JSONObject()
                        .put("field", diff.field)
                        .put("local", diff.localValue)
                        .put("cloud", diff.cloudValue)
                )
            }
            array.put(
                JSONObject()
                    .put("type", row.documentType)
                    .put("no", row.documentNo)
                    .put("diffs", diffs)
            )
        }
        prefs.edit().putString("conflicts_$localUserId", array.toString()).apply()
    }

    fun conflicts(localUserId: Long): List<SalesReceivablesConflict> {
        val raw = prefs.getString("conflicts_$localUserId", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val row = array.getJSONObject(index)
                    val diffsArray = row.optJSONArray("diffs") ?: JSONArray()
                    val diffs = buildList {
                        for (diffIndex in 0 until diffsArray.length()) {
                            val diff = diffsArray.getJSONObject(diffIndex)
                            add(
                                SalesConflictDifference(
                                    field = diff.optString("field"),
                                    localValue = diff.optString("local"),
                                    cloudValue = diff.optString("cloud"),
                                )
                            )
                        }
                    }
                    add(
                        SalesReceivablesConflict(
                            documentType = row.optString("type"),
                            documentNo = row.optString("no"),
                            differences = diffs,
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val PREFS = "fush_cloud_sales_receivables_sync_v1"
    }
}
