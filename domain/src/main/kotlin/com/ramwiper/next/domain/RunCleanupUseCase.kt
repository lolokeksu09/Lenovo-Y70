package com.ramwiper.next.domain

import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

data class CleanupRequest(val settings: UserSettings, val selected: Set<String>)

class RunCleanupUseCase(private val runner: CleanupRunner, private val catalog: AppRepository, private val sampler: AppMemorySampler? = null) {
    suspend operator fun invoke(request: CleanupRequest, stop: AtomicBoolean,
                                progress: (Progress) -> Unit): CleanupResult {
        val settings = request.settings
        var warning: String? = null
        val targets = if ((settings.engine == Engine.NATIVE && !settings.compareSelected) || request.selected.isEmpty() || stop.get()) emptySet()
        else try {
            val eligible = SelectionPolicy.eligible(request.selected, settings.protected, catalog.read())
            if (eligible.size != request.selected.size) warning = "Недоступные или исключённые приложения пропущены"
            eligible
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            warning = "Не удалось проверить список приложений; команды Shizuku не выполняются"
            emptySet()
        }
        val notes = mutableListOf<String>()
        warning?.let { notes += it }
        suspend fun sample(stage: String): List<AppMemorySample> {
            if (sampler == null || targets.isEmpty() || stop.get()) return emptyList()
            return try {
                val measured = sampler.measure(targets, stop) { done, total -> progress(Progress("$stage: $done/$total")) }
                if (measured.cancelled) notes += "$stage: замер неполный"
                measured.samples
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { notes += "$stage: замер недоступен"; emptyList() }
        }
        val before = if (settings.compareSelected) sample("PSS до очистки") else emptyList()
        val result = runner.run(settings.engine, settings.profile,
            if (settings.engine == Engine.NATIVE) emptySet() else targets, stop, settings.nativeLimits, progress)
        val after = if (before.isNotEmpty() && !stop.get() &&
            result.outcome in setOf(Outcome.COMPLETED, Outcome.PARTIAL)) sample("PSS после очистки") else emptyList()
        val afterByPackage = after.associateBy { it.pkg }
        val comparisons = before.map { AppMemoryComparison(it.pkg, it, afterByPackage[it.pkg]) }
        if (settings.compareSelected && before.isEmpty()) notes += "Сравнение PSS недоступно"
        return result.copy(comparisons = comparisons, note = (listOf(result.note) + notes)
            .filter { it.isNotBlank() }.joinToString("; "))
    }
}
