package com.ramwiper.next.domain

enum class Outcome { COMPLETED, PARTIAL, SKIPPED, CANCELLED, FAILED, LEGACY }
data class Progress(val stage: String, val allocated: Long = 0, val target: Long = 0)
data class CleanupResult(val time: Long, val engine: String, val profile: String, val before: Long, val after: Long,
                  val allocated: Long, val accepted: Int, val attempted: Int, val cancelled: Boolean, val note: String,
                  val outcome: Outcome = Outcome.LEGACY, val settled: Long? = null, val durationMs: Long? = null,
                  val swapDelta: Long? = null, val trigger: CleanupTrigger = CleanupTrigger.MANUAL,
                  val comparisons: List<AppMemoryComparison> = emptyList()) {
    val delta get() = after - before
    val settledDelta get() = settled?.minus(before)
}
