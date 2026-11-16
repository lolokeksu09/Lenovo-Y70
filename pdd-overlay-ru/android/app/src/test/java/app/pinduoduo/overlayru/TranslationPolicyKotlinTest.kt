package app.pinduoduo.overlayru

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationPolicyKotlinTest {
    @Test fun routesChineseAndEnglishWithoutChangingRussianOrBrands() {
        assertEquals(TranslationPolicy.CHINESE, TranslationPolicy.sourceLanguage("去购物"))
        assertEquals(TranslationPolicy.ENGLISH, TranslationPolicy.sourceLanguage("Sign in"))
        assertEquals(TranslationPolicy.ENGLISH, TranslationPolicy.sourceLanguage("Search"))
        assertEquals(TranslationPolicy.NONE, TranslationPolicy.sourceLanguage("Подписка"))
        assertEquals(TranslationPolicy.NONE, TranslationPolicy.sourceLanguage("Pinduoduo"))
        assertEquals(TranslationPolicy.NONE, TranslationPolicy.sourceLanguage(null))
        assertTrue(TranslationPolicy.containsChinese("包邮"))
        assertFalse(TranslationPolicy.containsChinese("Подписка"))
    }

    @Test fun preservesExistingShoppingAndPrivacyGuards() {
        assertTrue(TranslationPolicy.shouldTranslate("查看物流", false, false))
        assertTrue(TranslationPolicy.shouldTranslate("  待付款  ", false, false))
        assertTrue(TranslationPolicy.shouldTranslate("English text", false, false))
        assertTrue(TranslationPolicy.shouldTranslate("Search", false, false))
        assertFalse(TranslationPolicy.shouldTranslate("Pinduoduo", false, false))
        assertFalse(TranslationPolicy.shouldTranslate("Order total 4356", false, false))
        assertFalse(TranslationPolicy.shouldTranslate("123456", false, false))
        assertFalse(TranslationPolicy.shouldTranslate("¥99 包邮", false, false))
        assertFalse(TranslationPolicy.shouldTranslate("￥19 包邮", false, false))
        assertFalse(TranslationPolicy.shouldTranslate("付款密码", false, true))
        assertFalse(TranslationPolicy.shouldTranslate("验证码", true, false))
        assertFalse(TranslationPolicy.shouldTranslate("验证码 453278", false, false))
        assertFalse(TranslationPolicy.shouldTranslate("订单 https://example.com", false, false))
        assertFalse(TranslationPolicy.shouldTranslate("a".repeat(200) + "你好", false, false))
        assertFalse(TranslationPolicy.shouldTranslate(null, false, false))
    }

    @Test fun acceptsNaturalEnglishPunctuationButRejectsOtherLanguages() {
        assertEquals(TranslationPolicy.ENGLISH,
            TranslationPolicy.sourceLanguage("The padding—none of them have bruises."))
        assertEquals(TranslationPolicy.ENGLISH,
            TranslationPolicy.sourceLanguage("They’re fresh and look “excellent”."))
        assertEquals(TranslationPolicy.NONE,
            TranslationPolicy.sourceLanguage("Хорошие киви—fresh"))
        assertTrue(TranslationPolicy.shouldTranslate("Looks fresh—even after delivery.", false, false))
    }

    @Test fun rejectsLatinContactAndPriceWithoutObscuringMoney() {
        assertFalse(TranslationPolicy.shouldTranslate("Email admin@example.com", false, false))
        assertFalse(TranslationPolicy.shouldTranslate("订单 19 元", false, false))
        assertFalse(TranslationPolicy.shouldTranslate("Цена 100 ₽", false, false))
        assertFalse(TranslationPolicy.shouldTranslate("Pay 100 $", false, false))
    }
}
