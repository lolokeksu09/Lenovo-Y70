package com.ramwiper.next.domain

/** Conservative planning only; never raises the requested profile or overrides Policy. */
data class NativePlan(val profile: Profile, val budget: Long, val reason: String)
object AdaptivePolicy {
    fun plan(s: Snapshot, requested: Profile, limits: NativeLimits): NativePlan {
        val blocked = Policy.blockReason(s)
        if (blocked != null) return NativePlan(requested, 0, blocked)
        if (!limits.adaptive) {
            val budget = Policy.budget(s, requested, limits)
            return NativePlan(requested, budget, if (budget == 0L) "Резерв недостаточен для блока" else "Ручной профиль")
        }
        val percent = s.available.toDouble() / s.total * 100
        if (percent >= 40) return NativePlan(requested, 0, "Достаточно доступной RAM (40% и выше)")
        val psi = s.psiSome
        val thermal = s.thermal
        val ceiling = when {
            psi == null || thermal == null -> Profile.SOFT
            psi >= 3.0 || thermal >= 2 || percent < 15 -> Profile.SOFT
            else -> Profile.BALANCED
        }
        val effective = Profile.entries[minOf(requested.ordinal, ceiling.ordinal)]
        val reason = when {
            psi == null || thermal == null -> "Неполная телеметрия"
            psi >= 3.0 -> "Повышенное давление PSI"
            thermal >= 2 -> "Нагрев"
            percent < 15 -> "Мало доступной RAM"
            else -> "Ограничение адаптивного профиля"
        }
        val budget = Policy.budget(s, effective, limits)
        return NativePlan(effective, budget, if (budget == 0L) "Резерв недостаточен для блока" else reason)
    }
}
