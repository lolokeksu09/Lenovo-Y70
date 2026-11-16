package app.pinduoduo.overlayru

import org.junit.Assert.*
import org.junit.Test

class OcrInlineTextPolicyTest {
    private val region = OcrRegion(.1f, .2f, .8f, .4f)
    @Test fun knownTranslatedChineseIsEligible() {
        assertTrue(OcrInlineTextPolicy.canCover(
            "商品详情", "Описание товара", region, 250f, 80f))
    }
    @Test fun neverHidesUntranslatedUnknownOrSourcePixels() {
        assertFalse(OcrInlineTextPolicy.canCover(
            "商品详情", null, region, 250f, 80f))
        assertFalse(OcrInlineTextPolicy.canCover(
            "商品详情", "商品详情", region, 250f, 80f))
        assertFalse(OcrInlineTextPolicy.canCover(
            "商品详情", "Описание", null, 250f, 80f))
    }
    @Test fun paymentPricesAndOtpRemainOriginal() {
        assertFalse(OcrInlineTextPolicy.canCover(
            "立即付款", "Оплатить", region, 250f, 80f))
        assertFalse(OcrInlineTextPolicy.canCover(
            "¥39.90", "Цена 39.90", region, 250f, 80f))
        assertFalse(OcrInlineTextPolicy.canCover(
            "验证码 453278", "Код", region, 250f, 80f))
    }
    @Test fun tinyOrNonFiniteRegionsNeverReceiveOpaquePanel() {
        assertFalse(OcrInlineTextPolicy.canCover(
            "查看物流", "Отследить доставку", region, 20f, 12f))
        assertFalse(OcrInlineTextPolicy.canCover(
            "查看物流", "Отследить доставку", region, Float.NaN, 40f))
    }
}
