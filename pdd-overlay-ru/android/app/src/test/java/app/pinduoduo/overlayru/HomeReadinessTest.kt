package app.pinduoduo.overlayru

import app.pinduoduo.overlayru.ui.home.HomeBlocker
import app.pinduoduo.overlayru.ui.home.HomeReadiness
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeReadinessTest {
    private fun state(
        pdd: Boolean = true, allowed: Boolean = true, connected: Boolean = true,
        ready: Boolean = true, preparing: Boolean = false, overdue: Boolean = false,
    ) = HomeReadiness.resolve(pdd, allowed, connected, ready, preparing, overdue)

    @Test fun `ready only when pdd permission service and chinese model all ready`() {
        assertEquals(HomeBlocker.READY, state())
        assertEquals(HomeBlocker.PDD_MISSING, state(pdd = false))
        assertEquals(HomeBlocker.ACCESSIBILITY_DISABLED, state(allowed = false, connected = false))
        assertEquals(HomeBlocker.SERVICE_DISCONNECTED, state(connected = false))
        assertEquals(HomeBlocker.MODEL_MISSING, state(ready = false))
    }

    @Test fun `installed pdd has precedence over permission and model blockers`() {
        assertEquals(HomeBlocker.PDD_MISSING, state(pdd = false, allowed = false, connected = false, ready = false))
        assertEquals(HomeBlocker.ACCESSIBILITY_DISABLED, state(allowed = false, connected = false, ready = false))
        assertEquals(HomeBlocker.SERVICE_DISCONNECTED, state(connected = false, ready = false))
    }

    @Test fun `inflight model work never becomes a duplicate download`() {
        assertEquals(HomeBlocker.MODEL_LOADING, state(ready = false, preparing = true))
        assertEquals(HomeBlocker.MODEL_OVERDUE, state(ready = false, preparing = true, overdue = true))
    }

    @Test fun `usable cached model wins over stale inflight state`() {
        assertEquals(HomeBlocker.READY, state(ready = true, preparing = true, overdue = true))
    }

    @Test fun missingPddHasOfficialSiteAction() {
        val ui = app.pinduoduo.overlayru.ui.home.HomeUiState(blocker = HomeBlocker.PDD_MISSING)
        assertEquals("Открыть сайт Pinduoduo", ui.action)
        assertEquals(true, ui.actionable)
    }

    @Test fun modelDownloadingDisablesPrimaryAction() {
        val ui = app.pinduoduo.overlayru.ui.home.HomeUiState(blocker = HomeBlocker.MODEL_LOADING)
        assertEquals(false, ui.actionable)
        assertEquals("Загружаем китайскую модель", ui.status)
    }
}
