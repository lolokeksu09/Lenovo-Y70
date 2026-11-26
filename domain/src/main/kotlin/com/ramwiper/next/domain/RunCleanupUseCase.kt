package com.ramwiper.next.domain

import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

data class CleanupRequest(val settings: UserSettings, val selected: Set<String>)

class RunCleanupUseCase(private val runner: CleanupRunner, private val catalog: AppRepository) {
    suspend operator fun invoke(request: CleanupRequest, stop: AtomicBoolean,
                                progress: (Progress) -> Unit): CleanupResult {
        val settings = request.settings
        var warning: String? = null
        val targets = if (settings.engine == Engine.NATIVE || request.selected.isEmpty() || stop.get()) emptySet()
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
        val result = runner.run(settings.engine, settings.profile, targets, stop, progress)
        return if (warning == null) result else result.copy(note = listOf(result.note, warning).filter { !it.isNullOrBlank() }.joinToString("; "))
    }
}
