package app.pinduoduo.overlayru

import java.util.Locale
import java.util.regex.Pattern

/** Local UI-only translation guard; never forwards user content to a server. */
object TranslationPolicy {
    const val NONE = 0
    const val CHINESE = 1
    const val ENGLISH = 2

    private val DIGITS = Pattern.compile("""[+\d\s()\-]{7,}""")
    private val TOKEN = Pattern.compile("""(?i).*\b(?:https?://|www\.|@|\w+@\w+\.)\S*.*""")
    private val LATIN_WORD = Pattern.compile("[A-Za-z]{2,}")
    private val AMOUNT = Pattern.compile(""".*\d+\s*元.*""")
    private val OTP = Pattern.compile(".*[0-9]{4,8}.*")
    private val SINGLE_WORD_UI = setOf(
        "search", "home", "cart", "orders", "order", "profile", "login",
        "logout", "pay", "settings", "coupons", "discount", "delivery",
        "shipping", "confirm", "cancel", "save", "continue", "back",
        "next", "address", "support", "help", "checkout", "free", "sale",
        "buy", "view", "more", "details", "filter", "sort", "close",
        "open", "track", "tracking", "pending", "received", "total",
        "notifications", "feedback", "partnership"
    )

    @JvmStatic
    fun containsChinese(value: String?): Boolean {
        if (value == null) return false
        return value.any { it in '\u3400'..'\u9FFF' || it in '\uF900'..'\uFAFF' }
    }

    /** Conservative UI label routing, not generic language detection. */
    @JvmStatic
    fun sourceLanguage(value: String?): Int {
        if (value == null) return NONE
        if (containsChinese(value)) return CHINESE
        // Reviews may contain Latin accents and emoji/flags. A supplementary
        // Unicode code point must not be rejected as two invalid surrogate chars.
        // Never route mixed Cyrillic/Arabic words or currency to an English model.
        var cursor = 0
        while (cursor < value.length) {
            val cp = Character.codePointAt(value, cursor)
            cursor += Character.charCount(cp)
            if (cp <= 127) continue
            val kind = Character.getType(cp)
            val allowed = when {
                Character.isLetter(cp) ->
                    Character.UnicodeScript.of(cp) == Character.UnicodeScript.LATIN
                Character.isDigit(cp) || kind == Character.CURRENCY_SYMBOL -> false
                cp == 0x200D || cp == 0xFE0F || cp == 0xFE0E -> true
                cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF -> true
                kind == Character.NON_SPACING_MARK ||
                    kind == Character.COMBINING_SPACING_MARK -> true
                kind == Character.DASH_PUNCTUATION ||
                    kind == Character.START_PUNCTUATION ||
                    kind == Character.END_PUNCTUATION ||
                    kind == Character.INITIAL_QUOTE_PUNCTUATION ||
                    kind == Character.FINAL_QUOTE_PUNCTUATION ||
                    kind == Character.OTHER_PUNCTUATION ||
                    kind == Character.SPACE_SEPARATOR -> true
                else -> false
            }
            if (!allowed) return NONE
        }

        val matcher = LATIN_WORD.matcher(value)
        var count = 0
        var only: String? = null
        while (matcher.find()) {
            count++
            only = matcher.group().lowercase(Locale.ROOT)
        }
        return if (count >= 2 || (count == 1 && only in SINGLE_WORD_UI)) ENGLISH else NONE
    }

    @JvmStatic
    fun shouldTranslate(text: String?, editable: Boolean, password: Boolean): Boolean {
        if (editable || password || text == null) return false
        val s = text.trim()
        if (s.isEmpty() || s.length > 180 || sourceLanguage(s) == NONE) return false
        // Do not obscure prices, order totals, URLs, contacts or one-time codes.
        if (s.codePoints().anyMatch {
                Character.getType(it) == Character.CURRENCY_SYMBOL
            } || AMOUNT.matcher(s).matches()) return false
        if (DIGITS.matcher(s).matches() || TOKEN.matcher(s).matches()) return false
        if (OTP.matcher(s).matches()) return false
        return true
    }
}
