package com.fush.erp.cloud

import com.fush.erp.BuildConfig
import com.fush.erp.data.entity.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * v210 private FUSH AI gateway client.
 *
 * The model never receives database credentials and never queries Room/Supabase ERP tables.
 * Phase 1 sends only the natural-language question, short chat history and the names of tools
 * already permitted for the local ERP user. The Android app executes every tool locally.
 * Phase 2 sends only the resulting, permission-gated tool summaries for natural-language wording.
 */
class FushAiRemoteService(
    private val cloud: CloudSyncRepository,
) {
    suspend fun plan(
        user: UserEntity,
        question: String,
        history: List<FushAiRemoteHistoryTurn>,
        allowedTools: Set<String>,
    ): FushAiRemotePlan? {
        if (allowedTools.isEmpty()) return null
        val session = when (val result = cloud.validSessionForAi(user)) {
            is CloudOperationResult.Success -> result.value
            is CloudOperationResult.Failure -> return null
        }
        val payload = JSONObject()
            .put("phase", "plan")
            .put("organizationId", session.requireOrganizationId())
            .put("question", question)
            .put("history", history.toJson())
            .put("allowedTools", JSONArray(allowedTools.sorted()))
        return call(session, payload)?.let(FushAiRemoteContract::parsePlan)
    }

    suspend fun answer(
        user: UserEntity,
        question: String,
        history: List<FushAiRemoteHistoryTurn>,
        results: List<FushAiRemoteToolResult>,
    ): String? {
        if (results.isEmpty()) return null
        val session = when (val result = cloud.validSessionForAi(user)) {
            is CloudOperationResult.Success -> result.value
            is CloudOperationResult.Failure -> return null
        }
        val payload = JSONObject()
            .put("phase", "answer")
            .put("organizationId", session.requireOrganizationId())
            .put("question", question)
            .put("history", history.toJson())
            .put("toolResults", JSONArray().apply {
                results.forEach { row ->
                    put(JSONObject().put("name", row.name).put("content", row.content))
                }
            })
        val root = call(session, payload) ?: return null
        return root.optString("answer").trim().takeIf { it.isNotBlank() }
    }

    private suspend fun call(session: CloudSession, payload: JSONObject): JSONObject? = withContext(Dispatchers.IO) {
        val endpoint = BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/fush-ai"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 35_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer ${session.accessToken}")
        }
        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299 || body.isBlank()) return@withContext null
            runCatching { JSONObject(body) }.getOrNull()
        } finally {
            connection.disconnect()
        }
    }

    private fun List<FushAiRemoteHistoryTurn>.toJson(): JSONArray = JSONArray().apply {
        takeLast(8).forEach { turn ->
            put(JSONObject().put("role", turn.role).put("content", turn.content.take(1200)))
        }
    }
}

data class FushAiRemoteHistoryTurn(val role: String, val content: String)
data class FushAiRemoteToolCall(val name: String, val arguments: JSONObject)
data class FushAiRemotePlan(val toolCalls: List<FushAiRemoteToolCall>, val directReply: String?)
data class FushAiRemoteToolResult(val name: String, val content: String)

/** Pure JSON contract parser, separated for regression tests. */
object FushAiRemoteContract {
    fun parsePlan(root: JSONObject): FushAiRemotePlan {
        val calls = buildList {
            val array = root.optJSONArray("toolCalls") ?: JSONArray()
            for (i in 0 until minOf(array.length(), 4)) {
                val row = array.optJSONObject(i) ?: continue
                val name = row.optString("name").trim()
                if (name.isBlank()) continue
                add(FushAiRemoteToolCall(name, row.optJSONObject("arguments") ?: JSONObject()))
            }
        }
        return FushAiRemotePlan(
            toolCalls = calls,
            directReply = root.optString("directReply").trim().takeIf { it.isNotBlank() },
        )
    }
}
