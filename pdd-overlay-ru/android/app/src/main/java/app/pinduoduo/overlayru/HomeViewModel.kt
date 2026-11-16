package app.pinduoduo.overlayru

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.AndroidViewModel
import app.pinduoduo.overlayru.ui.home.HomeBlocker
import app.pinduoduo.overlayru.ui.home.HomeReadiness
import app.pinduoduo.overlayru.ui.home.HomeUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** State adapter for the existing Java translation engine and AccessibilityService. */
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val engine = TranslationEngine.get(app)
    private val handler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    private var polling = false
    private val ticker = object : Runnable {
        override fun run() {
            if (!polling) return
            refresh()
            handler.postDelayed(this, 1500L)
        }
    }

    fun start() {
        polling = true
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    fun stop() {
        polling = false
        handler.removeCallbacks(ticker)
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    fun refresh(message: String? = _state.value.message) {
        val hasPdd = app.packageManager.getLaunchIntentForPackage(PDD_PACKAGE) != null
        val connected = PddAccessibilityService.isConnected()
        val allowed = connected || accessibilityAllowed()
        val blocker = HomeReadiness.resolve(
            hasPdd = hasPdd,
            accessibilityAllowed = allowed,
            serviceConnected = connected,
            modelReady = engine.ready(),
            modelPreparing = engine.preparing(),
            modelOverdue = engine.overdue()
        )
        val previous = _state.value
        _state.value = HomeUiState(
            blocker = blocker,
            isPro = LicenseManager.get(app).state().pro,
            translationEnabled = engine.enabled(),
            floatingButtonEnabled = engine.prefs().getBoolean(TranslationEngine.PREF_FLOAT_BUTTON, true),
            modelError = engine.error(),
            inProgress = previous.inProgress,
            // A resolved blocker must not keep an obsolete instruction visible.
            message = if (previous.blocker == blocker) message else null
        )
    }

    fun checkAgain() {
        refresh(null)
        val snapshot = _state.value
        _state.value = snapshot.copy(
            message = when (snapshot.blocker) {
                HomeBlocker.READY -> "Готово: можно открывать Pinduoduo."
                HomeBlocker.MODEL_LOADING -> "ML Kit ещё готовит китайскую модель."
                else -> "Проверено: " + snapshot.status + "."
            }
        )
    }

    /** For sideloaded Android apps: app-info restricted settings can block Accessibility. */
    fun openAppInfo(activity: Activity) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", activity.packageName, null))
            activity.startActivity(intent)
            refresh("Проверь меню ⋮ → «Разрешить ограниченные настройки» (если оно есть). Затем включи службу Accessibility.")
        } catch (_: ActivityNotFoundException) {
            refresh("Не удалось открыть сведения о приложении. Найди PDD Overlay RU в настройках.")
        } catch (_: SecurityException) {
            refresh("Android запретил открывать сведения о приложении.")
        }
    }

    fun primaryAction(activity: Activity) {
        if (_state.value.inProgress || _state.value.blocker == HomeBlocker.MODEL_LOADING) return
        // Read the real device state again; never trust a stale composable snapshot.
        refresh(message = null)
        when (_state.value.blocker) {
            HomeBlocker.CHECKING -> Unit
            HomeBlocker.PDD_MISSING -> {
                try {
                    activity.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.pinduoduo.com/")))
                    refresh("Открыт официальный сайт Pinduoduo. После установки нажми «Проверить снова».")
                } catch (_: ActivityNotFoundException) {
                    refresh("Не удалось открыть сайт. Установи Pinduoduo из доверенного источника.")
                } catch (_: SecurityException) {
                    refresh("Android не разрешил открыть сайт Pinduoduo.")
                }
            }
            HomeBlocker.ACCESSIBILITY_DISABLED -> {
                try {
                    activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    refresh("Включи «PDD · Перевод экрана» в специальных возможностях Android.")
                } catch (_: ActivityNotFoundException) {
                    refresh("Открой настройки специальных возможностей вручную.")
                } catch (_: SecurityException) {
                    refresh("Android не разрешил открыть настройки специальных возможностей.")
                }
            }
            HomeBlocker.SERVICE_DISCONNECTED -> {
                refresh()
                if (_state.value.blocker == HomeBlocker.SERVICE_DISCONNECTED) {
                    try {
                        activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        refresh("Разрешение есть, но служба не подключилась. Проверь её состояние в Android.")
                    } catch (_: Exception) {
                        refresh("Не удалось открыть настройки. Проверь подключение службы вручную.")
                    }
                }
            }
            HomeBlocker.MODEL_LOADING -> Unit
            HomeBlocker.MODEL_OVERDUE -> {
                engine.inspectModels { refresh("Проверка локальных моделей: ${it.take(140)}") }
                refresh("Проверяем локальные модели без повторной загрузки.")
            }
            HomeBlocker.MODEL_MISSING -> {
                engine.prefs().edit().putBoolean(TranslationEngine.PREF_MODEL_ALLOWED, true).apply()
                engine.prepare { refresh() }
                refresh("Запрос подготовки китайской модели передан ML Kit.")
            }
            HomeBlocker.READY -> {
                _state.value = _state.value.copy(inProgress = true, message = null)
                val before = engine.enabled()
                try {
                    // Turn the translator on before foregrounding Pinduoduo.
                    if (!before) engine.prefs().edit().putBoolean(TranslationEngine.PREF_ENABLED, true).apply()
                    PddAccessibilityService.refreshRunningService()
                    val launch = activity.packageManager.getLaunchIntentForPackage(PDD_PACKAGE)
                        ?: throw ActivityNotFoundException("Pinduoduo launch activity unavailable")
                    activity.startActivity(launch)
                    refresh(null)
                } catch (_: ActivityNotFoundException) {
                    rollbackEnable(before)
                    refresh("Не удалось открыть Pinduoduo. Проверь установленное приложение.")
                } catch (_: SecurityException) {
                    rollbackEnable(before)
                    refresh("Android запретил запуск Pinduoduo.")
                } finally {
                    _state.value = _state.value.copy(inProgress = false)
                }
            }
        }
    }

    private fun rollbackEnable(previous: Boolean) {
        engine.prefs().edit().putBoolean(TranslationEngine.PREF_ENABLED, previous).apply()
        PddAccessibilityService.refreshRunningService()
    }

    private fun accessibilityAllowed(): Boolean {
        try {
            val enabled = Settings.Secure.getInt(app.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
            val services = Settings.Secure.getString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            if (enabled && AccessibilityServiceIds.contains(
                    services, app.packageName, PddAccessibilityService::class.java.name)) return true
        } catch (_: SecurityException) { /* fallback to AccessibilityManager */ }
        val manager = app.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val mine = ComponentName(app, PddAccessibilityService::class.java)
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { ComponentName.unflattenFromString(it.id) == mine }
    }

    private companion object {
        const val PDD_PACKAGE = "com.xunmeng.pinduoduo"
    }
}
