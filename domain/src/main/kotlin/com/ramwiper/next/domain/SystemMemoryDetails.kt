package com.ramwiper.next.domain

/** Independent kernel counters. Do not sum them into an exclusive memory breakdown. */
data class SystemMemoryDetails(
    val freeBytes: Long? = null,
    val cachedBytes: Long? = null,
    val reclaimableSlabBytes: Long? = null,
    val anonymousBytes: Long? = null,
    val dirtyBytes: Long? = null,
    val swapCachedBytes: Long? = null,
    val psiFullAvg10: Double? = null
)
object KernelMemoryParser {
    private val keys = setOf("MemFree", "Cached", "SReclaimable", "AnonPages", "Dirty", "SwapCached", "SwapTotal", "SwapFree")
    private val line = Regex("""^([A-Za-z_]+):\s*(\d+)\s+kB\s*$""")
    fun meminfo(lines: Sequence<String>): Map<String, Long> = lines.mapNotNull { text ->
        val match = line.matchEntire(text) ?: return@mapNotNull null
        val key = match.groupValues[1]
        val kib = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
        if (key in keys && kib in 0..Long.MAX_VALUE/1024) key to kib*1024 else null
    }.toMap()
    fun pressureAvg10(lines: List<String>, type: String): Double? {
        val raw = lines.firstOrNull { it.startsWith("$type ") } ?: return null
        return Regex("""(?:^|\s)avg10=([^\s]+)""").find(raw)?.groupValues?.get(1)
            ?.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..100.0 }
    }
}
