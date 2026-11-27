package com.ramwiper.next.data

import com.ramwiper.next.domain.CleanupResult
import com.ramwiper.next.domain.Outcome
import com.ramwiper.next.domain.CleanupTrigger
import com.ramwiper.next.domain.AppMemorySample
import com.ramwiper.next.domain.AppMemoryComparison
import org.json.JSONArray
import org.json.JSONObject

/** Preserves the alpha.1–3 JSON keys and optional fields. */
internal object HistoryCodec {
    private fun JSONObject.optionalLong(key: String): Long? = if (isNull(key)) null else optLong(key)

    private fun encodeSample(s: AppMemorySample?): Any = s?.let {
        JSONObject().apply {
            put("pkg", it.pkg)
            put("pss", it.pssKiB ?: JSONObject.NULL)
            put("rss", it.rssKiB ?: JSONObject.NULL)
            put("swapPss", it.swapPssKiB ?: JSONObject.NULL)
            put("processes", it.processes)
            put("measuredAt", it.measuredAt)
            put("issue", it.issue ?: JSONObject.NULL)
        }
    } ?: JSONObject.NULL

    private fun decodeSample(s: JSONObject?, pkg: String): AppMemorySample? = s?.let {
        require(it.getString("pkg") == pkg)
        AppMemorySample(pkg, it.optionalLong("pss"), it.optionalLong("rss"), it.optionalLong("swapPss"),
            it.getInt("processes"), it.getLong("measuredAt"), if (it.isNull("issue")) null else it.getString("issue"))
    }

    private fun comparisons(r: JSONObject): List<AppMemoryComparison> {
        val array = r.optJSONArray("comparisons") ?: return emptyList()
        return (0 until minOf(array.length(), 12)).mapNotNull { index -> runCatching {
            val item = array.getJSONObject(index)
            val pkg = item.getString("pkg")
            AppMemoryComparison(pkg, decodeSample(item.optJSONObject("before"), pkg),
                decodeSample(item.optJSONObject("after"), pkg))
        }.getOrNull() }
    }

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
                put("trigger", r.trigger.name)
                put("settled", r.settled ?: JSONObject.NULL)
                put("durationMs", r.durationMs ?: JSONObject.NULL)
                put("swapDelta", r.swapDelta ?: JSONObject.NULL)
                put("comparisons", JSONArray().apply { r.comparisons.take(12).forEach { c ->
                    put(JSONObject().apply { put("pkg", c.pkg); put("before", encodeSample(c.before)); put("after", encodeSample(c.after)) })
                } })
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
                    r.optionalLong("settled"), r.optionalLong("durationMs"), r.optionalLong("swapDelta"),
                    runCatching { CleanupTrigger.valueOf(r.optString("trigger")) }.getOrDefault(CleanupTrigger.MANUAL),
                    comparisons(r)
                )
            }.getOrNull()
        }.take(100).toList()
    }
}
