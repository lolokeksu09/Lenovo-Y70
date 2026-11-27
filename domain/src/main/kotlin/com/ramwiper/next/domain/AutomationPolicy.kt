package com.ramwiper.next.domain

/** Explicit opt-in; automation never kills a selected app through Shizuku. */
data class AutomationSettings(
    val foregroundEnabled: Boolean = false,
    val thresholdPercent: Int = 20,
    val intervalMinutes: Int = 5,
    val backgroundAlerts: Boolean = false
) {
    companion object {
        val THRESHOLDS = listOf(15, 20, 25, 30, 40)
        val INTERVALS = listOf(1, 5, 15, 30)
    }
    fun normalized() = copy(
        thresholdPercent = thresholdPercent.takeIf { it in THRESHOLDS } ?: 20,
        intervalMinutes = intervalMinutes.takeIf { it in INTERVALS } ?: 5
    )
}
object AutomationPolicy {
    fun availablePercent(s: Snapshot): Int? =
        if (s.total > 0 && s.available in 0..s.total)
            (s.available.toDouble() / s.total * 100).toInt() else null

    fun belowThreshold(s: Snapshot, settings: AutomationSettings): Boolean =
        availablePercent(s) != null && s.available.toDouble() / s.total * 100 <= settings.normalized().thresholdPercent

    fun mayAllocate(s: Snapshot, settings: AutomationSettings): Boolean =
        settings.foregroundEnabled && belowThreshold(s, settings) &&
            Policy.budget(s, Profile.SOFT) >= Policy.BLOCK
}

/** Two consecutive qualifying samples; reset after invalid sample/foreground loss.
 * Interval uses monotonic milliseconds and includes manual operations. */
class AutomationGate {
    private var consecutive = 0
    private var lastAttempt: Long? = null
    fun resetSamples() { consecutive = 0 }
    fun recordAttempt(now: Long) { lastAttempt = now; consecutive = 0 }
    fun observe(s: Snapshot, settings: AutomationSettings, now: Long, idle: Boolean): Boolean {
        if (!idle || !AutomationPolicy.mayAllocate(s, settings)) { resetSamples(); return false }
        consecutive = (consecutive + 1).coerceAtMost(2)
        val interval = settings.normalized().intervalMinutes * 60_000L
        return consecutive >= 2 && (lastAttempt?.let { now - it >= interval } ?: true)
    }
}
enum class CleanupTrigger { MANUAL, AUTOMATIC }
