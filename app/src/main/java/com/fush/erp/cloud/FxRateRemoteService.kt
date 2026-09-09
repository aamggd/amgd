package com.fush.erp.cloud

import com.fush.erp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Network-only client. Source scraping lives in Supabase, never in the Android app. */
class FxRateRemoteService {
    suspend fun fetchLatest(): FxRemoteBatch = withContext(Dispatchers.IO) {
        val endpoint = BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/fx-rates"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 25_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
        }
        try {
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(body).optString("error") }.getOrNull().orEmpty()
                error(message.ifBlank { "تعذر جلب أسعار الصرف من خدمة Supabase (HTTP $code)" })
            }
            parseBatch(JSONObject(body))
        } finally {
            connection.disconnect()
        }
    }

    private fun parseBatch(root: JSONObject): FxRemoteBatch {
        val batchId = root.getString("batchId").trim()
        val fetchedAt = root.getLong("fetchedAt")
        val fallbackMode = root.optString("fallbackMode", "LIVE").ifBlank { "LIVE" }
        val array = root.optJSONArray("rates") ?: JSONArray()
        val rates = buildList {
            for (i in 0 until array.length()) {
                val row = array.getJSONObject(i)
                add(
                    FxRemoteRate(
                        marketRegion = row.getString("marketRegion").trim().uppercase(),
                        currencyCode = row.getString("currencyCode").trim().uppercase(),
                        rateType = row.getString("rateType").trim().uppercase(),
                        rateYer = row.getDouble("rateYer"),
                        primarySource = row.getString("primarySource").trim(),
                        primarySourceUrl = row.optString("primarySourceUrl").trim(),
                        sourcePublishedAt = row.getLong("sourcePublishedAt"),
                        comparisonRateYer = row.optNullableDouble("comparisonRateYer"),
                        comparisonSource = row.optString("comparisonSource").trim(),
                        comparisonSourceUrl = row.optString("comparisonSourceUrl").trim(),
                        comparisonPublishedAt = row.optNullableLong("comparisonPublishedAt"),
                        variancePercent = row.optNullableDouble("variancePercent"),
                        sourceStatus = row.optString("sourceStatus", "PRIMARY_ONLY").trim().uppercase(),
                        rawHash = row.optString("rawHash").trim()
                    )
                )
            }
        }
        require(batchId.isNotBlank()) { "خدمة أسعار الصرف أعادت Batch فارغًا" }
        require(rates.isNotEmpty()) { "خدمة أسعار الصرف لم تعد أي أسعار" }
        return FxRemoteBatch(batchId, fetchedAt, fallbackMode, rates)
    }
}

data class FxRemoteBatch(
    val batchId: String,
    val fetchedAt: Long,
    val fallbackMode: String,
    val rates: List<FxRemoteRate>
)

data class FxRemoteRate(
    val marketRegion: String,
    val currencyCode: String,
    val rateType: String,
    val rateYer: Double,
    val primarySource: String,
    val primarySourceUrl: String,
    val sourcePublishedAt: Long,
    val comparisonRateYer: Double?,
    val comparisonSource: String,
    val comparisonSourceUrl: String,
    val comparisonPublishedAt: Long?,
    val variancePercent: Double?,
    val sourceStatus: String,
    val rawHash: String
)

private fun JSONObject.optNullableDouble(name: String): Double? =
    if (!has(name) || isNull(name)) null else optDouble(name).takeIf { it.isFinite() }

private fun JSONObject.optNullableLong(name: String): Long? =
    if (!has(name) || isNull(name)) null else optLong(name).takeIf { it > 0L }
