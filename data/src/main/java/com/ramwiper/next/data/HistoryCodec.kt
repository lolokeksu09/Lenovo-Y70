package com.ramwiper.next.data

import com.ramwiper.next.domain.CleanupResult
import com.ramwiper.next.domain.Outcome
import org.json.JSONArray
import org.json.JSONObject

/** Preserves the alpha.1–3 JSON keys and optional fields. */
internal object HistoryCodec {
    private fun JSONObject.optionalLong(key: String): Long? = if (isNull(key)) null else optLong(key)

    fun encode(history: List<CleanupResult>): String = JSONArray().apply {
        history.take(100).forEach { r ->
            put(JSONObject().apply {
                put("time", r.time)
                put("engine", r.engine)
                put("profile", r.profile)
                put("before", r.before)
                put("after", r.after)
                put("allocated", r.allocated)
                put("accepted", r.accepted)
                put("attempted", r.attempted)
                put("cancelled", r.cancelled)
                put("note", r.note)
                put("outcome", r.outcome.name)
                put("settled", r.settled ?: JSONObject.NULL)
                put("durationMs", r.durationMs ?: JSONObject.NULL)
                put("swapDelta", r.swapDelta ?: JSONObject.NULL)
            })
        }
    }.toString()

    fun decode(raw: String): List<CleanupResult> {
        val array = JSONArray(raw)
        return (0 until array.length()).asSequence().mapNotNull { index ->
            runCatching {
                val r = array.getJSONObject(index)
                CleanupResult(
                    r.getLong("time"), r.getString("engine"), r.getString("profile"), r.getLong("before"),
                    r.getLong("after"), r.getLong("allocated"), r.getInt("accepted"), r.getInt("attempted"),
                    r.getBoolean("cancelled"), r.getString("note"),
                    runCatching { Outcome.valueOf(r.optString("outcome")) }.getOrDefault(Outcome.LEGACY),
                    r.optionalLong("settled"), r.optionalLong("durationMs"), r.optionalLong("swapDelta")
                )
            }.getOrNull()
        }.take(100).toList()
    }
}
