package app.pinduoduo.overlayru

/**
 * Single flight for non-cancellable ML Kit model requests.
 * A delayed task is still in flight even after the advisory timeout.
 */
class DownloadAttemptGate {
    private var generation = 0
    private var active = false
    private var overdue = false

    fun begin(): Int {
        if (active) return -1
        active = true
        overdue = false
        return ++generation
    }

    fun markOverdue(attemptId: Int): Boolean {
        if (!active || generation != attemptId) return false
        overdue = true
        return true
    }

    fun finish(attemptId: Int): Boolean {
        if (!active || generation != attemptId) return false
        active = false
        overdue = false
        return true
    }

    fun inFlight(): Boolean = active
    fun overdue(): Boolean = overdue
}
