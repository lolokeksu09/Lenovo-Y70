package com.ramwiper.next.domain

/** Values in KiB as printed by Android dumpsys meminfo. Null means unavailable, not zero. */
data class ProcessMemory(
    val pid: Int?,
    val name: String?,
    val pssKiB: Long,
    val rssKiB: Long?,
    val swapPssKiB: Long?
)
data class AppMemoryReport(val processes: List<ProcessMemory>, val raw: String) {
    val totalPssKiB: Long? get() = processes.takeIf { it.isNotEmpty() }?.sumOf { it.pssKiB }
    val totalRssKiB: Long? get() = processes.takeIf { it.isNotEmpty() && it.all { p -> p.rssKiB != null } }?.sumOf { requireNotNull(it.rssKiB) }
    val totalSwapPssKiB: Long? get() = processes.takeIf { it.isNotEmpty() && it.all { p -> p.swapPssKiB != null } }?.sumOf { requireNotNull(it.swapPssKiB) }
}

/** Prefer per-process App Summary. Never count the legacy table and summary twice. */
object MeminfoParser {
    private val header = Regex("""^\s*\*\*\s*MEMINFO in pid (\d+)\s*\[([^]]+)]""")
    private val pss = Regex("""\bTOTAL PSS:\s*(\d+)""")
    private val rss = Regex("""\bTOTAL RSS:\s*(\d+)""")
    private val swap = Regex("""\bTOTAL SWAP PSS:\s*(\d+)""")
    // Old Android table: TOTAL followed by the Pss column. Do not infer RSS/Swap column offsets.
    private val legacy = Regex("""^\s*TOTAL\s+(\d+)(?:\s|$)""")
    fun parse(raw: String): AppMemoryReport {
        if (raw.startsWith("Ошибка") || raw.startsWith("Недопустимый") ||
            raw.startsWith("Shizuku") || raw.startsWith("Вывод усечён"))
            return AppMemoryReport(emptyList(), raw)
        val reports = mutableListOf<ProcessMemory>()
        var pid: Int? = null
        var name: String? = null
        var legacyPss: Long? = null
        var summaryPss: Long? = null
        var summaryRss: Long? = null
        var summarySwap: Long? = null
        fun flush() {
            val p = summaryPss ?: legacyPss
            if (pid != null && p != null) reports += ProcessMemory(pid, name, p, summaryRss, summarySwap)
            legacyPss = null; summaryPss = null; summaryRss = null; summarySwap = null
        }
        fun Regex.value(line: String): Long? = find(line)?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it >= 0 }
        raw.lineSequence().forEach { line ->
            val h = header.find(line)
            if (h != null) {
                flush()
                pid = h.groupValues[1].toIntOrNull()
                name = h.groupValues[2]
            } else if (pid != null) {
                pss.value(line)?.let { summaryPss = it }
                rss.value(line)?.let { summaryRss = it }
                swap.value(line)?.let { summarySwap = it }
                legacy.value(line)?.let { if (legacyPss == null) legacyPss = it }
            }
        }
        flush()
        return AppMemoryReport(reports.distinctBy { it.pid }, raw)
    }
}
