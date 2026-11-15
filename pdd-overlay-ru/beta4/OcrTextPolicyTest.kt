package app.pinduoduo.overlayru

import org.junit.Assert.*
import org.junit.Test

class OcrTextPolicyTest {
    @Test fun deduplicatesAndStripsBlankOcrLines() {
        assertEquals(
            listOf("颜色选择", "尺码指南"),
            OcrTextPolicy.cleaned(listOf("", " 颜色选择  ", "颜色选择", "  ", "尺码指南"))
        )
    }

    @Test fun limitsLargeImagesToSixtyLines() {
        assertEquals(60, OcrTextPolicy.cleaned((0..100).map { "文字$it" }).size)
    }

    @Test fun avoidsCheckoutAndPrices() {
        assertFalse(OcrTextPolicy.canTranslate("立即付款"))
        assertFalse(OcrTextPolicy.canTranslate("¥39.90"))
        assertFalse(OcrTextPolicy.canTranslate("123456"))
    }

    @Test fun translatesOrdinaryProductLabels() {
        assertTrue(OcrTextPolicy.canTranslate("商品详情"))
        assertTrue(OcrTextPolicy.canTranslate("Shipping options"))
    }
}
