package app.pinduoduo.overlayru

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.pinduoduo.overlayru.ui.design.PddFrame
import app.pinduoduo.overlayru.ui.design.PddNav
import app.pinduoduo.overlayru.ui.design.PddTab
import java.lang.ref.WeakReference

/** Compose settings. Local model consent, Accessibility and PRO remain unchanged. */
class MainActivity : ComponentActivity() {
    private companion object {
        const val TAG = "PddOverlayRU"
        const val PDD_PACKAGE = "com.xunmeng.pinduoduo"
    }

    private lateinit var engine: TranslationEngine
    private val handler = Handler(Looper.getMainLooper())
    private var state by mutableStateOf(SettingsSnapshot())
    private var polling = false
    private val ticker = object : Runnable {
        override fun run() {
            if (!polling || isFinishing || isDestroyed) return
            updateStatus()
            handler.postDelayed(this, 1500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.statusBarColor = AndroidColor.rgb(6,8,13)
        @Suppress("DEPRECATION")
        window.navigationBarColor = AndroidColor.rgb(6,8,13)
        engine = TranslationEngine.get(this)
        updateStatus()
        setContent {
            PddFrame(PddTab.SETTINGS, onChoose = { PddNav.open(this,it) }) {
                PremiumSettingsScreen(
                    state=state,
                    onEnableTranslation={ checked ->
                        engine.prefs().edit()
                            .putBoolean(TranslationEngine.PREF_ENABLED,checked).apply()
                        PddAccessibilityService.refreshRunningService()
                        updateStatus()
                    },
                    onFloatingButton={ checked ->
                        engine.prefs().edit()
                            .putBoolean(TranslationEngine.PREF_FLOAT_BUTTON,checked).apply()
                        PddAccessibilityService.refreshRunningService()
                        updateStatus()
                    },
                    onWifiOnly={ checked ->
                        engine.prefs().edit()
                            .putBoolean(TranslationEngine.PREF_WIFI_ONLY,checked).apply()
                        updateStatus()
                    },
                    onAccessibility={ safeLaunch(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                        "Не удалось открыть специальные возможности Android"
                    ) },
                    onAppInfo={ safeLaunch(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package",packageName,null)),
                        "Не удалось открыть сведения о приложении"
                    ) },
                    onDownloadChinese={ downloadChinese() },
                    onDownloadEnglish={ downloadEnglish() },
                    onInspectModels={ inspectModels() },
                    onLicense={ startActivity(Intent(this,LicenseActivity::class.java)) },
                    onStore={ openPinduoduo() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        polling=true
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        polling=false
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    override fun onDestroy() {
        polling=false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun updateStatus() {
        if (!::engine.isInitialized || isDestroyed) return
        state = SettingsSnapshot(
            accessibilityAllowed=hasService(),
            accessibilityConnected=PddAccessibilityService.isConnected(),
            translationEnabled=engine.enabled(),
            floatingButtonEnabled=engine.prefs()
                .getBoolean(TranslationEngine.PREF_FLOAT_BUTTON,true),
            wifiOnly=engine.wifiOnly(),
            chineseReady=engine.ready(),
            chinesePreparing=engine.preparing(),
            chineseOverdue=engine.overdue(),
            chineseConsent=engine.prefs()
                .getBoolean(TranslationEngine.PREF_MODEL_ALLOWED,false),
            chineseError=engine.error(),
            englishReady=engine.englishReady(),
            englishPreparing=engine.englishPreparing(),
            englishOverdue=engine.englishOverdue(),
            englishError=engine.englishError(),
            inspecting=engine.inspecting(),
            inspection=engine.inspection(),
            vpnActive=engine.vpnActive(),
            translationFailures=engine.translationFailures(),
            pro=LicenseManager.get(this).state().pro,
            shizukuInstalled=isInstalled("moe.shizuku.privileged.api")
        )
    }

    private fun downloadChinese() {
        when {
            engine.ready() -> toast("Китайская модель уже доступна офлайн")
            engine.preparing() -> {
                toast("ML Kit ещё выполняет предыдущий запрос; новая загрузка не запущена")
                if (engine.overdue()) inspectModels()
            }
            else -> {
                engine.prefs().edit()
                    .putBoolean(TranslationEngine.PREF_MODEL_ALLOWED,true).apply()
                engine.prepare(Runnable { runOnUiThread { updateStatus() } })
                updateStatus()
            }
        }
    }

    private fun downloadEnglish() {
        when {
            engine.englishReady() -> toast("Английская модель уже доступна офлайн")
            engine.englishPreparing() -> {
                toast("ML Kit ещё выполняет предыдущий запрос; новая загрузка не запущена")
                if (engine.englishOverdue()) inspectModels()
            }
            else -> {
                engine.prefs().edit()
                    .putBoolean(TranslationEngine.PREF_EN_MODEL_ALLOWED,true).apply()
                engine.prepareEnglish(Runnable { runOnUiThread { updateStatus() } })
                updateStatus()
            }
        }
    }

    private fun inspectModels() {
        Log.i(TAG,"action inspect downloaded models")
        val owner=WeakReference(this)
        state=state.copy(inspecting=true,inspection="Проверяем локальные модели…")
        engine.inspectModels { result ->
            owner.get()?.let { activity ->
                if (!activity.isDestroyed) {
                    activity.runOnUiThread {
                        activity.updateStatus()
                        activity.state=activity.state.copy(inspection=result)
                    }
                }
            }
        }
        handler.postDelayed({
            if (!isDestroyed && state.inspecting) {
                state=state.copy(inspection="ML Kit долго не отвечает. Проверь VPN и связь.")
            }
        },20_000L)
    }

    private fun openPinduoduo() {
        val intent=packageManager.getLaunchIntentForPackage(PDD_PACKAGE)
        if (intent==null) {
            toast("Pinduoduo не установлен")
        } else safeLaunch(intent,"Android не разрешил открыть Pinduoduo")
    }

    private fun safeLaunch(intent: Intent,error: String) {
        try { startActivity(intent) }
        catch (_: ActivityNotFoundException) { toast(error) }
        catch (_: SecurityException) { toast(error) }
    }

    private fun hasService(): Boolean {
        if (PddAccessibilityService.isConnected()) return true
        // Read secure settings only; never write them or silently grant permission.
        try {
            val global = Settings.Secure.getInt(
                contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0
            )
            val services = Settings.Secure.getString(
                contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (global == 1 && AccessibilityServiceIds.contains(
                    services, packageName, PddAccessibilityService::class.java.name
                )) return true
        } catch (error: SecurityException) {
            Log.w(TAG, "Cannot read accessibility secure setting", error)
        }

        val manager = getSystemService(ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val mine = ComponentName(this, PddAccessibilityService::class.java)
        for (info in manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )) {
            val listed = ComponentName.unflattenFromString(info.id)
            if (mine == listed) return true
        }
        return false
    }

    private fun isInstalled(pkg: String): Boolean =
        try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }


    private fun toast(message: String) {
        Toast.makeText(this,message,Toast.LENGTH_LONG).show()
    }
}

internal data class SettingsSnapshot(
    val accessibilityAllowed: Boolean=false,
    val accessibilityConnected: Boolean=false,
    val translationEnabled: Boolean=false,
    val floatingButtonEnabled: Boolean=true,
    val wifiOnly: Boolean=false,
    val chineseReady: Boolean=false,
    val chinesePreparing: Boolean=false,
    val chineseOverdue: Boolean=false,
    val chineseConsent: Boolean=false,
    val chineseError: String="",
    val englishReady: Boolean=false,
    val englishPreparing: Boolean=false,
    val englishOverdue: Boolean=false,
    val englishError: String="",
    val inspecting: Boolean=false,
    val inspection: String="",
    val vpnActive: Boolean=false,
    val translationFailures: Int=0,
    val pro: Boolean=false,
    val shizukuInstalled: Boolean=false
)
