package com.ramwiper.next

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import java.io.File

class Monitor(context: Context) {
    private val am = requireNotNull(context.getSystemService(ActivityManager::class.java))
    private val power = requireNotNull(context.getSystemService(PowerManager::class.java))
    fun read(): Snapshot {
        val m = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val swap = runCatching { File("/proc/meminfo").useLines { lines ->
            lines.mapNotNull { line ->
                val k = line.substringBefore(':')
                if (k !in setOf("SwapTotal", "SwapFree")) null else
                    line.substringAfter(':').trim().substringBefore(' ').toLongOrNull()?.let { k to it * 1024 }
            }.toMap()
        } }.getOrNull()
        val psi = runCatching { File("/proc/pressure/memory").useLines { lines ->
            lines.firstOrNull { it.startsWith("some ") }?.let { Regex("avg10=([0-9.]+)").find(it)?.groupValues?.get(1)?.toDoubleOrNull() }
        } }.getOrNull()
        val thermal = if (Build.VERSION.SDK_INT >= 29) power.currentThermalStatus else null
        return Snapshot(m.totalMem, m.availMem, m.threshold, m.lowMemory, swap?.get("SwapTotal"), swap?.get("SwapFree"), thermal, psi)
    }
}
