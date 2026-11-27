package com.ramwiper.next.domain

data class RetentionSummary(val count: Int, val positiveAtThree: Int, val positiveAtTen: Int, val medianChange: Long?)
object RetentionAnalytics {
    fun summarize(records: List<CleanupResult>): RetentionSummary {
        val pairs = records.filter { !it.cancelled && it.outcome in setOf(Outcome.COMPLETED,Outcome.PARTIAL) }
            .mapNotNull { r -> r.settledDelta?.let { a -> r.extendedDelta?.let { b -> a to b } } }
        val changes = pairs.map { (a,b) -> b-a }.sorted()
        val mid = changes.size/2
        val median = when {
            changes.isEmpty() -> null
            changes.size%2 == 1 -> changes[mid]
            else -> (changes[mid-1].toDouble()/2 + changes[mid].toDouble()/2).toLong()
        }
        return RetentionSummary(pairs.size,pairs.count { it.first>0 },pairs.count { it.second>0 },median)
    }
}
