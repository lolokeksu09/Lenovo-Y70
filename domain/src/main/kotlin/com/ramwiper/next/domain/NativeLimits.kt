package com.ramwiper.next.domain

/** Optional lower limits for manual operations. Never exceed a selected profile. */
data class NativeLimits(val capMiB: Int = 0, val holdMs: Int = -1) {
    companion object {
        val CAPS = listOf(0, 64, 128, 256, 512, 768)
        val HOLDS = listOf(-1, 0, 50, 100, 250, 350)
    }
    fun normalized() = NativeLimits(
        capMiB.takeIf { it in CAPS } ?: 0,
        holdMs.takeIf { it in HOLDS } ?: -1
    )
    fun cap(profile: Profile): Long = normalized().capMiB.let {
        if (it == 0) profile.capMiB else minOf(profile.capMiB, it.toLong())
    }
    fun hold(profile: Profile): Long = normalized().holdMs.let {
        if (it == -1) profile.holdMs else minOf(profile.holdMs, it.toLong())
    }
}
