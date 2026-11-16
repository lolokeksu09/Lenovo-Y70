package app.pinduoduo.overlayru

import java.util.Locale

/** Deterministic exact-match English UI labels; unknown labels fall back to ML Kit. */
object EnglishUiGlossary {
    private val UI = mapOf(
        "settings" to "Настройки",
        "linked phone" to "Привязанный телефон",
        "account & security" to "Аккаунт и безопасность",
        "account and security" to "Аккаунт и безопасность",
        "buy now, pay later" to "Купить сейчас — оплатить позже",
        "region & language" to "Регион и язык",
        "region and language" to "Регион и язык",
        "media & calls" to "Медиа и звонки",
        "media and calls" to "Медиа и звонки",
        "notifications" to "Уведомления",
        "font size" to "Размер шрифта",
        "dark mode" to "Тёмная тема",
        "feedback" to "Обратная связь",
        "partnership" to "Партнёрство",
        "teen mode" to "Подростковый режим",
        "clear cache" to "Очистить кеш",
        "data exchange" to "Обмен данными",
        "information collection" to "Сбор информации"
    )

    @JvmStatic
    fun lookup(source: String?): String? =
        source?.trim()?.lowercase(Locale.ROOT)?.let(UI::get)
}
