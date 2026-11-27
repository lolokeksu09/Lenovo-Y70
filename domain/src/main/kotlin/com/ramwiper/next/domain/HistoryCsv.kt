package com.ramwiper.next.domain

import java.time.Instant

/** A rectangular UTF-8 CSV; one operation per row, comparisons in the final column. */
object HistoryCsv {
    private val columns = listOf("time_utc","engine","profile","trigger","outcome","ram_before_bytes",
        "ram_after_bytes","ram_settled_bytes","settled_delta_bytes","allocated_bytes","swap_delta_bytes",
        "duration_ms","commands_accepted","commands_attempted","cancelled","note","app_pss_comparisons_kib","effective_native_profile","planned_native_bytes","native_decision","available_percent_before")
    private fun cell(value: Any?): String {
        if (value == null) return ""
        val text = value.toString()
        // Protect textual cells when opening CSV in spreadsheet software; numeric negatives remain numeric.
        val safe = if (value is String && (text.trimStart().firstOrNull() in listOf('=', '+', '-', '@') ||
                text.firstOrNull() in listOf('\t', '\r', '\n'))) "'" + text else text
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }
    fun encode(records: List<CleanupResult>): String = buildString {
        append(columns.joinToString(",")); append("\r\n")
        records.forEach { r ->
            val compare = r.comparisons.joinToString("; ") { c ->
                "${c.pkg}: ${c.before?.pssKiB ?: "?"} -> ${c.after?.pssKiB ?: "?"} (delta ${c.pssDeltaKiB ?: "?"})"
            }
            val row = listOf(Instant.ofEpochMilli(r.time).toString(), r.engine, r.profile, r.trigger.name, r.outcome.name,
                r.before, r.after, r.settled, r.settledDelta, r.allocated, r.swapDelta, r.durationMs, r.accepted, r.attempted,
                r.cancelled, r.note, compare, r.effectiveNativeProfile, r.plannedNativeBytes, r.nativeDecision, r.availablePercentBefore)
            append(row.joinToString(",") { cell(it) }); append("\r\n")
        }
    }
}
