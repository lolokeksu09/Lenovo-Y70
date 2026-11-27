package com.ramwiper.next.domain

data class HistorySummary(
    val count: Int,
    val completed: Int,
    val partial: Int,
    val skipped: Int,
    val cancelled: Int,
    val failed: Int,
    val measured: Int,
    val positive: Int,
    val medianDelta: Long?,
    val automatic: Int
)
object HistoryAnalytics {
    fun summarize(records: List<CleanupResult>): HistorySummary {
        val measured = records.filter { it.outcome == Outcome.COMPLETED || it.outcome == Outcome.PARTIAL }
            .mapNotNull { it.settledDelta }.sorted()
        val median = when {
            measured.isEmpty() -> null
            measured.size % 2 == 1 -> measured[measured.size / 2]
            else -> (measured[measured.size / 2 - 1].toDouble() / 2 +
                measured[measured.size / 2].toDouble() / 2).toLong()
        }
        return HistorySummary(records.size, records.count { it.outcome == Outcome.COMPLETED },
            records.count { it.outcome == Outcome.PARTIAL }, records.count { it.outcome == Outcome.SKIPPED },
            records.count { it.cancelled || it.outcome == Outcome.CANCELLED },
            records.count { it.outcome == Outcome.FAILED }, measured.size, measured.count { it > 0 }, median,
            records.count { it.trigger == CleanupTrigger.AUTOMATIC })
    }
}
