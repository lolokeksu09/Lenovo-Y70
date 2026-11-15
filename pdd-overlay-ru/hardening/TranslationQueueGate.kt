package app.pinduoduo.overlayru

import java.util.function.Consumer

/**
 * Bounded, main-thread-owned translation reservations.
 *
 * ML Kit's Task cannot be cancelled reliably. A timed-out request is forgotten,
 * its late callback is ignored by ticket ID, and that source is cooled down before
 * retrying. Callbacks are held only while the reservation is valid.
 */
internal class TranslationQueueGate(
    private val capacity: Int = 24,
    private val timeoutMs: Long = 30_000L,
    private val retryDelayMs: Long = 30_000L,
    private val callbackLimit: Int = 4
) {
    init {
        require(capacity > 0 && timeoutMs > 0 && retryDelayMs >= 0 && callbackLimit > 0)
    }

    data class Reservation(val ticket: Long?, val joined: Boolean)

    private data class Entry(
        val ticket: Long,
        val startedAtMs: Long,
        val callbacks: MutableList<Consumer<String?>>
    )

    private val active = LinkedHashMap<String, Entry>()
    private val retryAt = LinkedHashMap<String, Long>()
    private var nextTicket = 0L

    fun reserve(key: String, nowMs: Long, callback: Consumer<String?>?): Reservation {
        expire(nowMs)
        val already = active[key]
        if (already != null) {
            if (callback != null && already.callbacks.size < callbackLimit) {
                already.callbacks.add(callback)
            }
            return Reservation(null, true)
        }
        if (active.size >= capacity || nowMs < (retryAt[key] ?: Long.MIN_VALUE)) {
            return Reservation(null, false)
        }
        val callbacks = ArrayList<Consumer<String?>>(callbackLimit)
        if (callback != null) callbacks.add(callback)
        val ticket = ++nextTicket
        active[key] = Entry(ticket, nowMs, callbacks)
        return Reservation(ticket, false)
    }

    /**
     * Returns the owned callbacks only for a current non-expired ticket.
     * Null means a stale/expired task; callers must not publish its result.
     */
    fun finish(key: String, ticket: Long, nowMs: Long): List<Consumer<String?>>? {
        expire(nowMs)
        val entry = active[key] ?: return null
        if (entry.ticket != ticket) return null
        active.remove(key)
        return entry.callbacks.toList()
    }

    fun activeCount(): Int = active.size

    private fun expire(nowMs: Long) {
        val tasks = active.entries.iterator()
        while (tasks.hasNext()) {
            val task = tasks.next()
            if (nowMs < task.value.startedAtMs ||
                nowMs - task.value.startedAtMs >= timeoutMs) {
                tasks.remove()
                retryAt[task.key] = nowMs + retryDelayMs
            }
        }
        val cooldowns = retryAt.entries.iterator()
        while (cooldowns.hasNext()) {
            if (nowMs >= cooldowns.next().value) cooldowns.remove()
        }
        // A continuous stream of distinct failed labels must not grow this map forever.
        while (retryAt.size > 256) retryAt.remove(retryAt.keys.first())
    }
}
