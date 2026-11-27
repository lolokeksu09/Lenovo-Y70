package com.ramwiper.next.domain

import org.junit.Assert.*
import org.junit.Test

class AutomationPolicyTest {
    private val sample = Snapshot(8L * 1024 * MIB, 1536 * MIB, 128 * MIB, false)
    private val settings = AutomationSettings(true, 20, 5)
    @Test fun requiresOptInAndValidPressureBudget() {
        assertTrue(AutomationPolicy.mayAllocate(sample, settings))
        assertFalse(AutomationPolicy.mayAllocate(sample, settings.copy(foregroundEnabled = false)))
        assertFalse(AutomationPolicy.mayAllocate(sample.copy(low = true), settings))
        assertFalse(AutomationPolicy.mayAllocate(sample.copy(thermal = 3), settings))
        assertFalse(AutomationPolicy.mayAllocate(sample.copy(available = 4L * 1024 * MIB), settings))
    }
    @Test fun twoSamplesAndIntervalWithReset() {
        val gate = AutomationGate()
        assertFalse(gate.observe(sample, settings, 0, true))
        assertFalse(gate.observe(sample, settings, 1000, false))
        assertFalse(gate.observe(sample, settings, 2000, true))
        assertTrue(gate.observe(sample, settings, 4000, true))
        gate.recordAttempt(4000)
        assertFalse(gate.observe(sample, settings, 6000, true))
        assertFalse(gate.observe(sample, settings, 303999, true))
        assertTrue(gate.observe(sample, settings, 304000, true))
        gate.resetSamples()
        assertFalse(gate.observe(sample, settings, 306000, true))
    }
    @Test fun thresholdDoesNotRoundDown() {
        assertFalse(AutomationPolicy.belowThreshold(Snapshot(10000, 2099, 0, false), settings))
        assertTrue(AutomationPolicy.belowThreshold(Snapshot(10000, 2000, 0, false), settings))
    }
    @Test fun corruptedValuesUseDefaults() {
        val normalized = AutomationSettings(true, 99, -1).normalized()
        assertEquals(20, normalized.thresholdPercent)
        assertEquals(5, normalized.intervalMinutes)
    }
}
