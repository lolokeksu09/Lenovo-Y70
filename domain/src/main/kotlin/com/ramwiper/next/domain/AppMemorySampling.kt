package com.ramwiper.next.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import java.util.concurrent.atomic.AtomicBoolean

data class AppMemorySample(val pkg: String, val pssKiB: Long?, val rssKiB: Long?, val swapPssKiB: Long?,
                           val processes: Int, val measuredAt: Long, val issue: String? = null)
data class AppMemoryComparison(val pkg: String, val before: AppMemorySample?, val after: AppMemorySample?) {
    val pssDeltaKiB: Long? get() {
        val a = before?.pssKiB ?: return null
        val b = after?.pssKiB ?: return null
        return b - a
    }
}
data class SamplingResult(val samples: List<AppMemorySample>, val cancelled: Boolean)
class AppMemorySampler(
    private val catalog: AppRepository,
    private val access: PrivilegedAccess,
    private val clock: () -> Long = System::currentTimeMillis
) {
    companion object { const val BATCH_LIMIT = 20 }
    suspend fun measure(packages: Collection<String>, stop: AtomicBoolean,
                        progress: (Int, Int) -> Unit = { _, _ -> }): SamplingResult {
        val eligible = catalog.read().map { it.pkg }.toSet()
        val targets = packages.distinct().filter { it in eligible }.take(BATCH_LIMIT)
        val results = mutableListOf<AppMemorySample>()
        for ((index, pkg) in targets.withIndex()) {
            coroutineContext.ensureActive()
            if (stop.get() || !access.ready()) break
            progress(index, targets.size)
            val sample = try {
                val report = MeminfoParser.parse(access.memoryInfo(pkg))
                AppMemorySample(pkg, report.totalPssKiB, report.totalRssKiB, report.totalSwapPssKiB,
                    report.processes.size, clock(), if (report.totalPssKiB == null) "Нет подтверждённого PSS" else null)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { AppMemorySample(pkg, null, null, null, 0, clock(), "Ошибка замера") }
            coroutineContext.ensureActive()
            if (stop.get()) break
            results += sample
            progress(index + 1, targets.size)
        }
        return SamplingResult(results, stop.get() || results.size != targets.size)
    }
}
object AppMemoryRanking {
    fun order(samples: Collection<AppMemorySample>): List<AppMemorySample> =
        samples.sortedWith(compareByDescending<AppMemorySample> { it.pssKiB != null }
            .thenByDescending { it.pssKiB ?: 0L }.thenBy { it.pkg })
}
