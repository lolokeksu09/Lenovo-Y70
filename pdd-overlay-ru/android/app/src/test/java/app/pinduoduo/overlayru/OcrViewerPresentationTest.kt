package app.pinduoduo.overlayru

import org.junit.Assert.*
import org.junit.Test

class OcrViewerPresentationTest {
    private val untranslated = listOf("防冻" to null, "玻璃水" to null)
    private val translated = listOf(
        "商品详情" to "Описание товара",
        "查看物流" to "Отследить доставку"
    )

    @Test fun detectedButNotTranslatedImageNeverClaimsSuccess() {
        assertEquals(0, OcrViewerPresentation.translatedCount(untranslated))
        assertEquals("Посмотреть распознанные области",
            OcrViewerPresentation.openViewerLabel(untranslated))
        assertTrue(OcrViewerPresentation.viewerSummary(untranslated)
            .contains("Русский перевод пока недоступен"))
    }

    @Test fun translatedImageShowsAccurateCoverageAndAction() {
        assertEquals(2, OcrViewerPresentation.translatedCount(translated))
        assertEquals("Показать перевод поверх фото",
            OcrViewerPresentation.openViewerLabel(translated))
        assertEquals("Переведено 2 из 2 строк.",
            OcrViewerPresentation.viewerSummary(translated))
    }

    @Test fun untranslatedOrUnchangedOutputIsNotCounted() {
        val input = listOf("玻璃水" to "玻璃水", "防冻" to "", "查看物流" to "Отследить доставку")
        assertEquals(1, OcrViewerPresentation.translatedCount(input))
    }

    @Test fun neverOutlinesEveryUntranslatedRegionAsIfItWereDone() {
        assertFalse(OcrViewerPresentation.showRegionOutline(false, false, true))
        assertTrue(OcrViewerPresentation.showRegionOutline(true, false, false))
        assertTrue(OcrViewerPresentation.showRegionOutline(false, true, true))
        assertFalse(OcrViewerPresentation.showRegionOutline(false, true, false))
    }
}
