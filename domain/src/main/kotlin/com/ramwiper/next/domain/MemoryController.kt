package com.ramwiper.next.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import java.util.concurrent.atomic.AtomicBoolean

class MemoryController(
    private val monitor: MemorySource,
    private val shell: BackgroundControl,
    private val userId: Int,
    private val openSession: () -> AllocationSession,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 }
) : CleanupRunner {
    override suspend fun run(engine: Engine, profile: Profile, packages: Set<String>, stop: AtomicBoolean,
                    limits: NativeLimits, progress: (Progress) -> Unit): CleanupResult {
        val start = clock()
        val before = monitor.read()
        val notes = mutableListOf<String>()
        var allocated = 0L
        var attempted = 0
        var accepted = 0
        var nativePlan: NativePlan? = null
        var partial = false
        var failed = false
        fun active() = !stop.get()
        if (engine != Engine.NATIVE && active()) {
            when {
                packages.isEmpty() -> notes += "Приложения не выбраны"
                !shell.ready() -> notes += "Shizuku недоступен"
                else -> {
                    progress(Progress("Остановка выбранных приложений"))
                    for (pkg in packages.take(SelectionPolicy.LIMIT)) {
                        coroutineContext.ensureActive()
                        if (!active()) break
                        if (!shell.ready()) { notes += "Shizuku отключён"; partial = true; break }
                        attempted++
                        val code = try { shell.kill(pkg, userId) }
                            catch (e: kotlinx.coroutines.CancellationException) { throw e }
                            catch (_: Exception) { -4 }
                        if (code == 0) accepted++ else { partial = true; notes += "Команда не принята: $pkg ($code)" }
                        if (active()) pause(100)
                    }
                    if (attempted > 0 && active()) pause(350)
                }
            }
        }
        val middle = monitor.read()
        val useNative = engine == Engine.NATIVE || engine == Engine.HYBRID &&
            (accepted == 0 || Policy.shouldRunNative(before, middle))
        if (useNative && active()) {
            val plan = AdaptivePolicy.plan(middle, profile, limits)
            nativePlan = plan
            val budget = plan.budget
            if (budget == 0L) notes += if (limits.adaptive || Policy.blockReason(middle) != null) plan.reason else "Резерв недостаточен для блока"
            else {
                try {
                    openSession().use { session ->
                        try {
                            while (active() && allocated < budget) {
                                coroutineContext.ensureActive()
                                val current = monitor.read()
                                val size = Policy.next(current, budget - allocated)
                                if (size == 0) { partial = true; notes += Policy.blockReason(current) ?: "Достигнут резерв"; break }
                                if (!session.add(size)) { failed = true; notes += "Rust: блок не выделен"; break }
                                allocated += size
                                progress(Progress("Выделение памяти", allocated, budget))
                                if (active()) pause(35)
                            }
                            var hold = limits.hold(plan.profile)
                            while (active() && allocated > 0 && hold > 0 && !failed) {
                                if (!Policy.safe(monitor.read())) { partial = true; notes += "Удержание прекращено защитой"; break }
                                val interval = minOf(hold, 50)
                                pause(interval)
                                hold -= interval
                            }
                        } finally { progress(Progress("Освобождение памяти", allocated, budget)) }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                  catch (e: Exception) { failed = true; notes += "Rust: ${e.message ?: "ошибка"}" }
                  catch (e: LinkageError) { failed = true; notes += "Библиотека Rust недоступна" }
            }
        }
        progress(Progress("Измерение результата"))
        val acted = allocated > 0 || accepted > 0
        if (active() && acted) pause(300)
        val after = monitor.read()
        var settled: Snapshot? = null
        if (acted && active()) {
            progress(Progress("Контрольное измерение (3 с)"))
            repeat(30) { if (active()) pause(100) }
            if (active()) settled = monitor.read()
        }
        val cancelledDuringCleanup = !active()
        var extended: Snapshot? = null
        if (limits.extendedCheck && settled != null && !cancelledDuringCleanup) {
            progress(Progress("Дополнительный замер (10 с)"))
            // No allocations remain held. Seven seconds after the existing 3-second sample.
            repeat(70) { if (active()) pause(100) }
            if (active()) {
                try { extended = monitor.read().takeIf { it.total > 0 && it.available in 0..it.total }
                    if (extended == null) notes += "Некорректный дополнительный замер" }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { notes += "Дополнительный замер недоступен" }
            } else notes += "Дополнительный замер отменён"
        }
        val outcome = when {
            cancelledDuringCleanup -> Outcome.CANCELLED
            failed && !acted -> Outcome.FAILED
            failed || attempted > accepted -> if (acted) Outcome.PARTIAL else Outcome.FAILED
            partial -> if (acted) Outcome.PARTIAL else Outcome.SKIPPED
            !acted -> Outcome.SKIPPED
            else -> Outcome.COMPLETED
        }
        fun swapUsed(s: Snapshot): Long? = if (s.swapTotal != null && s.swapFree != null) s.swapTotal - s.swapFree else null
        val swapBefore = swapUsed(before)
        val swapAfter = swapUsed(settled ?: after)
        return CleanupResult(System.currentTimeMillis(), engine.name, profile.name, before.available, after.available,
            allocated, accepted, attempted, cancelledDuringCleanup, notes.distinct().joinToString("; "), outcome,
            settled?.available, (clock()-start).coerceAtLeast(0),
            if (swapBefore != null && swapAfter != null) swapAfter - swapBefore else null,
            effectiveNativeProfile = nativePlan?.takeIf { allocated > 0 }?.profile?.name,
            plannedNativeBytes = nativePlan?.budget, nativeDecision = nativePlan?.reason,
            availablePercentBefore = if (before.total > 0 && before.available in 0..before.total) before.available.toDouble()/before.total*100 else null,
            extendedAvailable = extended?.available,
            extendedSwapDelta = extended?.let { swapUsed(it) }?.let { value -> swapBefore?.let { value - it } })
    }
}
