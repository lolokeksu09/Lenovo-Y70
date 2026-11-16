package app.pinduoduo.overlayru

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsKotlinMigrationTest {
    @Test fun accessibilityComponentMatchIsStrictAndSupportsAbbreviations() {
        val pkg = "app.pinduoduo.overlayru"
        val service = "$pkg.PddAccessibilityService"
        assertTrue(AccessibilityServiceIds.contains("$pkg/.PddAccessibilityService", pkg, service))
        assertTrue(AccessibilityServiceIds.contains("$pkg/$service", pkg, service))
        assertFalse(AccessibilityServiceIds.contains("other/$service", pkg, service))
        assertFalse(AccessibilityServiceIds.contains("$pkg/.PddAccessibilityServiceEvil", pkg, service))
        assertFalse(AccessibilityServiceIds.contains("$pkg/.OtherService", pkg, service))
        assertFalse(AccessibilityServiceIds.contains(null, pkg, service))
    }

    @Test fun repeatedRequestsAndLateCompletionsCannotResetTheGate() {
        val gate = DownloadAttemptGate()
        val first = gate.begin()
        assertTrue(first > 0)
        assertEquals(-1, gate.begin())
        assertFalse(gate.markOverdue(first + 1))
        assertTrue(gate.markOverdue(first))
        assertTrue(gate.overdue())
        assertTrue(gate.inFlight()) // UI timeout does not cancel the ML Kit Task.
        assertFalse(gate.finish(first + 1))
        assertTrue(gate.finish(first))
        assertFalse(gate.inFlight())
        assertFalse(gate.overdue())
        val second = gate.begin()
        assertEquals(first + 1, second)
        assertFalse(gate.finish(first)) // stale callback must not finish a newer request
        assertTrue(gate.inFlight())
        assertTrue(gate.finish(second))
    }

    @Test fun languagesNeedLocalSourceAndRussianModels() {
        assertTrue(ModelAvailability.usable(true, true))
        assertFalse(ModelAvailability.usable(false, true))
        assertFalse(ModelAvailability.usable(true, false))
        assertFalse(ModelAvailability.usable(false, false))
    }

    @Test fun englishExactMatchIsCaseInsensitiveOnly() {
        assertEquals("Настройки", EnglishUiGlossary.lookup(" SETTINGS "))
        assertEquals("Очистить кеш", EnglishUiGlossary.lookup("clear cache"))
        assertEquals("Аккаунт и безопасность", EnglishUiGlossary.lookup("account & security"))
        assertEquals(null, EnglishUiGlossary.lookup("new unknown menu"))
        assertEquals(null, EnglishUiGlossary.lookup(null))
    }
}
