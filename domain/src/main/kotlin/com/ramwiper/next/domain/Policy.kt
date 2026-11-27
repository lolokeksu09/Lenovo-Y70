package com.ramwiper.next.domain

const val MIB = 1024L * 1024

enum class Profile(val capMiB: Long, val fraction: Int, val holdMs: Long) {
    SOFT(128, 3, 100), BALANCED(512, 6, 250), STRONG(768, 10, 350)
}
enum class Engine { NATIVE, SHIZUKU, HYBRID }
data class Snapshot(val total: Long, val available: Long, val threshold: Long, val low: Boolean,
                    val swapTotal: Long? = null, val swapFree: Long? = null, val thermal: Int? = null,
                    val psiSome: Double? = null)
object Policy {
    const val BLOCK = 16 * 1024 * 1024
    fun reserve(s: Snapshot): Long = maxOf(512 * MIB, s.threshold * 2, s.total / 12)
    fun blockReason(s: Snapshot): String? = when {
        s.total <= 0 || s.available !in 0..s.total || s.threshold !in 0..s.total -> "Некорректные данные памяти"
        s.low -> "Android сообщает о нехватке памяти"
        s.thermal != null && s.thermal >= 3 -> "Высокий тепловой статус"
        s.psiSome != null && (!s.psiSome.isFinite() || s.psiSome >= 10.0 || s.psiSome < 0) -> "Высокое или некорректное давление PSI"
        s.available <= reserve(s) -> "Достигнут защитный резерв"
        else -> null
    }
    fun safe(s: Snapshot): Boolean = blockReason(s) == null
    fun budget(s: Snapshot, p: Profile, limits: NativeLimits = NativeLimits()): Long {
        if (!safe(s)) return 0
        return minOf(limits.cap(p) * MIB, s.total / 100 * p.fraction, (s.available - reserve(s)) / 2)
            .coerceAtLeast(0) / BLOCK * BLOCK
    }
    fun next(s: Snapshot, remaining: Long): Int = if (safe(s) && remaining >= BLOCK && s.available - reserve(s) >= BLOCK * 2L) BLOCK else 0
    fun shouldRunNative(before: Snapshot, after: Snapshot): Boolean = safe(after) && after.available - before.available < 128 * MIB
}
