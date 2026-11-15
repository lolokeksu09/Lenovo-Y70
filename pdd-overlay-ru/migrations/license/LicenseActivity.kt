package app.pinduoduo.overlayru

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.lang.ref.WeakReference
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Self-contained PRO license screen.
 * Translation/Accessibility data never goes to the license server.
 * Existing Free functionality is deliberately independent from license status.
 */
class LicenseActivity : Activity() {
    private companion object {
        val BG = Color.rgb(7, 9, 12)
        val SURFACE = Color.rgb(19, 23, 30)
        val WHITE = Color.rgb(244, 246, 250)
        val MUTED = Color.rgb(162, 171, 184)
        val ACCENT = Color.rgb(72, 224, 190)
        val RED = Color.rgb(241, 61, 81)
    }

    private lateinit var manager: LicenseManager
    private lateinit var status: TextView
    private lateinit var detail: TextView
    private lateinit var message: TextView
    private lateinit var activate: Button
    private lateinit var refresh: Button
    private lateinit var keyField: EditText

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        manager = LicenseManager.get(this)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        render()
    }

    override fun onResume() {
        super.onResume()
        manager.renewIfNeeded()
        update()
    }

    private fun render() {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(BG)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(22f), dp(20f), dp(28f))
        }
        scroll.addView(root)
        if (Build.VERSION.SDK_INT >= 35) {
            scroll.setOnApplyWindowInsetsListener { _, insets ->
                val safe = insets.getInsets(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
                )
                root.setPadding(
                    dp(20f), dp(22f) + safe.top,
                    dp(20f), dp(28f) + safe.bottom
                )
                insets
            }
        }
        setContentView(scroll)

        val back = label("‹  Назад", 16, MUTED, Typeface.BOLD).apply {
            setPadding(0, dp(7f), 0, dp(12f))
            setOnClickListener { finish() }
        }
        root.addView(back)
        margin(root, label("ПОДПИСКА И ЛИЦЕНЗИЯ", 11, ACCENT, Typeface.BOLD), 7)
        margin(root, label("PRO", 35, WHITE, Typeface.BOLD), 8)
        margin(
            root,
            label(
                "Управление ключом и доступом без постоянного подключения к сети",
                14, MUTED, Typeface.NORMAL
            ),
            5
        )

        val stateCard = panel(root)
        margin(stateCard, label("ТЕКУЩИЙ ДОСТУП", 11, ACCENT, Typeface.BOLD), 0)
        status = label("Проверяю…", 23, WHITE, Typeface.BOLD)
        margin(stateCard, status, 12)
        detail = label("", 13, MUTED, Typeface.NORMAL)
        margin(stateCard, detail, 9)

        val keyCard = panel(root)
        margin(keyCard, label("АКТИВАЦИЯ", 11, ACCENT, Typeface.BOLD), 0)
        margin(
            keyCard,
            label(
                "Введите ключ, полученный после покупки. Код активации не сохраняется в приложении.",
                13, MUTED, Typeface.NORMAL
            ),
            9
        )
        keyField = EditText(this).apply {
            setSingleLine(true)
            setTextColor(WHITE)
            setHintTextColor(MUTED)
            textSize = 15f
            hint = "OVR-XXXX-XXXX-XXXX-…"
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            // Avoid copying a purchase/activation secret into Android autofill datasets.
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            background = rounded(Color.rgb(32, 38, 48), dp(13f)).apply {
                setStroke(dp(1f), Color.rgb(60, 67, 79))
            }
            setPadding(dp(12f), dp(12f), dp(12f), dp(12f))
        }
        margin(keyCard, keyField, 13)

        activate = button("Активировать ключ", true).apply {
            setOnClickListener {
                if (manager.busy()) return@setOnClickListener
                message.text = "Проверка ключа на сервере…"
                isEnabled = false
                manager.activate(keyField.text.toString(), observeResult())
            }
        }
        margin(keyCard, activate, 12)

        refresh = button("Обновить срок доступа", false).apply {
            setOnClickListener {
                if (manager.busy()) return@setOnClickListener
                message.text = "Связываюсь с сервером…"
                isEnabled = false
                manager.renew(observeResult())
            }
        }
        margin(keyCard, refresh, 9)

        message = label("", 13, MUTED, Typeface.NORMAL)
        margin(keyCard, message, 10)

        val info = panel(root)
        margin(info, label("КАК ЭТО РАБОТАЕТ", 11, ACCENT, Typeface.BOLD), 0)
        margin(
            info,
            label(
                "• PRO подтверждается цифровой подписью сервера.\n" +
                    "• После активации доступ возможен офлайн до окончания выданного разрешения — максимум 7 дней.\n" +
                    "• Срок продлевается при подключении к серверу, пока действительна подписка.\n" +
                    "• Бесплатный перевод и плавающая кнопка продолжают работать без PRO.\n" +
                    "• Экран Pinduoduo и тексты переводов на сервер не отправляются.",
                13, MUTED, Typeface.NORMAL
            ),
            12
        )
        margin(
            info,
            label(
                "Код установки для обращения в поддержку: " + manager.supportId(),
                12, MUTED, Typeface.NORMAL
            ),
            12
        )
        update()
    }

    private fun observeResult(): LicenseManager.Callback {
        val weakOwner = WeakReference(this)
        return LicenseManager.Callback { ok, code ->
            val activity = weakOwner.get()
            if (activity != null && !activity.isDestroyed && !activity.isFinishing) {
                activity.message.text = activity.translateCode(code)
                if (ok) activity.keyField.setText("")
                activity.update()
            }
        }
    }

    private fun translateCode(code: String): String = when (code) {
        "activated" -> "Ключ принят. Доступ PRO активирован."
        "renewed" -> "Разрешение успешно обновлено."
        "invalid_license" -> "Ключ неверный или не найден. Проверь символы."
        "activation_limit_reached" ->
            "Лимит устройств достигнут. Обратись в поддержку для переноса."
        "license_expired" -> "Срок лицензии закончился."
        "license_revoked" -> "Лицензия отозвана. Обратись в поддержку."
        "activation_not_found" ->
            "Установка не привязана к лицензии. Обратись в поддержку."
        "expired_token" -> "Для восстановления доступа обратись в поддержку."
        "no_license" -> "Сначала активируй ключ."
        "request_in_progress" -> "Дождись завершения предыдущего запроса."
        "not_configured" -> "Лицензионный сервер ещё не настроен в этой сборке."
        "service_unavailable" -> "Сервер недоступен. Попробуй позже."
        "connection_error" ->
            "Не удалось подключиться. Проверь интернет или VPN. Офлайн-доступ сохранён до срока разрешения."
        else ->
            "Не удалось подтвердить доступ. Проверь соединение или обратись в поддержку."
    }

    private fun update() {
        if (!::status.isInitialized || !::detail.isInitialized) return
        val current = manager.state()
        if (current.pro) {
            status.text = "PRO активен"
            status.setTextColor(ACCENT)
            val format: DateFormat =
                SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale("ru", "RU"))
            val planLabel = when (current.plan) {
                "pro_lifetime" -> "Бессрочная лицензия"
                "pro_365d" -> "Годовая лицензия"
                else -> "Лицензия на 30 дней"
            }
            detail.text = planLabel +
                "\nОфлайн-разрешение действует до " +
                format.format(Date(current.leaseExpiresAt * 1000L)) +
                "\nПри наличии интернета его можно обновлять."
        } else {
            status.text = "Free"
            status.setTextColor(WHITE)
            detail.text = when (current.code) {
                "not_configured" ->
                    "Сервер лицензий ещё не подключён к этой сборке. Бесплатный перевод доступен."
                "clock_changed" ->
                    "Обнаружено изменение времени телефона. Проверь дату и повтори обновление лицензии."
                "expired_or_invalid" ->
                    "Офлайн-разрешение истекло или недействительно. Обнови лицензию через интернет."
                else ->
                    "Ключ пока не активирован. Перевод на китайском и английском остаётся доступен."
            }
        }
        val enabled = manager.configured() && !manager.busy()
        if (::activate.isInitialized) activate.isEnabled = enabled
        if (::refresh.isInitialized) refresh.isEnabled = enabled
        if (::keyField.isInitialized) keyField.isEnabled = enabled
    }

    private fun panel(parent: LinearLayout): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17f), dp(17f), dp(17f), dp(18f))
            background = rounded(SURFACE, dp(20f)).apply {
                setStroke(dp(1f), Color.rgb(48, 55, 65))
            }
        }
        margin(parent, card, 17)
        return card
    }

    private fun label(
        content: String, sp: Int, color: Int, style: Int
    ): TextView = TextView(this).apply {
        text = content
        setTextColor(color)
        textSize = sp.toFloat()
        typeface = Typeface.create("sans-serif", style)
        setLineSpacing(dp(2f).toFloat(), 1.02f)
    }

    private fun button(name: String, primary: Boolean): Button =
        Button(this).apply {
            text = name
            isAllCaps = false
            setTextColor(WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            minHeight = dp(52f)
            background = rounded(
                if (primary) RED else Color.rgb(35, 41, 50), dp(14f)
            ).apply {
                if (!primary) setStroke(dp(1f), Color.rgb(72, 81, 93))
            }
        }

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }

    private fun margin(parent: LinearLayout, view: View, top: Int) {
        val params = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(top.toFloat())
        }
        parent.addView(view, params)
    }

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density).roundToInt()
}
