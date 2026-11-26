package com.ramwiper.next

import android.app.Application
import android.os.Process
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

data class AppTarget(val name: String, val pkg: String)
data class ScreenState(val snapshot: Snapshot? = null, val engine: Engine = Engine.HYBRID,
    val profile: Profile = Profile.BALANCED, val targets: List<AppTarget> = emptyList(),
    val selected: Set<String> = emptySet(), val protected: Set<String> = emptySet(),
    val progress: Progress? = null, val history: List<Result> = emptyList(), val message: String = "",
    val diagnostic: String = "", val diagnosticBusy: Boolean = false,
    val samples: List<Long> = emptyList(), val loadingApps: Boolean = true, val appError: String? = null, val monitorError: String? = null, val cooldownSeconds: Int = 0)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("memory", 0)
    private val monitor = Monitor(app)
    val shizuku = ShizukuEngine(app)
    // Android UID uses a per-user range of 100000; never operate on all users.
    private val controller = MemoryController(monitor, shizuku, Process.myUid() / 100000)
    private val stop = AtomicBoolean(false)
    private val historyStore = HistoryStore(prefs)
    private val catalog = AppCatalog(app)
    private var nextRunAt = 0L
    private var polling: Job? = null
    private var appLoad: Job? = null
    private val mutable = MutableStateFlow(ScreenState(
        engine = runCatching { Engine.valueOf(prefs.getString("engine", "HYBRID")!!) }.getOrDefault(Engine.HYBRID),
        profile = runCatching { Profile.valueOf(prefs.getString("profile", "BALANCED")!!) }.getOrDefault(Profile.BALANCED),
        protected = prefs.getStringSet("protected", emptySet())?.toSet().orEmpty(), history = historyStore.load()))
    val state = mutable.asStateFlow()
    init { refreshApps() }
    fun setVisible(visible: Boolean) {
        if (!visible) { polling?.cancel(); polling = null; stop.set(true); return }
        if (polling?.isActive == true) return
        polling = viewModelScope.launch {
            while (true) {
                try {
                    val snap = withContext(Dispatchers.IO) { monitor.read() }
                    mutable.update { it.copy(snapshot = snap, samples = (it.samples + snap.available).takeLast(40), monitorError = null, cooldownSeconds = remainingCooldown()) }
                } catch (e: CancellationException) { throw e }
                  catch (e: Exception) { mutable.update { it.copy(monitorError = e.message ?: "Не удалось прочитать память", cooldownSeconds = remainingCooldown()) } }
                delay(2000)
            }
        }
    }
    fun refreshApps() {
        if (appLoad?.isActive == true) return
        mutable.update { it.copy(loadingApps = true, appError = null) }
        appLoad = viewModelScope.launch {
            try {
                val apps = withContext(Dispatchers.IO) { catalog.read() }
                mutable.update { it.copy(targets = apps, selected = it.selected.intersect(apps.map { a -> a.pkg }.toSet()), loadingApps = false) }
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { mutable.update { it.copy(loadingApps = false, appError = e.message ?: "Ошибка списка приложений") } }
        }
    }
    fun engine(value: Engine) { if (state.value.progress != null) return; prefs.edit().putString("engine", value.name).apply(); mutable.update { it.copy(engine = value) } }
    fun profile(value: Profile) { if (state.value.progress != null) return; prefs.edit().putString("profile", value.name).apply(); mutable.update { it.copy(profile = value) } }
    fun select(pkg: String, checked: Boolean) { mutable.update {
        if (it.progress != null || pkg in it.protected || it.targets.none { a -> a.pkg == pkg }) it
        else it.copy(selected = if (checked && it.selected.size < 12) it.selected + pkg else if (!checked) it.selected - pkg else it.selected)
    } }
    fun protect(pkg: String, checked: Boolean) { if (state.value.progress != null) return; mutable.update {
        it.copy(protected = if (checked) it.protected + pkg else it.protected - pkg, selected = if (checked) it.selected - pkg else it.selected)
    }; prefs.edit().putStringSet("protected", state.value.protected).apply() }
    private fun remainingCooldown(): Int = ((nextRunAt - SystemClock.elapsedRealtime()).coerceAtLeast(0) + 999).div(1000).toInt()
    fun start() {
        val current = state.value
        if (current.progress != null || current.diagnosticBusy || remainingCooldown() > 0) return
        if (current.engine == Engine.SHIZUKU && (!shizuku.ready() || current.selected.isEmpty())) {
            mutable.update { it.copy(message = "Подключи Shizuku и выбери приложения") }; return
        }
        stop.set(false)
        mutable.update { it.copy(progress = Progress("Подготовка"), message = "") }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { controller.run(current.engine, current.profile, current.selected - current.protected, stop) { p -> mutable.update { it.copy(progress = p) } } }
                val history = (listOf(result) + state.value.history).take(100)
                withContext(Dispatchers.IO) { historyStore.save(history) }
                mutable.update { it.copy(history = history, message = result.outcome.label()) }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
              catch (e: Exception) { mutable.update { it.copy(message = "Ошибка: ${e.message}") } }
              catch (e: LinkageError) { mutable.update { it.copy(message = "Нативная библиотека недоступна: ${e.message}") } }
            finally { nextRunAt = SystemClock.elapsedRealtime() + 15_000; mutable.update { it.copy(progress = null, cooldownSeconds = remainingCooldown()) } }
        }
    }
    fun cancel() { stop.set(true) }
    fun inspect(pkg: String) {
        if (state.value.progress != null || state.value.diagnosticBusy || !shizuku.ready() || state.value.targets.none { it.pkg == pkg }) return
        mutable.update { it.copy(diagnosticBusy = true, diagnostic = "") }
        viewModelScope.launch {
            try { val output = withContext(Dispatchers.IO) { shizuku.memoryInfo(pkg) }; mutable.update { it.copy(diagnostic = output) } }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(diagnostic = "Ошибка: ${e.message}") } }
            finally { mutable.update { it.copy(diagnosticBusy = false) } }
        }
    }
    fun closeDiagnostic() { mutable.update { it.copy(diagnostic = "") } }
    override fun onCleared() { polling?.cancel(); appLoad?.cancel(); stop.set(true); shizuku.close(); super.onCleared() }
}
