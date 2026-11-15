package app.pinduoduo.overlayru

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

/**
 * Kotlin port of the existing advanced settings screen.
 *
 * Keep the old controls and their SharedPreferences keys intact. In particular:
 * model downloads require an explicit user action, Accessibility is user-granted,
 * English and Chinese model consents are independent, and PRO never gates translation.
 */
class MainActivity : Activity() {

    private companion object {
        const val TAG = "PddOverlayRU"
        const val PDD_PACKAGE = "com.xunmeng.pinduoduo"
        val BG = Color.rgb(7, 9, 12)
        val SURFACE = Color.rgb(20, 23, 29)
        val TEXT = Color.rgb(244, 245, 248)
        val MUTED = Color.rgb(167, 173, 184)
        val RED = Color.rgb(241, 61, 81)
        val ACCENT = Color.rgb(72, 224, 190)
    }

    private val statusHandler = Handler(Looper.getMainLooper())
    private lateinit var engine: TranslationEngine

    private var serviceValue: TextView? = null
    private var modelValue: TextView? = null
    private var englishModelValue: TextView? = null
    private var shizukuValue: TextView? = null
    private var modelDiagnostics: TextView? = null
    private var proStatus: TextView? = null
    private var activeSwitch: Switch? = null
    private var wifiOnlySwitch: Switch? = null
    private var floatingSwitch: Switch? = null
    private var modelButton: Button? = null
    private var englishModelButton: Button? = null
    private var serviceButton: Button? = null

    private val statusTicker = object : Runnable {
        override fun run() {
            updateStatus()
            if (::engine.isInitialized && !isFinishing && !isDestroyed) {
                statusHandler.postDelayed(this, 1500L)
            }
        }
    }

    private fun startStatusPolling() {
        statusHandler.removeCallbacks(statusTicker)
        statusHandler.post(statusTicker)
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        engine = TranslationEngine.get(this)
        render()
    }

    override fun onResume() {
        super.onResume()
        startStatusPolling()
    }

    override fun onPause() {
        statusHandler.removeCallbacks(statusTicker)
        super.onPause()
    }

    override fun onDestroy() {
        statusHandler.removeCallbacks(statusTicker)
        super.onDestroy()
    }

