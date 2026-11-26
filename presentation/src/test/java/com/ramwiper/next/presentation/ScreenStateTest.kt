package com.ramwiper.next.presentation

import com.ramwiper.next.domain.*
import org.junit.Assert.*
import org.junit.Test

class ScreenStateTest {
    @Test fun operationsRequireLoadedSettingsAndNoConflictingWork() {
        val initial = ScreenState("test")
        assertFalse(initial.canStart)
        val ready = initial.copy(settingsLoaded = true, initialLoading = false)
        assertTrue(ready.canStart)
        assertFalse(ready.copy(settingsBusy = true).canStart)
        assertFalse(ready.copy(progress = Progress("working")).canStart)
        assertFalse(ready.copy(diagnosticBusy = true).canStart)
        assertFalse(ready.copy(cooldownSeconds = 1).canStart)
    }
    @Test fun explicitShizukuModeRequiresPermissionAndSelection() {
        val ready = ScreenState("test", settings = UserSettings(engine = Engine.SHIZUKU), settingsLoaded = true)
        val connected = ready.copy(access = AccessStatus(AccessState.CONNECTED))
        assertFalse(ready.copy(selected = setOf("test.app")).canStart)
        assertFalse(connected.canStart)
        assertTrue(connected.copy(selected = setOf("test.app")).canStart)
    }
}
