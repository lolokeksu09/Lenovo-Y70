package com.ramwiper.next

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Process
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

data class AppTarget(val name: String, val pkg: String)
data class ScreenState(val snapshot: Snapshot? = null, val engine: Engine = Engine.HYBRID,
    val profile: Profile = Profile.BALANCED, val targets: List<AppTarget> = emptyList(),
    val selected: Set<String> = emptySet(), val protected: Set<String> = emptySet(),
    val progress: Progress? = null, val history: List<Result> = emptyList(), val message: String = "",
    val diagnostic: String = "", val diagnosticBusy: Boolean = false)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("memory", 0)
    private val monitor = Monitor(app)
    val shizuku = ShizukuEngine(app)
    // Android UID uses a per-user range of 100000; never operate on all users.
    private val controller = MemoryController(monitor, shizuku, Process.myUid() / 100000)
    private val stop = AtomicBoolean(false)
    val state = MutableStateFlow(ScreenState(
        engine = runCatching { Engine.valueOf(prefs.getString("engine", "HYBRID")!!) }.getOrDefault(Engine.HYBRID),
        profile = runCatching { Profile.valueOf(prefs.getString("profile", "BALANCED")!!) }.getOrDefault(Profile.BALANCED),
        protected = prefs.getStringSet("protected", emptySet())?.toSet().orEmpty(), history = loadHistory()))
    init {
        viewModelScope.launch {
            while (true) {
                runCatching { withContext(Dispatchers.IO) { monitor.read() } }.onSuccess { snap -> state.update { it.copy(snapshot = snap) } }
                delay(1500)
            }
        }
        refreshApps()
    }
    fun refreshApps() { viewModelScope.launch {
        val apps = withContext(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val home = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0).map { it.activityInfo.packageName }.toSet()
            val ignore = home + setOf(getApplication<Application>().packageName, "com.ramwiper.app", "moe.shizuku.privileged.api")
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .filter { it.activityInfo.packageName !in ignore && it.activityInfo.applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0 }
                .map { AppTarget(it.loadLabel(pm).toString(), it.activityInfo.packageName) }.distinctBy { it.pkg }.sortedBy { it.name.lowercase() }
        }
        state.update { it.copy(targets = apps, selected = it.selected.intersect(apps.map { a -> a.pkg }.toSet())) }
    } }
    fun engine(value: Engine) { if (state.value.progress != null) return; prefs.edit().putString("engine", value.name).apply(); state.update { it.copy(engine = value) } }
    fun profile(value: Profile) { if (state.value.progress != null) return; prefs.edit().putString("profile", value.name).apply(); state.update { it.copy(profile = value) } }
    fun select(pkg: String, checked: Boolean) { state.update {
        if (it.progress != null || pkg in it.protected || it.targets.none { a -> a.pkg == pkg }) it
        else it.copy(selected = if (checked && it.selected.size < 12) it.selected + pkg else if (!checked) it.selected - pkg else it.selected)
    } }
    fun protect(pkg: String, checked: Boolean) { if (state.value.progress != null) return; state.update {
        it.copy(protected = if (checked) it.protected + pkg else it.protected - pkg, selected = if (checked) it.selected - pkg else it.selected)
    }; prefs.edit().putStringSet("protected", state.value.protected).apply() }
    fun start() {
        val current = state.value
        if (current.progress != null || current.diagnosticBusy) return
        if (current.engine == Engine.SHIZUKU && (!shizuku.ready() || current.selected.isEmpty())) {
            state.update { it.copy(message = "Подключи Shizuku и выбери приложения") }; return
        }
        stop.set(false)
        state.update { it.copy(progress = Progress("Подготовка"), message = "") }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { controller.run(current.engine, current.profile, current.selected - current.protected, stop) { p -> state.update { it.copy(progress = p) } } }
                val history = (listOf(result) + state.value.history).take(100)
                saveHistory(history)
                state.update { it.copy(history = history, message = if (result.cancelled) "Операция отменена" else "Операция завершена") }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
              catch (e: Exception) { state.update { it.copy(message = "Ошибка: ${e.message}") } }
              catch (e: LinkageError) { state.update { it.copy(message = "Нативная библиотека недоступна: ${e.message}") } }
            finally { state.update { it.copy(progress = null) } }
        }
    }
    fun cancel() { stop.set(true) }
    fun inspect(pkg: String) {
        if (state.value.progress != null || state.value.diagnosticBusy || !shizuku.ready() || state.value.targets.none { it.pkg == pkg }) return
        state.update { it.copy(diagnosticBusy = true, diagnostic = "") }
        viewModelScope.launch {
            try { val output = withContext(Dispatchers.IO) { shizuku.memoryInfo(pkg) }; state.update { it.copy(diagnostic = output) } }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { state.update { it.copy(diagnostic = "Ошибка: ${e.message}") } }
            finally { state.update { it.copy(diagnosticBusy = false) } }
        }
    }
    fun closeDiagnostic() { state.update { it.copy(diagnostic = "") } }
    private fun saveHistory(history: List<Result>) {
        val array = JSONArray(); history.forEach { r -> array.put(JSONObject().apply {
            put("time", r.time); put("engine", r.engine); put("profile", r.profile); put("before", r.before); put("after", r.after)
            put("allocated", r.allocated); put("accepted", r.accepted); put("attempted", r.attempted); put("cancelled", r.cancelled); put("note", r.note)
        }) }; prefs.edit().putString("history", array.toString()).apply()
    }
    private fun loadHistory(): List<Result> = runCatching {
        val a = JSONArray(prefs.getString("history", "[]")); (0 until a.length()).map { i -> a.getJSONObject(i).let { r ->
            Result(r.getLong("time"), r.getString("engine"), r.getString("profile"), r.getLong("before"), r.getLong("after"), r.getLong("allocated"), r.getInt("accepted"), r.getInt("attempted"), r.getBoolean("cancelled"), r.getString("note"))
        } }
    }.getOrDefault(emptyList())
    override fun onCleared() { stop.set(true); shizuku.close(); super.onCleared() }
}