    private fun render() {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(BG)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18f), dp(24f), dp(18f), dp(32f))
        }
        scroll.addView(root)
        if (Build.VERSION.SDK_INT >= 35) {
            scroll.setOnApplyWindowInsetsListener { _, insets ->
                val safe = insets.getInsets(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
                )
                root.setPadding(
                    dp(18f), dp(24f) + safe.top,
                    dp(18f), dp(32f) + safe.bottom
                )
                insets
            }
        }
        setContentView(scroll)

        val hero = panel(root)
        val heroBg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(35, 26, 37), Color.rgb(19, 22, 29))
        ).apply {
            cornerRadius = dp(22f).toFloat()
            setStroke(dp(1f), Color.rgb(63, 44, 57))
        }
        hero.background = heroBg
        hero.addView(text("PDD  /  ПЕРЕВОД ЭКРАНА", 11, ACCENT, Typeface.BOLD))
        marginTop(hero, text("Русский поверх оригинала", 24, TEXT, Typeface.BOLD), 9)
        marginTop(
            hero,
            text(
                "Китайский и английский · перевод на устройстве · без изменения Pinduoduo",
                13, MUTED, Typeface.NORMAL
            ),
            9
        )
        spacer(root, 16)

        // Free translation remains independent from the optional PRO entitlement.
        val licenseCard = panel(root)
        licenseCard.addView(text("ЛИЦЕНЗИЯ", 11, ACCENT, Typeface.BOLD))
        proStatus = text("Free · основной перевод работает", 15, TEXT, Typeface.BOLD)
        marginTop(licenseCard, requireNotNull(proStatus), 8)
        marginTop(
            licenseCard,
            actionButton("PRO и управление ключом  →", false).apply {
                setOnClickListener { startActivity(Intent(this@MainActivity, LicenseActivity::class.java)) }
            },
            12
        )
        spacer(root, 14)

        val status = panel(root)
        status.addView(text("СОСТОЯНИЕ ПЕРЕВОДА", 11, ACCENT, Typeface.BOLD))
        serviceValue = text("Доступность: проверка…", 15, TEXT, Typeface.BOLD)
        modelValue = text("Китайская модель: проверка…", 13, MUTED, Typeface.NORMAL)
        englishModelValue = text("Английская модель: проверка…", 13, MUTED, Typeface.NORMAL)
        shizukuValue = text("Shizuku: проверка…", 13, MUTED, Typeface.NORMAL)
        marginTop(status, requireNotNull(serviceValue), 13)
        marginTop(status, requireNotNull(modelValue), 8)
        marginTop(status, requireNotNull(englishModelValue), 7)
        spacer(root, 14)

        root.addView(actionButton("Открыть Pinduoduo  →", true).apply {
            setOnClickListener {
                Log.i(TAG, "action open official Pinduoduo")
                val launch = packageManager.getLaunchIntentForPackage(PDD_PACKAGE)
                if (launch == null) {
                    toast("Официальное приложение Pinduoduo не найдено. Установи оригинал.")
                } else {
                    toast("Открываю официальный Pinduoduo")
                    try {
                        startActivity(launch)
                    } catch (_: ActivityNotFoundException) {
                        toast("Android не разрешил открыть Pinduoduo")
                    } catch (_: SecurityException) {
                        toast("Android не разрешил открыть Pinduoduo")
                    }
                }
            }
        })
        spacer(root, 16)

        val controls = panel(root)
        controls.addView(text("УПРАВЛЕНИЕ", 11, ACCENT, Typeface.BOLD))
        activeSwitch = Switch(this).apply {
            text = "Включить русский перевод"
            setTextColor(TEXT)
            textSize = 15f
            isChecked = engine.enabled()
            tintSwitch(this)
            setOnCheckedChangeListener { _, checked ->
                engine.prefs().edit().putBoolean(TranslationEngine.PREF_ENABLED, checked).apply()
                PddAccessibilityService.refreshRunningService()
                updateStatus()
            }
        }
        marginTop(controls, requireNotNull(activeSwitch), 12)
        controls.addView(
            text("Оригинальное приложение и его функции не изменяются.", 12, MUTED, Typeface.NORMAL)
        )

        floatingSwitch = Switch(this).apply {
            text = "Плавающая кнопка «РУ»"
            setTextColor(TEXT)
            textSize = 15f
            isChecked = engine.prefs().getBoolean(TranslationEngine.PREF_FLOAT_BUTTON, true)
            tintSwitch(this)
            setOnCheckedChangeListener { _, checked ->
                engine.prefs().edit().putBoolean(TranslationEngine.PREF_FLOAT_BUTTON, checked).apply()
                PddAccessibilityService.refreshRunningService()
            }
        }
        marginTop(controls, requireNotNull(floatingSwitch), 14)
        controls.addView(
            text("Нажми для включения перевода; перетащи к краю экрана.",
                12, MUTED, Typeface.NORMAL)
        )
        spacer(root, 14)

        serviceButton = actionButton("Настроить службу Accessibility", false).apply {
            setOnClickListener {
                Log.i(TAG, "action accessibility settings")
                toast("Открываю специальные возможности. Найди «PDD · Перевод экрана».")
                try {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (_: ActivityNotFoundException) {
                    toast("Настройки специальных возможностей не открылись. Открой их вручную.")
                } catch (_: SecurityException) {
                    toast("Настройки специальных возможностей не открылись. Открой их вручную.")
                }
            }
        }
        root.addView(requireNotNull(serviceButton))
        marginTop(
            root,
            actionButton("Открыть сведения о переводчике", false).apply {
                setOnClickListener {
                    Log.i(TAG, "action app info")
                    toast("Открываю информацию о приложении")
                    try {
                        startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", packageName, null)
                            )
                        )
                    } catch (_: ActivityNotFoundException) {
                        toast("Не удалось открыть сведения об этом приложении")
                    } catch (_: SecurityException) {
                        toast("Не удалось открыть сведения об этом приложении")
                    }
                }
            },
            9
        )
        spacer(root, 16)

        val network = panel(root)
        network.addView(text("ЯЗЫКОВЫЕ МОДЕЛИ", 11, ACCENT, Typeface.BOLD))
        wifiOnlySwitch = Switch(this).apply {
            text = "Скачивать только по Wi-Fi"
            setTextColor(TEXT)
            textSize = 15f
            isChecked = engine.wifiOnly()
            tintSwitch(this)
            setOnCheckedChangeListener { _, checked ->
                engine.prefs().edit().putBoolean(TranslationEngine.PREF_WIFI_ONLY, checked).apply()
                updateStatus()
            }
        }
        marginTop(network, requireNotNull(wifiOnlySwitch), 12)
        network.addView(
            text(
                "Мобильная сеть разрешена, если переключатель выключен. Первое скачивание расходует трафик.",
                12, MUTED, Typeface.NORMAL
            )
        )
        spacer(root, 14)

        modelButton = actionButton("2. Скачать модель китайский → русский", false).apply {
            setOnClickListener {
                Log.i(
                    TAG,
                    "action model download: ready=" + engine.ready() +
                        ", pending=" + engine.preparing() + ", overdue=" + engine.overdue()
                )
                when {
                    engine.ready() -> toast("Языковая модель уже загружена")
                    engine.preparing() -> {
                        toast(
                            if (engine.overdue()) {
                                "ML Kit всё ещё выполняет запрос. Проверю локальные модели, не начиная вторую загрузку."
                            } else {
                                "ML Kit уже загружает модель. Повторный запрос не создаётся."
                            }
                        )
                        if (engine.overdue()) engine.inspectModels { }
                    }
                    else -> {
                        toast("Проверяю локальные модели и при необходимости запускаю загрузку.")
                        engine.prefs().edit()
                            .putBoolean(TranslationEngine.PREF_MODEL_ALLOWED, true).apply()
                        engine.prepare(Runnable { updateStatus() })
                    }
                }
                updateStatus()
                startStatusPolling()
            }
        }
        root.addView(requireNotNull(modelButton))
        modelDiagnostics = text(engine.inspection(), 12, MUTED, Typeface.NORMAL)
        marginTop(
            root,
            actionButton("Проверить загруженные модели ML Kit", false).apply {
                setOnClickListener {
                    Log.i(TAG, "action inspect downloaded models")
                    modelDiagnostics?.text = "Проверка моделей ML Kit…"
                    // ML Kit Task can outlive Activity; never strongly retain the screen.
                    val owner = WeakReference(this@MainActivity)
                    engine.inspectModels { result ->
                        val activity = owner.get()
                        if (activity != null && !activity.isDestroyed) {
                            activity.runOnUiThread {
                                activity.modelDiagnostics?.text = result
                                activity.updateStatus()
                            }
                        }
                    }
                    statusHandler.postDelayed({
                        if (modelDiagnostics?.text?.toString() == "Проверка моделей ML Kit…") {
                            modelDiagnostics?.text =
                                "Проверка моделей не ответила за 20 секунд. Нужны системные журналы ML Kit."
                        }
                    }, 20_000L)
                }
            },
            9
        )
        marginTop(root, requireNotNull(modelDiagnostics), 6)

        englishModelButton = actionButton("Скачать модель английский → русский", false).apply {
            setOnClickListener {
                Log.i(TAG, "action English model download; ready=" + engine.englishReady())
                when {
                    engine.englishReady() -> toast("Модель английского перевода уже готова")
                    engine.englishPreparing() -> {
                        toast(
                            if (engine.englishOverdue()) "ML Kit не ответил. Проверь VPN и связь."
                            else "Английская модель уже загружается, дополнительный запрос не создан"
                        )
                        if (engine.englishOverdue()) engine.inspectModels { }
                    }
                    else -> {
                        engine.prefs().edit()
                            .putBoolean(TranslationEngine.PREF_EN_MODEL_ALLOWED, true).apply()
                        toast("Проверяю модели. Если требуется, загружаю английскую через интернет.")
                        engine.prepareEnglish(Runnable { updateStatus() })
                    }
                }
                updateStatus()
            }
        }
        marginTop(root, requireNotNull(englishModelButton), 9)
        marginTop(
            root,
            text(
                "Если скачивание зависает — отключи VPN Material Xray на время загрузки. После этого перевод работает офлайн.",
                12, MUTED, Typeface.NORMAL
            ),
            7
        )
        spacer(root, 10)

        val more = panel(root)
        val moreTitle = text(
            "ДИАГНОСТИКА И КОНФИДЕНЦИАЛЬНОСТЬ   +",
            12, ACCENT, Typeface.BOLD
        ).apply {
            setPadding(0, dp(5f), 0, dp(5f))
            contentDescription = "Показать или скрыть диагностику и конфиденциальность"
            isClickable = true
            isFocusable = true
        }
        more.addView(moreTitle)
        val moreBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        marginTop(more, moreBody, 10)
        moreTitle.setOnClickListener {
            val expand = moreBody.visibility != View.VISIBLE
            moreBody.visibility = if (expand) View.VISIBLE else View.GONE
            moreTitle.text = if (expand) "ДИАГНОСТИКА И КОНФИДЕНЦИАЛЬНОСТЬ   −"
                             else "ДИАГНОСТИКА И КОНФИДЕНЦИАЛЬНОСТЬ   +"
        }
        shizukuValue = text("Shizuku: проверка…", 12, MUTED, Typeface.NORMAL)
        marginTop(moreBody, requireNotNull(shizukuValue), 9)
        marginTop(
            moreBody,
            text(
                "Служба видит события Android, но обрабатывает только окно Pinduoduo. " +
                    "Поля ввода, пароли и коды исключаются. Экранный текст не передаётся на сервер. " +
                    "При использовании PRO отправляются только ключ и случайный идентификатор установки. " +
                    "OCR текста на картинках пока нет.",
                12, MUTED, Typeface.NORMAL
            ),
            10
        )
        marginTop(
            moreBody,
            text(
                "Если Android запрещает Accessibility после установки APK: сведения о приложении → ⋮ → «Разрешить ограниченные настройки».",
                12, MUTED, Typeface.NORMAL
            ),
            10
        )
        spacer(root, 12)
        updateStatus()
    }

    private fun updateStatus() {
        val service = serviceValue ?: return
        val allowed = hasService()
        val connected = PddAccessibilityService.isConnected()
        val pro = LicenseManager.get(this).state()
        proStatus?.apply {
            text = if (pro.pro) "PRO активен · разрешение подтверждено"
                   else "Free · китайский и английский перевод доступны"
            setTextColor(if (pro.pro) ACCENT else TEXT)
        }
        service.text = when {
            connected -> "Доступность: подключена (проверка перевода — в Pinduoduo)"
            allowed -> "Доступность: разрешена в Android (служба не подключена)"
            else -> "Доступность: не разрешена"
        }
        serviceButton?.text = if (allowed) "Служба разрешена · открыть настройки"
                              else "Разрешить службу перевода"

        val modelState = when {
            engine.ready() -> "Модель китайский → русский: доступна офлайн"
            engine.preparing() && engine.overdue() ->
                "ML Kit не ответил за 60 секунд. Предыдущий запрос не отменён. Нажми «Проверить модели», вторая загрузка не начнётся."
            engine.inspecting() -> "Модель: проверяю уже скачанные языковые данные…"
            engine.preparing() -> if (engine.preparingWifiOnly()) {
                "Модель: ожидаю ответ ML Kit (только Wi-Fi)…"
            } else {
                "Модель: ожидаю ответ ML Kit (Wi-Fi или мобильная сеть)…"
            }
            !engine.prefs().getBoolean(TranslationEngine.PREF_MODEL_ALLOWED, false) ->
                "Модель: нужно разрешить загрузку"
            engine.error().isNotEmpty() -> "Модель: ошибка — " + engine.error()
            else -> "Модель: ожидание"
        }

        modelValue?.apply {
            text = modelState
            if (!engine.ready() && engine.vpnActive()) {
                append("\nVPN активен: он может блокировать загрузку ML Kit")
            }
            if (engine.translationFailures() > 0) {
                append(
                    "\nНеудачных переводов фраз: " + engine.translationFailures() +
                        " (повтор ограничен 30 сек)"
                )
            }
        }

        val englishState = when {
            engine.englishReady() -> "Модель английский → русский: доступна офлайн"
            engine.englishPreparing() && engine.englishOverdue() ->
                "Английский: ожидание ML Kit более 60 секунд. Проверь VPN."
            engine.englishPreparing() -> "Английский: загрузка модели выполняется…"
            engine.englishError().isNotEmpty() -> "Английский: " + engine.englishError()
            else -> "Английский: модель пока не загружена"
        }
        englishModelValue?.text = englishState
        englishModelButton?.text = when {
            engine.englishReady() -> "Английская модель загружена"
            engine.englishPreparing() -> "Английская модель: ожидаю ML Kit"
            else -> "Скачать модель английский → русский"
        }
        modelDiagnostics?.text = if (engine.inspecting()) "Проверяю локальные модели ML Kit…"
                                 else engine.inspection()
        modelButton?.text = when {
            engine.ready() -> "Модель загружена — перевод доступен"
            engine.preparing() && engine.overdue() -> "2. Проверить загрузку (без дублирования)"
            engine.preparing() -> "2. Загрузка выполняется · нажми для проверки"
            else -> "2. Скачать модель китайский → русский"
        }
        shizukuValue?.text = if (isInstalled("moe.shizuku.privileged.api")) {
            "Shizuku: установлен (статус сервиса не проверялся)"
        } else {
            "Shizuku: приложение не обнаружено"
        }
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
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun panel(root: LinearLayout): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17f), dp(18f), dp(17f), dp(18f))
            background = GradientDrawable().apply {
                setColor(SURFACE)
                cornerRadius = dp(19f).toFloat()
                setStroke(dp(1f), Color.rgb(45, 50, 59))
            }
        }
        root.addView(panel, LinearLayout.LayoutParams(-1, -2))
        return panel
    }

    private fun text(content: String, sp: Int, color: Int, style: Int): TextView =
        TextView(this).apply {
            text = content
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp.toFloat())
            typeface = Typeface.create("sans-serif", style)
            setLineSpacing(dp(2f).toFloat(), 1.05f)
        }

    private fun actionButton(title: String, primary: Boolean): Button =
        Button(this).apply {
            isAllCaps = false
            text = title
            setTextColor(TEXT)
            textSize = 14f
            gravity = Gravity.CENTER
            minHeight = dp(if (primary) 58f else 50f)
            background = GradientDrawable().apply {
                setColor(if (primary) RED else SURFACE)
                cornerRadius = dp(15f).toFloat()
                if (!primary) setStroke(dp(1f), Color.rgb(58, 61, 69))
            }
            setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
        }

    private fun tintSwitch(control: Switch) {
        control.thumbTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(ACCENT, Color.rgb(112, 119, 129))
        )
        control.trackTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Color.rgb(32, 100, 91), Color.rgb(53, 58, 66))
        )
    }

    private fun marginTop(parent: LinearLayout, child: View, top: Int) {
        val params = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(top.toFloat())
        }
        parent.addView(child, params)
    }

    private fun spacer(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height.toFloat())))
    }

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density).roundToInt()
}
