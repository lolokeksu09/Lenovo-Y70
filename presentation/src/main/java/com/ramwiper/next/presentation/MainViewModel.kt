package com.ramwiper.next.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramwiper.next.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean

class MainViewModel(
    private val monitor: MemorySource,
    private val access: PrivilegedAccess,
    private val cleanup: RunCleanupUseCase,
    private val settingsStore: SettingsRepository,
    private val historyStore: HistoryRepository,
    private val catalog: AppRepository,
    versionName: String,
    private val clock: () -> Long,
    private val background: BackgroundMonitor,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val mutable = MutableStateFlow(ScreenState(versionName = versionName, access = access.status.value))
    val state: StateFlow<ScreenState> = mutable.asStateFlow()

    private var polling: Job? = null
    private var initialLoad: Job? = null
    private var appLoad: Job? = null
    private var diagnosticJob: Job? = null
    private var diagnosticRequest = 0L
    private var stop: AtomicBoolean? = null
    private var foreground = false
    private var nextRunAt = 0L
    private val autoGate = AutomationGate()

    init {
        viewModelScope.launch { access.status.collect { status -> mutable.update { it.copy(access = status) } } }
        loadInitial()
        refreshApps()
    }

    fun loadInitial() {
        if (state.value.settingsLoaded || initialLoad?.isActive == true) return
        mutable.update { it.copy(initialLoading = true, initialError = null) }
        initialLoad = viewModelScope.launch {
            try {
                val (settings, history) = withContext(io) { settingsStore.load() to historyStore.load() }
                mutable.update { it.copy(settings = settings, history = history, settingsLoaded = true,
                    initialLoading = false, settingsBusy = true, selected = SelectionPolicy.eligible(it.selected, settings.protected, it.targets)) }
                try { syncBackground(settings.automation.backgroundAlerts) }
                finally { mutable.update { it.copy(settingsBusy = false) } }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(initialLoading = false, initialError = e.message ?: "Не удалось загрузить данные") } }
        }
    }

    fun setVisible(visible: Boolean) {
        foreground = visible
        if (!visible) { polling?.cancel(); polling = null; stop?.set(true); autoGate.resetSamples(); closeDiagnostic(); return }
        if (polling?.isActive == true) return
        polling = viewModelScope.launch {
            while (isActive) {
                try {
                    val snap = withContext(io) { monitor.read() }
                    mutable.update { it.copy(snapshot = snap, samples = (it.samples + snap.available).takeLast(40),
                        monitorError = null, cooldownSeconds = remainingCooldown()) }
                    val current = state.value
                    val idle = foreground && current.canEdit && !current.diagnosticBusy && remainingCooldown() == 0
                    if (autoGate.observe(snap, current.settings.automation, clock(), idle)) startOperation(true)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { autoGate.resetSamples(); mutable.update { it.copy(monitorError = e.message ?: "Не удалось прочитать память",
                    cooldownSeconds = remainingCooldown()) } }
                delay(2000)
            }
        }
    }

    fun refreshApps() {
        if (appLoad?.isActive == true) return
        mutable.update { it.copy(loadingApps = true, appError = null) }
        appLoad = viewModelScope.launch {
            try {
                val apps = withContext(io) { catalog.read() }
                mutable.update { it.copy(targets = apps, selected = SelectionPolicy.eligible(it.selected, it.protected, apps), loadingApps = false) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(loadingApps = false, appError = e.message ?: "Ошибка списка приложений") } }
        }
    }

    private fun updateSettings(transform: (UserSettings) -> UserSettings) {
        val current = state.value
        if (!current.canEdit) return
        val next = transform(current.settings)
        if (next == current.settings) return
        mutable.update { it.copy(settingsBusy = true, message = "") }
        viewModelScope.launch {
            try {
                withContext(io) { settingsStore.save(next) }
                mutable.update { it.copy(settings = next, selected = SelectionPolicy.eligible(it.selected, next.protected, it.targets)) }
                if (next.automation != current.settings.automation) autoGate.resetSamples()
                if (next.automation.backgroundAlerts != current.settings.automation.backgroundAlerts) syncBackground(next.automation.backgroundAlerts)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(message = e.message ?: "Ошибка сохранения настроек") } }
            finally { mutable.update { it.copy(settingsBusy = false) } }
        }
    }

    private suspend fun syncBackground(enabled: Boolean) {
        try {
            withContext(io) { background.sync(enabled) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { mutable.update { it.copy(message = "Ошибка фонового мониторинга: ${e.message}") } }
    }
    fun setNativeLimits(value: NativeLimits) = updateSettings { it.copy(nativeLimits = value.normalized()) }
    fun setAutomation(value: AutomationSettings) = updateSettings { it.copy(automation = value.normalized()) }
    fun setBackgroundAlerts(enabled: Boolean) = updateSettings { it.copy(automation = it.automation.copy(backgroundAlerts = enabled)) }
    fun notifyPermissionDenied() { mutable.update { it.copy(message = "Уведомления не разрешены. Включи их в системных настройках.") } }
    fun engine(value: Engine) = updateSettings { it.copy(engine = value) }
    fun profile(value: Profile) = updateSettings { it.copy(profile = value) }
    fun select(pkg: String, checked: Boolean) {
        mutable.update {
            if (!it.canEdit) it else it.copy(selected = SelectionPolicy.toggle(it.selected, pkg, checked, it.protected, it.targets))
        }
    }
    fun protect(pkg: String, checked: Boolean) {
        if (state.value.targets.none { it.pkg == pkg }) return
        updateSettings { it.copy(protected = if (checked) it.protected + pkg else it.protected - pkg) }
    }
    fun requestAccess() { if (state.value.progress == null) access.request() }

    private fun remainingCooldown(): Int = ((nextRunAt - clock()).coerceAtLeast(0) + 999).div(1000).toInt()

    fun start() = startOperation(false)
    private fun startOperation(automatic: Boolean) {
        val current = state.value
        if (!foreground || !current.canEdit || current.diagnosticBusy || remainingCooldown() > 0) return
        if (automatic && (current.snapshot?.let { AutomationPolicy.mayAllocate(it, current.settings.automation) } != true)) return
        if (!automatic && current.engine == Engine.SHIZUKU && (!access.ready() || current.selected.isEmpty())) {
            mutable.update { it.copy(message = "Подключи Shizuku и выбери приложения") }; return
        }
        autoGate.recordAttempt(clock())
        val token = AtomicBoolean(false)
        stop = token
        val request = if (automatic) CleanupRequest(current.settings.copy(engine = Engine.NATIVE, profile = Profile.SOFT, nativeLimits = NativeLimits()), emptySet())
            else CleanupRequest(current.settings, current.selected)
        mutable.update { it.copy(progress = Progress(if (automatic) "Автоматическая очистка" else "Подготовка"), message = "") }
        viewModelScope.launch {
            try {
                val result = withContext(io) {
                    cleanup(request, token) { p ->
                        mutable.update { it.copy(progress = p) }
                    }.copy(trigger = if (automatic) CleanupTrigger.AUTOMATIC else CleanupTrigger.MANUAL)
                }
                val history = (listOf(result) + state.value.history).take(100)
                var saveError: String? = null
                try { withContext(io) { historyStore.save(history) } }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { saveError = e.message ?: "Журнал не сохранён" }
                mutable.update { it.copy(history = history, message = listOfNotNull(result.outcome.label(), saveError).joinToString(" · ")) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(message = "Ошибка: ${e.message}") } }
            catch (e: LinkageError) { mutable.update { it.copy(message = "Нативная библиотека недоступна: ${e.message}") } }
            finally {
                stop = null
                nextRunAt = clock() + 15_000
                mutable.update { it.copy(progress = null, cooldownSeconds = remainingCooldown()) }
            }
        }
    }

    fun cancel() { stop?.set(true) }
    fun inspect(pkg: String) {
        val current = state.value
        if (!foreground || current.progress != null || current.diagnosticBusy || !access.ready() || current.targets.none { it.pkg == pkg }) return
        val requestId = ++diagnosticRequest
        mutable.update { it.copy(diagnosticBusy = true, diagnostic = "", diagnosticReport = null, diagnosticPackage = pkg) }
        diagnosticJob = viewModelScope.launch {
            try {
                val output = withContext(io) {
                    if (catalog.read().none { it.pkg == pkg }) "Приложение больше недоступно"
                    else access.memoryInfo(pkg)
                }
                ensureActive()
                if (requestId == diagnosticRequest) mutable.update { it.copy(diagnostic = output, diagnosticReport = MeminfoParser.parse(output)) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (requestId == diagnosticRequest) mutable.update { it.copy(diagnostic = "Ошибка: ${e.message}") } }
            finally { diagnosticJob = null; mutable.update { it.copy(diagnosticBusy = false) } }
        }
    }
    fun closeDiagnostic() {
        diagnosticRequest++
        // A running Binder call is bounded by the service; hide stale output but keep the single-flight gate until it finishes.
        mutable.update { it.copy(diagnostic = "", diagnosticReport = null, diagnosticPackage = null) }
    }
    override fun onCleared() {
        stop?.set(true)
        polling?.cancel()
        appLoad?.cancel()
        initialLoad?.cancel()
        diagnosticJob?.cancel()
        runCatching { access.close() }
        super.onCleared()
    }
}
