package com.ramwiper.next.domain

data class StrategySummary(val engine: String, val profile: String, val count: Int, val positive: Int,
                           val medianDelta: Long?, val medianDurationMs: Long?, val medianSwapDelta: Long?)
object StrategyAnalytics {
    private fun median(values: List<Long>): Long? {
        val sorted = values.sorted()
        if (sorted.isEmpty()) return null
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid]
        else (sorted[mid - 1].toDouble() / 2 + sorted[mid].toDouble() / 2).toLong()
    }
    fun compare(records: List<CleanupResult>): List<StrategySummary> = records
        .filter { it.trigger == CleanupTrigger.MANUAL && !it.cancelled &&
            it.outcome in setOf(Outcome.COMPLETED, Outcome.PARTIAL) && it.settledDelta != null }
        .groupBy { r ->
            val route = when {
                r.allocated > 0 && r.accepted > 0 -> "HYBRID"
                r.allocated > 0 -> "NATIVE"
                r.accepted > 0 -> "SHIZUKU"
                else -> "UNKNOWN"
            }
            route to if (r.allocated > 0) r.effectiveNativeProfile ?: "UNKNOWN" else "—"
        }.filterKeys { it.first != "UNKNOWN" }
        .map { (key, rows) ->
            val deltas = rows.mapNotNull { it.settledDelta }
            StrategySummary(key.first, key.second, rows.size, deltas.count { it > 0 }, median(deltas),
                median(rows.mapNotNull { it.durationMs }), median(rows.mapNotNull { it.swapDelta }))
        }.sortedWith(compareBy<StrategySummary> { it.engine }.thenBy { it.profile })
}
