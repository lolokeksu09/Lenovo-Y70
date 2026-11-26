package com.ramwiper.next

import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import java.util.concurrent.atomic.AtomicBoolean

data class Progress(val stage: String, val allocated: Long = 0, val target: Long = 0)
data class Result(val time: Long, val engine: String, val profile: String, val before: Long, val after: Long,
                  val allocated: Long, val accepted: Int, val attempted: Int, val cancelled: Boolean, val note: String) {
    val delta get() = after - before
}
class MemoryController(private val monitor: Monitor, private val shell: ShizukuEngine, private val userId: Int) {
    suspend fun run(engine: Engine, profile: Profile, packages: Set<String>, stop: AtomicBoolean,
                    progress: (Progress) -> Unit): Result {
        val before = monitor.read()
        var allocated = 0L; var accepted = 0; var attempted = 0
        val notes = mutableListOf<String>()
        if (engine != Engine.NATIVE) {
            if (!shell.ready()) notes += "Shizuku недоступен"
            else {
                progress(Progress("Выборочная остановка"))
                for (pkg in packages.take(12)) {
                    coroutineContext.ensureActive()
                    if (stop.get()) break
                    attempted++
                    if (runCatching { shell.kill(pkg, userId) }.getOrDefault(-4) == 0) accepted++
                    delay(100)
                }
                if (packages.isEmpty()) notes += "Приложения не выбраны"
                if (accepted < attempted) notes += "Не все команды выполнены"
                if (attempted > 0) delay(350)
            }
        }
        val middle = monitor.read()
        val native = engine == Engine.NATIVE || (engine == Engine.HYBRID && Policy.shouldRunNative(before, middle))
        if (native && !stop.get()) {
            val budget = Policy.budget(middle, profile)
            val session = NativeWiper.Session()
            try {
                if (budget == 0L) notes += "Недостаточный резерв или высокое давление/нагрев"
                while (!stop.get() && allocated < budget) {
                    coroutineContext.ensureActive()
                    val size = Policy.next(monitor.read(), budget - allocated)
                    if (size == 0) { notes += "Сработало защитное ограничение"; break }
                    if (!session.add(size)) { notes += "Rust: не удалось выделить блок"; break }
                    allocated += size
                    progress(Progress("Native: выделение памяти", allocated, budget))
                    delay(35)
                }
                if (!stop.get() && allocated > 0 && Policy.safe(monitor.read())) delay(profile.holdMs)
            } finally {
                progress(Progress("Освобождение памяти", allocated, budget))
                session.close()
            }
        }
        progress(Progress("Измерение результата"))
        delay(300)
        val after = monitor.read()
        return Result(System.currentTimeMillis(), engine.name, profile.name, before.available, after.available,
            allocated, accepted, attempted, stop.get(), notes.distinct().joinToString("; "))
    }
}
