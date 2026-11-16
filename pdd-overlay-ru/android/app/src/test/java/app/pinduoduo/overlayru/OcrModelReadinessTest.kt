package app.pinduoduo.overlayru

import org.junit.Assert.*
import org.junit.Test

class OcrModelReadinessTest {
    @Test fun englishModelAloneDoesNotMakeChineseScreenshotReady() {
        assertFalse(OcrModelReadiness.allRequiredModelsReady(
            listOf("防冻玻璃水", "查看物流"), false, true))
    }

    @Test fun chineseModelAloneIsEnoughForChineseOnlyScreenshot() {
        assertTrue(OcrModelReadiness.allRequiredModelsReady(
            listOf("防冻玻璃水", "商品详情"), true, false))
    }

    @Test fun mixedScreenshotRequiresBothModels() {
        val rows = listOf("防冻玻璃水", "Read more")
        assertFalse(OcrModelReadiness.allRequiredModelsReady(rows, true, false))
        assertFalse(OcrModelReadiness.allRequiredModelsReady(rows, false, true))
        assertTrue(OcrModelReadiness.allRequiredModelsReady(rows, true, true))
    }

    @Test fun protectedPriceOnlyIsNotReadyToTranslate() {
        assertFalse(OcrModelReadiness.allRequiredModelsReady(
            listOf("¥19.90", "¥15.5"), true, true))
    }
}
