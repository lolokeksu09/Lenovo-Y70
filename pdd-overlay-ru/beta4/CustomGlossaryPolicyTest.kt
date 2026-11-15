package app.pinduoduo.overlayru

import org.junit.Assert.*
import org.junit.Test

class CustomGlossaryPolicyTest {
    @Test fun allowsChineseAndEnglishVisibleLabels() {
        assertTrue(CustomGlossaryPolicy.valid("查看物流", "Проверить доставку"))
        assertTrue(CustomGlossaryPolicy.valid("Shipping options", "Варианты доставки"))
    }

    @Test fun refusesSensitiveCheckoutActionsAndAmounts() {
        assertFalse(CustomGlossaryPolicy.valid("立即支付", "Оплатить"))
        assertFalse(CustomGlossaryPolicy.valid("提交订单", "Заказать"))
        assertFalse(CustomGlossaryPolicy.valid("￥39.99", "0 рублей"))
        assertFalse(CustomGlossaryPolicy.valid("123456", "Пароль"))
    }

    @Test fun refusesInvalidOrOversizedCorrections() {
        assertFalse(CustomGlossaryPolicy.valid("", "Перевод"))
        assertFalse(CustomGlossaryPolicy.valid("查看物流", "  "))
        assertFalse(CustomGlossaryPolicy.valid("查看物流", "12345"))
        assertFalse(CustomGlossaryPolicy.valid("查看物流", "а".repeat(201)))
        assertFalse(CustomGlossaryPolicy.valid("中".repeat(181), "Значение"))
    }

    @Test fun normalizesWhitespaceWithoutChangingChineseCharacters() {
        assertEquals("查看 物流", CustomGlossaryPolicy.normalize("  查看   物流  "))
    }
}
