package com.ramwiper.next.data

import com.ramwiper.next.domain.*
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import java.io.File

class Monitor(context: Context) : MemorySource {
    private val am = requireNotNull(context.getSystemService(ActivityManager::class.java))
    private val power = requireNotNull(context.getSystemService(PowerManager::class.java))
    override fun read(): Snapshot {
        val m = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val mem = runCatching { File("/proc/meminfo").useLines { KernelMemoryParser.meminfo(it) } }.getOrNull().orEmpty()
        val pressure = runCatching { File("/proc/pressure/memory").readLines() }.getOrNull().orEmpty()
        val thermal = if (Build.VERSION.SDK_INT >= 29) runCatching { power.currentThermalStatus }.getOrNull() else null
        val swapTotal = mem["SwapTotal"]
        val swapFree = mem["SwapFree"]?.takeIf { swapTotal != null && it <= swapTotal }
        return Snapshot(m.totalMem, m.availMem, m.threshold, m.lowMemory, swapTotal, swapFree, thermal,
            KernelMemoryParser.pressureAvg10(pressure, "some"), SystemMemoryDetails(
                mem["MemFree"], mem["Cached"], mem["SReclaimable"], mem["AnonPages"], mem["Dirty"],
                mem["SwapCached"], KernelMemoryParser.pressureAvg10(pressure,"full")))
    }
}
